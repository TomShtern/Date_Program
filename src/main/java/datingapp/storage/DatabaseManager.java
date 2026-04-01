package datingapp.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import datingapp.storage.schema.MigrationRunner;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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
    private volatile boolean initialized = false;
    private volatile int queryTimeoutSeconds = 30;

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
        String explicitPassword = resolveExplicitPassword();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(resolveJdbcUrl(configuredJdbcUrl, configuredProfile, explicitPassword));
        config.setUsername(USER);
        config.setPassword(resolvePassword(explicitPassword, configuredProfile, configuredJdbcUrl));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(3000);
        dataSource.set(new HikariDataSource(config));
    }

    private String effectiveJdbcUrl() {
        return configuredJdbcUrlOverride != null ? configuredJdbcUrlOverride : jdbcUrl;
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
        return resolvePassword(resolveExplicitPassword(), resolveConfiguredProfile(), configuredJdbcUrl);
    }

    private static String resolveExplicitPassword() {
        return firstNonBlank(System.getProperty(DB_PASSWORD_PROPERTY), System.getenv(DB_PASSWORD_ENV));
    }

    private static String resolvePassword(String explicitPassword, String configuredProfile, String configuredJdbcUrl) {
        if (explicitPassword != null) {
            return explicitPassword;
        }

        if (isExplicitDevOrTestProfile(configuredProfile)) {
            return "";
        }

        if (isLocalFileUrl(configuredJdbcUrl)) {
            throw new IllegalStateException(
                    "Local file databases require an explicit password or an explicit database profile (test/dev)");
        }

        throw new IllegalStateException(
                "Database password must be provided via datingapp.db.password or DATING_APP_DB_PASSWORD");
    }

    private static String resolveJdbcUrl(String configuredJdbcUrl, String configuredProfile, String explicitPassword) {
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
        return firstNonBlank(System.getProperty(DB_PROFILE_PROPERTY), System.getenv(DB_PROFILE_ENV));
    }

    private static boolean isExplicitDevOrTestProfile(String profile) {
        return TEST_PROFILE.equalsIgnoreCase(profile) || DEV_PROFILE.equalsIgnoreCase(profile);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private void applySessionQueryTimeout(Connection connection) {
        long timeoutMillisLong = queryTimeoutSeconds * 1000L;
        long safeTimeoutMillis = Math.min(timeoutMillisLong, Integer.MAX_VALUE);
        String sql = "SET QUERY_TIMEOUT " + safeTimeoutMillis;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new StorageException("Failed to apply query timeout", e);
        }
    }

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
