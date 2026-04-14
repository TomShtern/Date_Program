package datingapp.support;

import datingapp.core.RuntimeEnvironment;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;

/** Shared opt-in configuration for tests that intentionally talk to a live PostgreSQL instance. */
public final class LivePostgresqlTestConfig {

    private static final String URL_PROPERTY = "datingapp.pgtest.url";
    private static final String HOST_PROPERTY = "datingapp.pgtest.host";
    private static final String PORT_PROPERTY = "datingapp.pgtest.port";
    private static final String DATABASE_PROPERTY = "datingapp.pgtest.database";
    private static final String USERNAME_PROPERTY = "datingapp.pgtest.username";
    private static final String PASSWORD_PROPERTY = "datingapp.pgtest.password";

    private static final String URL_ENV = "DATING_APP_DB_URL";
    private static final String HOST_ENV = "DATING_APP_DB_HOST";
    private static final String PORT_ENV = "DATING_APP_DB_PORT";
    private static final String DATABASE_NAME_ENV = "DATING_APP_DB_NAME";
    private static final String DATABASE_ENV = "DATING_APP_DB_DATABASE";
    private static final String USERNAME_ENV = "DATING_APP_DB_USERNAME";
    private static final String PASSWORD_ENV = "DATING_APP_DB_PASSWORD";
    private static final int DEFAULT_POSTGRES_PORT = 5432;

    private LivePostgresqlTestConfig() {}

    public static void assumeConfigured() {
        Assumptions.assumeTrue(
                load().isPresent(),
                "Live PostgreSQL tests require datingapp.pgtest.* system properties or DATING_APP_DB_* environment variables");
    }

    public static ConnectionInfo requireConfig() {
        return load().orElseThrow(
                        () -> new IllegalStateException(
                                "Live PostgreSQL config is missing. Provide datingapp.pgtest.* system properties or DATING_APP_DB_* environment variables."));
    }

    public static Connection openConnection() throws SQLException {
        ConnectionInfo config = requireConfig();
        return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
    }

    private static Optional<ConnectionInfo> load() {
        JdbcUrlParts urlParts = parseConfiguredJdbcUrl().orElse(null);
        String host = firstNonBlank(
                System.getProperty(HOST_PROPERTY),
                RuntimeEnvironment.getEnv(HOST_ENV),
                urlParts != null ? urlParts.host() : null);
        String portValue = firstNonBlank(
                System.getProperty(PORT_PROPERTY),
                RuntimeEnvironment.getEnv(PORT_ENV),
                urlParts != null ? Integer.toString(urlParts.port()) : null);
        String databaseName = firstNonBlank(
                System.getProperty(DATABASE_PROPERTY),
                RuntimeEnvironment.getEnv(DATABASE_NAME_ENV),
                RuntimeEnvironment.getEnv(DATABASE_ENV),
                urlParts != null ? urlParts.databaseName() : null);
        String username = firstNonBlank(System.getProperty(USERNAME_PROPERTY), RuntimeEnvironment.getEnv(USERNAME_ENV));
        String password = firstNonBlank(System.getProperty(PASSWORD_PROPERTY), RuntimeEnvironment.getEnv(PASSWORD_ENV));

        if (host == null || portValue == null || databaseName == null || username == null || password == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new ConnectionInfo(host, Integer.parseInt(portValue), databaseName, username, password));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<JdbcUrlParts> parseConfiguredJdbcUrl() {
        String jdbcUrl = firstNonBlank(System.getProperty(URL_PROPERTY), RuntimeEnvironment.getEnv(URL_ENV));
        return parseJdbcUrl(jdbcUrl);
    }

    private static Optional<JdbcUrlParts> parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            return Optional.empty();
        }

        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_POSTGRES_PORT;
        String path = uri.getPath();
        if (host == null || host.isBlank() || path == null || path.isBlank() || "/".equals(path)) {
            return Optional.empty();
        }
        return Optional.of(new JdbcUrlParts(host, port, path.substring(1)));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record ConnectionInfo(String host, int port, String databaseName, String username, String password) {
        public String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
        }
    }

    private record JdbcUrlParts(String host, int port, String databaseName) {}
}
