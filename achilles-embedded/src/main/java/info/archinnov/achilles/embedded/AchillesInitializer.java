/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package info.archinnov.achilles.embedded;

import static info.archinnov.achilles.configuration.ConfigurationParameters.KEYSPACE_NAME;
import static info.archinnov.achilles.embedded.CassandraEmbeddedConfigParameters.*;
import static info.archinnov.achilles.embedded.StateRepository.REPOSITORY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Optional;
import info.archinnov.achilles.persistence.AsyncManager;
import info.archinnov.achilles.script.ScriptExecutor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolOptions.Compression;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.ReconnectionPolicy;
import com.datastax.driver.core.policies.RetryPolicy;
import info.archinnov.achilles.configuration.ConfigurationParameters;
import info.archinnov.achilles.internal.utils.ConfigMap;
import info.archinnov.achilles.internal.validation.Validator;
import info.archinnov.achilles.persistence.PersistenceManager;
import info.archinnov.achilles.persistence.PersistenceManagerFactory;
import info.archinnov.achilles.persistence.PersistenceManagerFactory.PersistenceManagerFactoryBuilder;
import info.archinnov.achilles.type.TypedMap;

public class AchillesInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AchillesInitializer.class);

    private static final Pattern KEYSPACE_NAME_PATTERN = Pattern.compile("[a-zA-Z][_a-zA-Z0-9]{0,31}");

    private Cluster singletonCluster;

    void initializeFromParameters(String cassandraHost, TypedMap parameters, ConfigMap achillesParameters) {

        String keyspaceName = achillesParameters.getTyped(KEYSPACE_NAME);
        synchronized (REPOSITORY) {
            if (!REPOSITORY.keyspaceAlreadyBootstrapped(keyspaceName)) {
                LOGGER.trace("Bootstrapping Achilles for keyspace {}", keyspaceName);
                initialize(cassandraHost, parameters, achillesParameters);
                REPOSITORY.markKeyspaceAsBootstrapped(keyspaceName);
            }
            LOGGER.trace("Do not bootstrap Achilles for keyspace {} because it was already created", keyspaceName);
        }
    }

    private void initialize(String cassandraHost, TypedMap parameters, ConfigMap achillesParameters) {

        Boolean keyspaceDurableWrite = parameters.getTyped(KEYSPACE_DURABLE_WRITE);
        Boolean nativeSessionOnly = parameters.getTyped(BUILD_NATIVE_SESSION_ONLY);
        Boolean nativeClusterOnly = parameters.getTyped(BUILD_NATIVE_CLUSTER_ONLY);


        String hostname;
        int cqlPort;

        if (isNotBlank(cassandraHost) && cassandraHost.contains(":")) {
            String[] split = cassandraHost.split(":");
            hostname = split[0];
            cqlPort = Integer.parseInt(split[1]);
        } else {
            hostname = DEFAULT_CASSANDRA_HOST;
            cqlPort = parameters.getTyped(CASSANDRA_CQL_PORT);
        }

        final Cluster cluster = createCluster(hostname, cqlPort, parameters);

        if (nativeClusterOnly) {
            buildClusterOnly(parameters, achillesParameters, keyspaceDurableWrite, cluster);
            //Break here, nothing more to do
            return;
        }

        String keyspaceName = extractAndValidateKeyspaceName(achillesParameters);
        createKeyspaceIfNeeded(cluster, keyspaceName, keyspaceDurableWrite);
        final Session session = cluster.connect(keyspaceName);
        executeStartupScripts(session, parameters);

        if (nativeSessionOnly) {
            REPOSITORY.addNewSessionToKeyspace(keyspaceName, session);
        } else {
            bootstrapAchilles(achillesParameters, cluster, keyspaceName, session);
        }

    }

    private void bootstrapAchilles(ConfigMap achillesParameters, Cluster cluster, String keyspaceName, Session session) {
        achillesParameters.put(ConfigurationParameters.NATIVE_SESSION, session);
        achillesParameters.put(ConfigurationParameters.KEYSPACE_NAME, keyspaceName);
        if (!achillesParameters.containsKey(ConfigurationParameters.FORCE_TABLE_CREATION)) {
            achillesParameters.put(ConfigurationParameters.FORCE_TABLE_CREATION, true);
        }

        PersistenceManagerFactory factory = PersistenceManagerFactoryBuilder.build(cluster, achillesParameters);

        ServerStarter.CASSANDRA_EMBEDDED.getShutdownHook().addManagerFactory(factory);

        PersistenceManager manager = factory.createPersistenceManager();
        AsyncManager asyncManager = factory.createAsyncManager();

        REPOSITORY.addNewManagerFactoryToKeyspace(keyspaceName, factory);
        REPOSITORY.addNewManagerToKeyspace(keyspaceName, manager);
        REPOSITORY.addNewAsyncManagerToKeyspace(keyspaceName, asyncManager);
        REPOSITORY.addNewSessionToKeyspace(keyspaceName, manager.getNativeSession());
    }

    private void buildClusterOnly(TypedMap parameters, ConfigMap achillesParameters, Boolean keyspaceDurableWrite, Cluster cluster) {
        final Session temporarySession;
        String keyspaceName = achillesParameters.getTyped(KEYSPACE_NAME);
        if (isNotBlank(keyspaceName)) {
            createKeyspaceIfNeeded(cluster, keyspaceName, keyspaceDurableWrite);
            temporarySession = cluster.connect(keyspaceName);
        } else {
            temporarySession = cluster.connect();
        }
        executeStartupScripts(temporarySession,parameters);
        temporarySession.close();
    }

    private String extractAndValidateKeyspaceName(ConfigMap parameters) {
        String keyspaceName = parameters.getTyped(KEYSPACE_NAME);
        Validator.validateNotBlank(keyspaceName, "The provided keyspace name should not be blank");
        Validator.validateTrue(KEYSPACE_NAME_PATTERN.matcher(keyspaceName).matches(),
                "The provided keyspace name '%s' should match the " + "following pattern : '%s'", keyspaceName,
                KEYSPACE_NAME_PATTERN.pattern());

        return keyspaceName;
    }

    private Cluster createCluster(String host, int cqlPort, TypedMap parameters) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating Cluster object with host/port {}/{} and parameters {}", host, cqlPort, parameters);
        }
        String clusterName = parameters.getTyped(CLUSTER_NAME);
        Compression compression = parameters.getTyped(COMPRESSION_TYPE);
        LoadBalancingPolicy loadBalancingPolicy = parameters.getTyped(LOAD_BALANCING_POLICY);
        RetryPolicy retryPolicy = parameters.getTyped(RETRY_POLICY);
        ReconnectionPolicy reconnectionPolicy = parameters.getTyped(RECONNECTION_POLICY);

        synchronized (this) {
            if (singletonCluster == null) {
                singletonCluster = Cluster.builder().addContactPoint(host).withPort(cqlPort).withClusterName(clusterName)
                        .withCompression(compression).withLoadBalancingPolicy(loadBalancingPolicy).withRetryPolicy(retryPolicy)
                        .withReconnectionPolicy(reconnectionPolicy).build();

                // Add Cluster for shutdown process
                ServerStarter.CASSANDRA_EMBEDDED.getShutdownHook().addCluster(singletonCluster);
            }
        }
        return singletonCluster;
    }

    private void createKeyspaceIfNeeded(Cluster cluster, String keyspaceName, Boolean keyspaceDurableWrite) {
        final Session session = cluster.connect("system");

        StringBuilder createKeyspaceStatement = new StringBuilder("CREATE keyspace IF NOT EXISTS ");
        createKeyspaceStatement.append(keyspaceName);
        createKeyspaceStatement.append(" WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1}");
        if (!keyspaceDurableWrite) {
            createKeyspaceStatement.append(" AND DURABLE_WRITES=false");
        }
        final String query = createKeyspaceStatement.toString();

        LOGGER.info("Creating keyspace : "+query);

        session.execute(query);
        session.close();
    }

    private void executeStartupScripts(Session session, TypedMap parameters) {
        List<String> scriptLocations = parameters.getTypedOr(SCRIPT_LOCATIONS, new ArrayList<String>());
        final ScriptExecutor scriptExecutor = new ScriptExecutor(session);
        for (String scriptLocation : scriptLocations) {
            scriptExecutor.executeScript(scriptLocation);
        }
    }

    public Cluster getSingletonCluster() {
        return singletonCluster;
    }
}
