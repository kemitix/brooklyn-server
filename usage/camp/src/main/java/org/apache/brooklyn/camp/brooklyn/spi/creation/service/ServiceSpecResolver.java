/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.camp.brooklyn.spi.creation.service;

import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;

/**
 * Resolves and decorates {@link EntitySpec entity specifications} based on the {@code serviceType} in a template.
 * <p>
 * The resolver implementation will use the rest of the local part of the service type information
 * to create and decorate an appropriate {@link EntitySpec entity}.
 * <p>
 * The resolvers are loaded using the {@link ServiceLoader} mechanism, allowing external libraries
 * to add extra service type implementations that will be picked up at runtime.
 */
// TODO Not CAMP specific, move to core, to be reused by other parsers
public interface ServiceSpecResolver extends ManagementContextInjectable {
    /**
     * Uniquely identifies the resolver, can be used to address the same resolver at a later point in time.
     * For implementations: this usually matches the service type prefix, but not required.
     */
    String getName();

    /**
     * @return if the resolver can create a spec for the service type
     */
    boolean accepts(String type, BrooklynClassLoadingContext loader);

    /**
     * Create a spec for the service type
     */
    @Nullable EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes);
}
