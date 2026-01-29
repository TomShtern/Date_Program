package datingapp.storage.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Utility methods for JDBI row mappers.
 * Provides null-safe reading and type conversion from ResultSet.
 */
public final class MapperHelper {

    private MapperHelper() {} // Utility class

    /**
     * Reads a UUID from a ResultSet column.
     * Returns null if column is NULL.
     */
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
     */
    public static Instant readInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    /**
     * Reads a LocalDate from a DATE column.
     * Returns null if column is NULL.
     */
    public static LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    /**
     * Reads an enum from a VARCHAR column.
     * Returns null if column is NULL.
     */
    public static <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    /**
     * Reads an integer from a column, handling NULL.
     * Returns null if column is NULL (use wasNull() pattern).
     */
    public static Integer readInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a double from a column, handling NULL.
     * Returns null if column is NULL (use wasNull() pattern).
     */
    public static Double readDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Reads a comma-separated string as a List.
     * Returns empty list if column is NULL or empty.
     */
    public static List<String> readCsvAsList(ResultSet rs, String column) throws SQLException {
        String csv = rs.getString(column);
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(csv.split(","));
    }

    /**
     * Alias for readInstant() - reads nullable Instant from TIMESTAMP column.
     * Returns null if column is NULL.
     */
    public static Instant readInstantNullable(ResultSet rs, String column) throws SQLException {
        return readInstant(rs, column);
    }

    /**
     * Alias for readInstant() - reads optional Instant from TIMESTAMP column.
     * Returns null if column is NULL.
     */
    public static Instant readInstantOptional(ResultSet rs, String column) throws SQLException {
        return readInstant(rs, column);
    }
}
