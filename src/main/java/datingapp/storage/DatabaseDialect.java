package datingapp.storage;

import java.util.Locale;

/** Supported runtime database dialects. */
public enum DatabaseDialect {
    H2,
    POSTGRESQL;

    public static DatabaseDialect fromConfig(String configuredDialect, String jdbcUrl) {
        if (configuredDialect != null && !configuredDialect.isBlank()) {
            return DatabaseDialect.valueOf(configuredDialect.trim().toUpperCase(Locale.ROOT));
        }
        return fromJdbcUrl(jdbcUrl);
    }

    public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl cannot be blank");
        }

        String normalizedJdbcUrl = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (normalizedJdbcUrl.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        }
        if (normalizedJdbcUrl.startsWith("jdbc:h2:")) {
            return H2;
        }

        throw new IllegalArgumentException("Unsupported JDBC URL for dialect detection: " + jdbcUrl);
    }

    public static DatabaseDialect fromDatabaseProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName cannot be blank");
        }

        String normalizedProductName = productName.trim().toLowerCase(Locale.ROOT);
        if ("postgresql".equals(normalizedProductName)) {
            return POSTGRESQL;
        }
        if ("h2".equals(normalizedProductName)) {
            return H2;
        }

        throw new IllegalArgumentException("Unsupported database product name: " + productName);
    }
}
