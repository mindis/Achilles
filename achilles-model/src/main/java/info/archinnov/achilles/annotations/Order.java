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

package info.archinnov.achilles.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Deprecated, please use the
 * <strong>{@literal @}PartitionKey(value = x)</strong> and
 * <strong>{@literal @}ClusteringColumn(value = yyy)</strong> alternative
 */
@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
@Documented
public @interface Order {

	/**
	 * <p>
	 * Indicates the order. <strong>The order index start at 1</strong>
	 * </p>
	 */
	int value();

    /**
     * <p>
     * Define the <strong>clustering order</strong> on a clustering key. This
     * attribute has <strong>no effect</strong> on a partition key component
     * </p>
     *
     * <pre class="code"><code class="java">
     *
     *   {@literal @}EmbeddedId
     *   private CompoundKey compoundKey;
     *
     *   ...
     *
     *   public static class CompoundKey {
     *
     *      // Partition key
     *      {@literal @}Column
     *      <strong>{@literal @}Order(1)</strong>
     *      private Long userId;
     *
     *      // Clustering key
     *      {@literal @}Column
     *      {@literal @}Order(value = 2, <strong>reversed = true</strong>)
     *      private UUID time;
     *   }
     *
     * </code></pre>
     *
     */
	boolean reversed() default false;
}
