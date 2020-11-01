package com.grommitz.jpadatetime;

import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;

import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceUnitTransactionType;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Build an EntityManagerFactory programmatically without any xml.
 *
 * Example usage:
 * <pre>
 *  emf = EmfBuilder.forMySQL()
 *          .setProperty("hibernate.connection.password", "mypassword")
 *          .build();
 * </pre>
 */
public class EmfBuilder {

    private Properties properties = new Properties();
    private Collection<Class> entityClasses = new ArrayList<>();
    private String puName = "testpu";

    /**
     * Convenience factory method with reasonable setup for mysql 8
     *
     * The default database name is "mysqltestdb" - this must exist on the server
     * prior to running the test, but it can be empty. The db server is localhost.
     *
     * To override this, set the property "hibernate.connection.url"
     *
     * @return A new EmfBuilder instance
     */
    public static EmfBuilder forMySQL() {
        return new EmfBuilder()
                .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                .setProperty("hibernate.connection.connectionCollation", "utf8mb4_unicode_ci")
                .setProperty("hibernate.connection.url", "jdbc:mysql://localhost/mysqltestdb")
                .setProperty("hibernate.connection.username", "root")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.current_session_context_class", "thread")
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect")
                //.setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
    }

    /**
     * Convenience factory method with reasonable setup for H2 database
     * which is on disk at ./h2testdb
     *
     * @return A new EmfBuilder instance
     */
    public static EmfBuilder forH2() {
        EmfBuilder builder = new EmfBuilder();
		builder.properties.put("hibernate.connection.driver_class", "org.h2.Driver");
		builder.properties.put("hibernate.connection.url", "jdbc:h2:./h2testdb");
		builder.properties.put("hibernate.connection.username", "sa");
		builder.properties.put("hibernate.connection.password", "");
		builder.properties.put("hibernate.connection.pool_size", "2");
		builder.properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
		builder.properties.put("hibernate.show_sql", "false");
		builder.properties.put("hibernate.hbm2ddl.auto", "create-drop");
		builder.properties.put("hibernate.current_session_context_class", "thread");
        return builder;
    }

    /**
     * Convenience factory method with reasonable setup for H2 in-memory database
     * <br><br>
     * Note the use of DB_CLOSE_DELAY=-1 in the jdbc url - this prevents the normal behaviour
     * which removes all data from the database the moment the last connection is closed.
     *
     * @return A new EmfBuilder instance
     */
    public static EmfBuilder forH2InMemory() {
        return forH2()
				.setProperty("hibernate.connection.pool_size", "10")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:test;" +
                        "DB_CLOSE_DELAY=-1;" +
                        "MODE=MySQL;" +
                        //"DATABASE_TO_UPPER=false;" +
                        "INIT=CREATE SCHEMA IF NOT EXISTS public\\; SET SCHEMA public;");
    }

    private EmfBuilder() {}

    public EntityManagerFactory build() {
        return Bootstrap.getEntityManagerFactoryBuilder(
                new CustomPersistenceUnitDescriptor(),
                Collections.emptyMap()
        ).build();
    }

    /**
     * Set an arbitrary property of the persistence unit.
     *
     * @param key The key fr the PU properties
     * @param value The value
     * @return this
     */
    public EmfBuilder setProperty(String key, String value) {
        this.properties.put(key, value);
        return this;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Add some more entities to the entities we know about.
     * @param classes Some more entities
     * @return this
     */
    public EmfBuilder withEntities(Collection<Class> classes) {
        this.entityClasses.addAll(classes);
        return this;
    }

    public EmfBuilder withPuName(String puName) {
        this.puName = puName;
        return this;
    }

    public DatabaseConnectionInfo getDatabaseConnectionInfo() {
        return new DatabaseConnectionInfo(
                this.properties.getProperty("hibernate.connection.url"),
                this.properties.getProperty("hibernate.connection.username"),
                this.properties.getProperty("hibernate.connection.password")
        );
    }

    /**
     * See {@link #withEntities(Collection)}
     * @param classes Some more entities
     * @return this
     */
    public EmfBuilder withEntities(Class... classes) {
        return withEntities(Arrays.asList(classes));
    }

	private class CustomPersistenceUnitDescriptor implements PersistenceUnitDescriptor {

        @Override
        public String getName() {
            return puName;
        }

        @Override
        public PersistenceUnitTransactionType getTransactionType() {
            return PersistenceUnitTransactionType.RESOURCE_LOCAL;
        }

        @Override
        public List<String> getManagedClassNames() {
            return entityClasses.stream()
                    .map(Class::getCanonicalName)
                    .collect(Collectors.toList());
        }

        @Override
        public Properties getProperties() {
            return properties;
        }

        @Override
        public URL getPersistenceUnitRootUrl() {
            return null;
        }

        @Override
        public String getProviderClassName() {
            return null;
        }

        @Override
        public boolean isUseQuotedIdentifiers() {
            return false;
        }

        @Override
        public boolean isExcludeUnlistedClasses() {
            return false;
        }

        @Override
        public ValidationMode getValidationMode() {
            return null;
        }

        @Override
        public SharedCacheMode getSharedCacheMode() {
            return null;
        }

        @Override
        public List<String> getMappingFileNames() {
            return null;
        }

        @Override
        public List<URL> getJarFileUrls() {
            return null;
        }

        @Override
        public Object getNonJtaDataSource() {
            return null;
        }

        @Override
        public Object getJtaDataSource() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }

        @Override
        public ClassLoader getTempClassLoader() {
            return null;
        }

        @Override
        public void pushClassTransformer(EnhancementContext enhancementContext) {

        }

    }

    public static class DatabaseConnectionInfo {

        private final String jdbcUrl;
        private final String username;
        private final String password;

        public DatabaseConnectionInfo(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        public String jdbcUrl() {
            return jdbcUrl;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }
    }
}
