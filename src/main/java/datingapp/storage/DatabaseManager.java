package datingapp.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import datingapp.storage.schema.MigrationRunner;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Manages H2 database connection pooling and lifecycle. Schema creation is delegated to {@link
 * datingapp.storage.schema.SchemaInitializer} and migrations to {@link MigrationRunner}.
 */
public final class DatabaseManager {

    private static volatile String jdbcUrl = "jdbc:h2:./data/dating";
    private static final String DEFAULT_DEV_PASSWORD = "dev";
    private static final String USER = "sa";

    private static DatabaseManager instance;
    private volatile HikariDataSource dataSource;
    private volatile boolean initialized = false;

    public static void setJdbcUrl(String url) {
        jdbcUrl = Objects.requireNonNull(url, "JDBC URL cannot be null");
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    private DatabaseManager() {
        // Driver loaded automatically by SPI
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
        if (dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(USER);
        config.setPassword(getPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        dataSource = new HikariDataSource(config);
    }

    /** Gets a new database connection. Initializes the schema on first call. */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            initializeSchema();
        }
        if (dataSource == null) {
            initializePool();
        }
        return dataSource.getConnection();
    }

    /** Shuts down the database gracefully. */
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Schema initialization (delegates to extracted classes)
    // ═══════════════════════════════════════════════════════════════

    /** Initializes the database schema — delegates to SchemaInitializer and MigrationRunner. */
    private synchronized void initializeSchema() {
        if (initialized) {
            return;
        }

        if (dataSource == null) {
            initializePool();
        }

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            MigrationRunner.migrateV1(stmt);
            initialized = true;

        } catch (SQLException e) {
            throw new StorageException("Failed to initialize database schema", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Password resolution
    // ═══════════════════════════════════════════════════════════════

    private static String getPassword() {
        return getConfiguredPassword();
    }

    private static String getConfiguredPassword() {
        String envPassword = System.getenv("DATING_APP_DB_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            return envPassword;
        }

        if (isTestUrl(jdbcUrl)) {
            return "";
        }

        if (isLocalFileUrl(jdbcUrl)) {
            return DEFAULT_DEV_PASSWORD;
        }

        throw new IllegalStateException(
                "Database password must be provided via DATING_APP_DB_PASSWORD environment variable");
    }

    private static boolean isTestUrl(String url) {
        return url.contains("test") || url.contains(":mem:");
    }

    private static boolean isLocalFileUrl(String url) {
        return url.startsWith("jdbc:h2:./") || url.startsWith("jdbc:h2:\\") || url.startsWith("jdbc:h2:.");
    }
}
