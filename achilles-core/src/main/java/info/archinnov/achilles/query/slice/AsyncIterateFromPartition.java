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

package info.archinnov.achilles.query.slice;

import info.archinnov.achilles.internal.metadata.holder.EntityMeta;
import info.archinnov.achilles.internal.persistence.operations.SliceQueryExecutor;

public class AsyncIterateFromPartition<TYPE> extends AsyncIteratePartitionRoot<TYPE,AsyncIterateFromPartition<TYPE>> {

    protected AsyncIterateFromPartition(SliceQueryExecutor sliceQueryExecutor, Class<TYPE> entityClass, EntityMeta meta, SliceQueryProperties.SliceType sliceType) {
        super(sliceQueryExecutor, entityClass, meta, sliceType);
    }

    @Override
    protected AsyncIterateFromPartition<TYPE> getThis() {
        return AsyncIterateFromPartition.this;
    }

    /**
     *
     * Start the Iterate DSL with provided partition components IN
     *
     * <pre class="code"><code class="java">
     *
     *  asyncManager.sliceQuery(MessageEntity.class)
     *      .forIteration()
     *      .withPartitionComponents(10L)
     *      .andPartitionComponentsIN(2013, 2014)
     *
     * </code></pre>
     *
     * Generated CQL query:
     *
     * <br/>
     *  SELECT * FROM messages WHERE user_id=10 AND year IN (2013,2014)
     *
     * @return slice DSL
     */
    public AsyncIterateFromPartition<TYPE> andPartitionComponentsIN(Object... partitionKeyComponentsIn) {
        super.andPartitionKeysINInternal(partitionKeyComponentsIn);
        return AsyncIterateFromPartition.this;
    }
}
