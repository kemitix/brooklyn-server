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
package org.apache.brooklyn.core.test.entity;

import java.util.Map;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.ha.OsgiManager;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.server.BrooklynServerConfig;

/** management context which allows disabling common time-consuming tasks.
 * most instances have:
 * <li> empty properties
 * <li> no catalog
 * <li> persistence off
 * <li> osgi off
 * <p>
 * the constructor, {@link #newInstance()}, and {@link #builder(boolean)} (with true) return the above;
 * the constructor and the builder allow custom properties to be set,
 * and the builder allows individual items to be turned back on.
 */
public class LocalManagementContextForTests extends LocalManagementContext {

    protected LocalManagementContextForTests(BrooklynProperties brooklynProperties, boolean minimal) {
        super(builder(minimal).useProperties(brooklynProperties).buildProperties());
    }
    
    public LocalManagementContextForTests() {
        this(null);
    }
    
    public LocalManagementContextForTests(BrooklynProperties brooklynProperties) {
        this(brooklynProperties, true);
    }
    
    private static BrooklynProperties emptyIfNull(BrooklynProperties bp) {
        if (bp!=null) return bp;
        return BrooklynProperties.Factory.newEmpty();
    }

    public static BrooklynProperties setEmptyCatalogAsDefault(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        brooklynProperties.putIfAbsent(BrooklynServerConfig.BROOKLYN_CATALOG_URL, ManagementContextInternal.EMPTY_CATALOG_URL);
        return brooklynProperties;
    }
    
    public static BrooklynProperties disableOsgi(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        setFailingIfConflicting(brooklynProperties, BrooklynServerConfig.USE_OSGI, false);
        return brooklynProperties;
    }
    
    public static BrooklynProperties reuseOsgi(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        setFailingIfConflicting(brooklynProperties, OsgiManager.REUSE_OSGI, true);
        return brooklynProperties;
    }
    
    @SuppressWarnings("deprecation")
    public static BrooklynProperties disablePersistenceBackups(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        setFailingIfConflicting(brooklynProperties, BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED_ON_DEMOTION, false);
        setFailingIfConflicting(brooklynProperties, BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED_ON_PROMOTION, false);
        setFailingIfConflicting(brooklynProperties, BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED, false);
        return brooklynProperties;
    }
    
    private static <T> void setFailingIfConflicting(BrooklynProperties brooklynProperties, ConfigKey<T> key, T value) {
        Object old = brooklynProperties.put(key, value);
        if (old != null && !old.equals(value)) {
            throw new IllegalStateException("Cannot change value for '" + key + "' from " + old + " to " + value);
        }
    }

    public static class Builder {
        boolean disablePersistenceBackups = false;
        boolean disableOsgi = false;
        boolean reuseOsgi = false;
        boolean emptyCatalog = false;
        BrooklynProperties properties = null;
        Map<String, ?> additionalProperties = null;
        
        public Builder disablePersistenceBackups() { return disablePersistenceBackups(true); }
        public Builder disableOsgi() { disableOsgi = true; return this; }
        public Builder emptyCatalog() { return emptyCatalog(true); }

        public Builder disablePersistenceBackups(boolean disablePersistenceBackups) { this.disablePersistenceBackups = disablePersistenceBackups; return this; }
        
        /** @deprecated since 0.12.0 use {@link #setOsgiEnablementAndReuse(boolean, boolean)}  */
        @Deprecated
        public Builder disableOsgi(boolean disableOsgi) { this.disableOsgi = disableOsgi; return this; }
        
        /** sets the underlying fields more usually controlled by convenience methods 
         * {@link #disableOsgi()} or {@link #enableOsgiReusable()} or {@link #enableOsgiNonReusable()};
         * this method is available as a convenience for nested builders */
        public Builder setOsgiEnablementAndReuse(boolean enableOsgi, boolean reuseOsgi) {
            this.disableOsgi = !enableOsgi;
            this.reuseOsgi = reuseOsgi;
            return this;
        }
        
        /** enables OSGi and sets the flag that a container can be reused;
         * this is the default if {@link #minimal()} is specdified and then OSGi enabled */
        public Builder enableOsgiReusable() {
            disableOsgi = false;
            reuseOsgi = true;
            return this;
        }
        /** enables OSGi and forces a management context to use its own non-reusable OSGi container;
         * this is the default in normal Brooklyn and the same behaviour is applied if minimal is not specified,
         * but if minimal is specified this method must be used if a re-used OSGi container is required */
        public Builder enableOsgiNonReusable() {
            disableOsgi = false;
            reuseOsgi = false;
            return this;
        }
        
        public Builder emptyCatalog(boolean emptyCatalog) { this.emptyCatalog = emptyCatalog; return this; }

        // for use in the outer class's constructor
        private Builder minimal(boolean really) {
            if (really) minimal();
            return this;
        }
        
        public Builder minimal() {
            disablePersistenceBackups();
            disableOsgi();
            reuseOsgi = true;
            emptyCatalog();
            properties = null;
            additionalProperties = null;
            return this;
        }
        
        public Builder useAdditionalProperties(Map<String, ?> additionalProperties) {
            if (hasCustomProperties() && additionalProperties != null)
                throw new IllegalStateException("Cannot set multiple properties");
            this.additionalProperties = additionalProperties; 
            return this; 
        }
        
        public Builder useProperties(BrooklynProperties properties) {
            if (hasCustomProperties() && properties != null)
                throw new IllegalStateException("Cannot set multiple properties");
            this.properties = properties; 
            return this; 
        }
        
        protected boolean hasCustomProperties() {
            return (properties != null) || (additionalProperties != null && additionalProperties.size() > 0);
        }
        
        public BrooklynProperties buildProperties() {
            BrooklynProperties result = emptyIfNull(properties);
            if (additionalProperties != null) result.putAll(additionalProperties);
            if (disablePersistenceBackups) LocalManagementContextForTests.disablePersistenceBackups(result);
            if (disableOsgi) LocalManagementContextForTests.disableOsgi(result);
            else if (reuseOsgi) LocalManagementContextForTests.reuseOsgi(result);
            if (emptyCatalog) LocalManagementContextForTests.setEmptyCatalogAsDefault(result);
            return result;
        }
        
        public LocalManagementContext build() {
            return new LocalManagementContextForTests(buildProperties(), false);
        }
        public Builder useDefaultProperties() {
            properties = BrooklynProperties.Factory.newDefault();
            return this;
        }
    }
    
    /** Create a new builder, defaulting to empty properties, and with the parameter determining whether 
     * by default to disable common things disabled in tests (and the caller can re-enable selected ones individually)
     * or (if false) leaving everything enabled (so the caller turns things off) */
    public static Builder builder(boolean minimal) { return new Builder().minimal(minimal); }
    
    /** Creates a new minimal instance */
    public static LocalManagementContext newInstance() {
        return builder(true).build();
    }

    /** Creates a new minimal instance with the given properties then installed. */
    public static LocalManagementContext newInstance(BrooklynProperties properties) {
        return builder(true).useProperties(properties).build();
    }

    /** Creates a new minimal instance with OSGi then enabled. */
    public static LocalManagementContext newInstanceWithOsgi() {
        return builder(true).enableOsgiReusable().build();
    }

    @Override
    public BrooklynCatalog getCatalog() {
        if (!getCatalogInitialization().hasRunAnyInitialization()) {
            // Before catalog init, the catalog will be empty.
            // Normally the BasicLauncher (either the classic BrooklynLauncher or the OsgiLauncher)
            // will have ensured the catalog initialization is called. But for tests, the lifecycle
            // for the management context is unfortunately different.
            getCatalogInitialization().unofficialPopulateInitialCatalog();
        }
        return catalog;
    }
}
