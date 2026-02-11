package datingapp.storage.jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for JDBI row mappers.
 * Provides null-safe reading and type conversion from ResultSet.
 */
public final class MapperHelper {

    private static final Logger logger = LoggerFactory.getLogger(MapperHelper.class);

    private MapperHelper() {} // Utility class

    /**
     * Reads a UUID from a ResultSet column.
     * Returns null if column is NULL.
     */
    @Nullable
    public static UUID readUuid(ResultSet rs, String column) throws SQLException {
        Object obj = rs.getObject(column);
        if (obj == null) {
            return null;
        }
        if (obj instanceof UUID uuid) {
            return uuid;
        }
        // H2 might return UUID as String or byte array
        if (obj instanceof String str) {
            return UUID.fromString(str);
        }
        throw new SQLException("Unexpected UUID type: " + obj.getClass());
    }

    /**
     * Reads an Instant from a TIMESTAMP column.
     * Returns null if column is NULL.
     *
     * <p>Precision: Uses {@code Timestamp.toInstant()} which preserves nanosecond
     * precision from the JDBC driver.</p>
     */
    @Nullable
    public static Instant readInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    /**
     * Reads a LocalDate from a DATE column.
     * Returns null if column is NULL.
     */
    @Nullable
    public static LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    /**
     * Reads an enum from a VARCHAR column.
     * Returns null if column is NULL or if the value is invalid.
     *
     * @throws NullPointerException if enumType is null
     */
    @Nullable
    public static <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
        Objects.requireNonNull(enumType, "enumType cannot be null");
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Skipping invalid {} value '{}' in column '{}'", enumType.getSimpleName(), value, column);
            }
            return null;
        }
    }

    /**
     * Reads an integer from a column, handling NULL.
     * Returns null if column is NULL (use wasNull() pattern).
     */
    @Nullable
    public static Integer readInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a double from a column, handling NULL.
     * Returns null if column is NULL (use wasNull() pattern).
     */
    @Nullable
    public static Double readDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a comma-separated string as an unmodifiable List.
     * Returns empty list if column is NULL or blank.
     */
    public static List<String> readCsvAsList(ResultSet rs, String column) throws SQLException {
        String csv = rs.getString(column);
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
