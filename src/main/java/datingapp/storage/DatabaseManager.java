package datingapp.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import datingapp.core.AppConfig;
import datingapp.core.RuntimeEnvironment;
import datingapp.storage.schema.MigrationRunner;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Manages H2 database connection pooling and lifecycle. Schema creation is
 * delegated to {@link
 * datingapp.storage.schema.SchemaInitializer} and migrations to
 * {@link MigrationRunner}.
 */
public final class DatabaseManager {

    private static final String DEFAULT_JDBC_URL = "jdbc:h2:./data/dating";
    private static final String DEFAULT_DEV_JDBC_URL = "jdbc:h2:./data/dating-dev";
    private static final String DEFAULT_TEST_JDBC_URL = "jdbc:h2:./data/dating-test";
    private static volatile String jdbcUrl = DEFAULT_JDBC_URL;
    private static final String DB_PASSWORD_PROPERTY = "datingapp.db.password";
    private static final String DB_PASSWORD_ENV = "DATING_APP_DB_PASSWORD";
    private static final String DB_PROFILE_PROPERTY = "datingapp.db.profile";
    private static final String DB_PROFILE_ENV = "DATING_APP_DB_PROFILE";
    private static final String TEST_PROFILE = "test";
    private static final String DEV_PROFILE = "dev";
    private static final String USER = "sa";

    private static DatabaseManager instance;
    private final String configuredJdbcUrlOverride;
    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();
    private final AtomicReference<RuntimeStorageState> runtimeStorageState = new AtomicReference<>();
    private volatile boolean initialized = false;
    private volatile int queryTimeoutSeconds = 30;

    /** Immutable pool configuration swapped atomically to prevent partial reads. */
    private static final class PoolConfig {
        final int maxPoolSize;
        final int minIdle;
        final int connectionTimeoutSeconds;
        final int validationTimeoutSeconds;
        final int idleTimeoutSeconds;
        final int maxLifetimeSeconds;
        final int keepaliveTimeSeconds;

        PoolConfig(
                int maxPoolSize,
                int minIdle,
                int connectionTimeoutSeconds,
                int validationTimeoutSeconds,
                int idleTimeoutSeconds,
                int maxLifetimeSeconds,
                int keepaliveTimeSeconds) {
            this.maxPoolSize = maxPoolSize;
            this.minIdle = minIdle;
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
            this.validationTimeoutSeconds = validationTimeoutSeconds;
            this.idleTimeoutSeconds = idleTimeoutSeconds;
            this.maxLifetimeSeconds = maxLifetimeSeconds;
            this.keepaliveTimeSeconds = keepaliveTimeSeconds;
        }
    }

    private static final PoolConfig DEFAULT_POOL_CONFIG = new PoolConfig(10, 2, 5, 3, 600, 1800, 0);
    private final AtomicReference<PoolConfig> configRef = new AtomicReference<>(DEFAULT_POOL_CONFIG);

    public static void setJdbcUrl(String url) {
        jdbcUrl = Objects.requireNonNull(url, "JDBC URL cannot be null");
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
        jdbcUrl = DEFAULT_JDBC_URL;
    }

    private DatabaseManager() {
        this(null);
    }

    private DatabaseManager(String configuredJdbcUrlOverride) {
        this.configuredJdbcUrlOverride = configuredJdbcUrlOverride;
        // Driver loaded automatically by SPI
    }

    static DatabaseManager createIsolated(String jdbcUrl) {
        return new DatabaseManager(Objects.requireNonNull(jdbcUrl, "JDBC URL cannot be null"));
    }

    public void configureQueryTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("query timeout must be positive");
        }
        this.queryTimeoutSeconds = timeoutSeconds;
    }

    public void configurePoolSettings(AppConfig.StorageConfig storageConfig) {
        Objects.requireNonNull(storageConfig, "storageConfig cannot be null");
        if (storageConfig.maxPoolSize() <= 0) {
            throw new IllegalArgumentException("maxPoolSize must be positive");
        }
        if (storageConfig.minIdle() < 0 || storageConfig.minIdle() > storageConfig.maxPoolSize()) {
            throw new IllegalArgumentException("minIdle must be between 0 and maxPoolSize");
        }
        if (storageConfig.connectionTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("connectionTimeoutSeconds must be positive");
        }
        long connectionTimeoutMs = storageConfig.connectionTimeoutSeconds() * 1000L;
        if (connectionTimeoutMs < 250) {
            throw new IllegalArgumentException(
                    "connectionTimeoutSeconds must be at least 1 second (250 ms Hikari minimum)");
        }
        if (storageConfig.validationTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("validationTimeoutSeconds must be positive");
        }
        if (storageConfig.validationTimeoutSeconds() >= storageConfig.connectionTimeoutSeconds()) {
            throw new IllegalArgumentException("validationTimeoutSeconds must be less than connectionTimeoutSeconds");
        }
        if (storageConfig.idleTimeoutSeconds() < 0) {
            throw new IllegalArgumentException("idleTimeoutSeconds must be non-negative");
        }
        if (storageConfig.maxLifetimeSeconds() < 0) {
            throw new IllegalArgumentException("maxLifetimeSeconds must be non-negative");
        }
        if (storageConfig.maxLifetimeSeconds() > 0
                && storageConfig.maxLifetimeSeconds() < storageConfig.idleTimeoutSeconds()) {
            throw new IllegalArgumentException("maxLifetimeSeconds must be >= idleTimeoutSeconds (or 0 for disabled)");
        }
        if (storageConfig.keepaliveTimeSeconds() < 0) {
            throw new IllegalArgumentException("keepaliveTimeSeconds must be non-negative");
        }

        PoolConfig newConfig = new PoolConfig(
                storageConfig.maxPoolSize(),
                storageConfig.minIdle(),
                storageConfig.connectionTimeoutSeconds(),
                storageConfig.validationTimeoutSeconds(),
                storageConfig.idleTimeoutSeconds(),
                storageConfig.maxLifetimeSeconds(),
                storageConfig.keepaliveTimeSeconds());
        configRef.set(newConfig);
    }

    public synchronized void configureStorage(AppConfig.StorageConfig storageConfig) {
        configurePoolSettings(storageConfig);
        configureStorage(
                storageConfig,
                DatabaseDialect.fromConfig(storageConfig.databaseDialect(), storageConfig.databaseUrl()));
    }

    synchronized void configureStorage(AppConfig.StorageConfig storageConfig, DatabaseDialect dialect) {
        Objects.requireNonNull(storageConfig, "storageConfig cannot be null");
        Objects.requireNonNull(dialect, "dialect cannot be null");
        if (dataSource.get() != null || initialized) {
            shutdown();
        }
        runtimeStorageState.set(new RuntimeStorageState(storageConfig, dialect));
        configureQueryTimeoutSeconds(storageConfig.queryTimeoutSeconds());
    }

    synchronized void clearRuntimeStorageConfiguration() {
        if (dataSource.get() != null || initialized) {
            shutdown();
        }
        runtimeStorageState.set(null);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    // ═══════════════════════════════════════════════════════════════
    // Connection pool
    // ═══════════════════════════════════════════════════════════════

    private synchronized void initializePool() {
        if (dataSource.get() != null) {
            return;
        }
        final String configuredJdbcUrl = effectiveJdbcUrl();
        String configuredProfile = resolveConfiguredProfile();
        String explicitPassword = resolveExplicitPassword(configuredJdbcUrl);
        RuntimeStorageState storageState = runtimeStorageState.get();
        DatabaseDialect dialect =
                storageState != null ? storageState.dialect() : DatabaseDialect.fromJdbcUrl(configuredJdbcUrl);
        PoolConfig cfg = configRef.get();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(resolveJdbcUrl(configuredJdbcUrl, configuredProfile, explicitPassword, dialect));
        config.setUsername(resolveUsername());
        config.setPassword(resolvePassword(explicitPassword, configuredProfile, configuredJdbcUrl, dialect));
        config.setMaximumPoolSize(cfg.maxPoolSize);
        config.setMinimumIdle(cfg.minIdle);
        config.setConnectionTimeout(cfg.connectionTimeoutSeconds * 1000L);
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(cfg.validationTimeoutSeconds * 1000L);
        config.setIdleTimeout(cfg.idleTimeoutSeconds * 1000L);
        config.setMaxLifetime(cfg.maxLifetimeSeconds * 1000L);
        if (cfg.keepaliveTimeSeconds > 0) {
            config.setKeepaliveTime(cfg.keepaliveTimeSeconds * 1000L);
        }
        dataSource.set(new HikariDataSource(config));
    }

    private String effectiveJdbcUrl() {
        RuntimeStorageState storageState = runtimeStorageState.get();
        if (configuredJdbcUrlOverride != null) {
            return configuredJdbcUrlOverride;
        }
        if (storageState != null) {
            String runtimeJdbcUrl = storageState.storageConfig().databaseUrl();
            if (!DEFAULT_JDBC_URL.equals(runtimeJdbcUrl)) {
                return runtimeJdbcUrl;
            }
        }
        return jdbcUrl;
    }

    private String resolveUsername() {
        RuntimeStorageState storageState = runtimeStorageState.get();
        return storageState != null ? storageState.storageConfig().databaseUsername() : USER;
    }

    /**
     * Gets a new database connection. Initializes the schema on first call.
     * Thread-safe.
     */
    @SuppressWarnings("PMD.CloseResource")
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            initializeSchema();
        } else if (dataSource.get() == null) {
            initializePool();
        }

        HikariDataSource localDataSource = dataSource.get();
        if (localDataSource == null) {
            throw new StorageException("Connection pool is not initialized");
        }

        Connection connection = localDataSource.getConnection();
        applySessionQueryTimeout(connection);
        return connection;
    }

    /** Shuts down the database gracefully. */
    @SuppressWarnings("PMD.CloseResource")
    public synchronized void shutdown() {
        HikariDataSource existingDataSource = dataSource.getAndSet(null);
        if (existingDataSource != null) {
            existingDataSource.close();
        }
        initialized = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema initialization (delegates to extracted classes)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initializes the database schema — delegates to SchemaInitializer and
     * MigrationRunner.
     */
    @SuppressWarnings("PMD.CloseResource")
    private synchronized void initializeSchema() {
        if (initialized) {
            return;
        }

        if (dataSource.get() == null) {
            initializePool();
        }

        HikariDataSource localDataSource = dataSource.get();
        if (localDataSource == null) {
            throw new StorageException("Connection pool is not initialized");
        }

        try (Connection conn = localDataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            MigrationRunner.runAllPending(stmt);
            initialized = true;

        } catch (SQLException e) {
            throw new StorageException("Failed to initialize database schema", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Password resolution
    // ═══════════════════════════════════════════════════════════════
    // Supported explicit profiles:
    // - test: allow passwordless local file or in-memory database access
    // - dev: allow passwordless local file database access during intentional development runs

    static String getConfiguredPassword() {
        String configuredJdbcUrl = jdbcUrl;
        return resolvePassword(
                resolveExplicitPassword(configuredJdbcUrl),
                resolveConfiguredProfile(),
                configuredJdbcUrl,
                DatabaseDialect.fromJdbcUrl(configuredJdbcUrl));
    }

    private static String resolveExplicitPassword(String configuredJdbcUrl) {
        return resolveExplicitPassword(
                configuredJdbcUrl, System::getProperty, System::getenv, RuntimeEnvironment::getEnv);
    }

    static String resolveExplicitPassword(
            String configuredJdbcUrl,
            UnaryOperator<String> propertyLookup,
            UnaryOperator<String> envLookup,
            UnaryOperator<String> runtimeEnvLookup) {
        String propertyPassword = propertyLookup.apply(DB_PASSWORD_PROPERTY);
        if (propertyPassword != null && !propertyPassword.isBlank()) {
            return propertyPassword;
        }

        DatabaseDialect dialect = DatabaseDialect.fromJdbcUrl(configuredJdbcUrl);
        String processEnvPassword = envLookup.apply(DB_PASSWORD_ENV);
        if (processEnvPassword != null && !processEnvPassword.isBlank()) {
            boolean requireExplicitRuntimeMatch = dialect == DatabaseDialect.H2;
            if (matchesConfiguredRuntime(dialect, configuredJdbcUrl, envLookup, requireExplicitRuntimeMatch)) {
                return processEnvPassword;
            }
        }

        if (dialect != DatabaseDialect.POSTGRESQL) {
            return null;
        }

        String dotEnvPassword = runtimeEnvLookup.apply(DB_PASSWORD_ENV);
        if (dotEnvPassword == null || dotEnvPassword.isBlank()) {
            return null;
        }

        if (!matchesConfiguredRuntime(dialect, configuredJdbcUrl, runtimeEnvLookup, false)) {
            return null;
        }

        return dotEnvPassword;
    }

    private static boolean matchesConfiguredRuntime(
            DatabaseDialect dialect,
            String configuredJdbcUrl,
            UnaryOperator<String> envLookup,
            boolean requireExplicitRuntimeMatch) {
        String configuredDialect = envLookup.apply("DATING_APP_DB_DIALECT");
        String configuredRuntimeUrl = envLookup.apply("DATING_APP_DB_URL");
        boolean hasExplicitRuntimeMatch = false;

        if (configuredDialect != null && !configuredDialect.isBlank()) {
            if (DatabaseDialect.fromConfig(configuredDialect, configuredJdbcUrl) != dialect) {
                return false;
            }
            hasExplicitRuntimeMatch = true;
        }

        if (configuredRuntimeUrl != null && !configuredRuntimeUrl.isBlank()) {
            if (!configuredRuntimeUrl.equals(configuredJdbcUrl)) {
                return false;
            }
            hasExplicitRuntimeMatch = true;
        }

        return !requireExplicitRuntimeMatch || hasExplicitRuntimeMatch;
    }

    private static String resolvePassword(
            String explicitPassword, String configuredProfile, String configuredJdbcUrl, DatabaseDialect dialect) {
        if (explicitPassword != null) {
            return explicitPassword;
        }

        if (dialect == DatabaseDialect.H2 && isExplicitDevOrTestProfile(configuredProfile)) {
            return "";
        }

        if (dialect == DatabaseDialect.H2 && isLocalFileUrl(configuredJdbcUrl)) {
            throw new IllegalStateException(
                    "Local file databases require an explicit password or an explicit database profile (test/dev)");
        }

        throw new IllegalStateException(
                "Database password must be provided via datingapp.db.password or DATING_APP_DB_PASSWORD");
    }

    static String resolvePassword(String explicitPassword, String configuredProfile, String configuredJdbcUrl) {
        return resolvePassword(
                explicitPassword, configuredProfile, configuredJdbcUrl, DatabaseDialect.fromJdbcUrl(configuredJdbcUrl));
    }

    static String resolveJdbcUrl(String configuredJdbcUrl, String configuredProfile, String explicitPassword) {
        return resolveJdbcUrl(
                configuredJdbcUrl, configuredProfile, explicitPassword, DatabaseDialect.fromJdbcUrl(configuredJdbcUrl));
    }

    private static String resolveJdbcUrl(
            String configuredJdbcUrl, String configuredProfile, String explicitPassword, DatabaseDialect dialect) {
        if (dialect == DatabaseDialect.POSTGRESQL) {
            return configuredJdbcUrl;
        }
        if (explicitPassword != null || !DEFAULT_JDBC_URL.equals(configuredJdbcUrl)) {
            return configuredJdbcUrl;
        }
        if (DEV_PROFILE.equalsIgnoreCase(configuredProfile)) {
            return DEFAULT_DEV_JDBC_URL;
        }
        if (TEST_PROFILE.equalsIgnoreCase(configuredProfile)) {
            return DEFAULT_TEST_JDBC_URL;
        }
        return configuredJdbcUrl;
    }

    private static boolean isLocalFileUrl(String url) {
        return url.startsWith("jdbc:h2:./") || url.startsWith("jdbc:h2:\\") || url.startsWith("jdbc:h2:.");
    }

    private static String resolveConfiguredProfile() {
        return RuntimeEnvironment.lookup(DB_PROFILE_PROPERTY, DB_PROFILE_ENV);
    }

    private static boolean isExplicitDevOrTestProfile(String profile) {
        return TEST_PROFILE.equalsIgnoreCase(profile) || DEV_PROFILE.equalsIgnoreCase(profile);
    }

    private void applySessionQueryTimeout(Connection connection) {
        long timeoutMillisLong = queryTimeoutSeconds * 1000L;
        long safeTimeoutMillis = Math.min(timeoutMillisLong, Integer.MAX_VALUE);
        RuntimeStorageState storageState = runtimeStorageState.get();
        DatabaseDialect dialect =
                storageState != null ? storageState.dialect() : DatabaseDialect.fromJdbcUrl(effectiveJdbcUrl());
        try (Statement statement = connection.createStatement()) {
            if (dialect == DatabaseDialect.POSTGRESQL) {
                statement.execute("SET search_path TO public");
                statement.execute("SET TIME ZONE 'UTC'");
                statement.execute("SET statement_timeout TO " + safeTimeoutMillis);
            } else {
                statement.execute("SET TIME ZONE 'UTC'");
                statement.execute("SET QUERY_TIMEOUT " + safeTimeoutMillis);
            }
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new StorageException("Failed to apply query timeout", e);
        }
    }

    private record RuntimeStorageState(AppConfig.StorageConfig storageConfig, DatabaseDialect dialect) {}

    // ═══════════════════════════════════════════════════════════════
    // StorageException (nested)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Unchecked exception for storage-related errors.
     * Wraps SQLException and other storage exceptions.
     */
    public static class StorageException extends RuntimeException {

        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }

        public StorageException(Throwable cause) {
            super(cause);
        }
    }
}
