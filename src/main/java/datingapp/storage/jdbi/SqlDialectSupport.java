package datingapp.storage.jdbi;

import datingapp.storage.DatabaseDialect;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import org.jdbi.v3.core.Jdbi;

/** Dialect-aware SQL helpers for JDBI storage upserts and expressions. */
public final class SqlDialectSupport {

    private SqlDialectSupport() {}

    public static String upsertSql(
            DatabaseDialect dialect, String tableName, List<ColumnBinding> columns, List<String> conflictColumns) {
        Objects.requireNonNull(dialect, "dialect cannot be null");
        requireNonBlank(tableName, "tableName");
        Objects.requireNonNull(columns, "columns cannot be null");
        Objects.requireNonNull(conflictColumns, "conflictColumns cannot be null");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns cannot be empty");
        }
        if (conflictColumns.isEmpty()) {
            throw new IllegalArgumentException("conflictColumns cannot be empty");
        }

        String columnList = joinColumns(columns);
        String valueList = joinValues(columns);
        String conflictList = joinIdentifiers(conflictColumns);
        List<String> updateAssignments = buildUpdateAssignments(columns, conflictColumns);

        return switch (dialect) {
            case H2 ->
                "MERGE INTO %s (%s) KEY (%s) VALUES (%s)".formatted(tableName, columnList, conflictList, valueList);
            case POSTGRESQL ->
                updateAssignments.isEmpty()
                        ? "INSERT INTO %s (%s)%nVALUES (%s)%nON CONFLICT (%s) DO NOTHING"
                                .formatted(tableName, columnList, valueList, conflictList)
                        : "INSERT INTO %s (%s)%nVALUES (%s)%nON CONFLICT (%s) DO UPDATE%nSET %s"
                                .formatted(
                                        tableName,
                                        columnList,
                                        valueList,
                                        conflictList,
                                        String.join(",\n    ", updateAssignments));
        };
    }

    public static String sessionDurationSecondsExpression(
            DatabaseDialect dialect, String startedAtColumn, String endedAtColumn) {
        Objects.requireNonNull(dialect, "dialect cannot be null");
        requireNonBlank(startedAtColumn, "startedAtColumn");
        requireNonBlank(endedAtColumn, "endedAtColumn");

        return switch (dialect) {
            case H2 -> "DATEDIFF('SECOND', %s, %s)".formatted(startedAtColumn, endedAtColumn);
            case POSTGRESQL -> "EXTRACT(EPOCH FROM (%s - %s))".formatted(endedAtColumn, startedAtColumn);
        };
    }

    public static DatabaseDialect detectDialect(Jdbi jdbi) {
        Objects.requireNonNull(jdbi, "jdbi cannot be null");
        return jdbi.withHandle(handle -> {
            try {
                return DatabaseDialect.fromDatabaseProductName(
                        handle.getConnection().getMetaData().getDatabaseProductName());
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to detect database dialect", exception);
            }
        });
    }

    public record ColumnBinding(String column, String binding) {}

    private static String joinColumns(List<ColumnBinding> columns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ColumnBinding column : columns) {
            requireNonBlank(column.column(), "column");
            joiner.add(column.column());
        }
        return joiner.toString();
    }

    private static String joinValues(List<ColumnBinding> columns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ColumnBinding column : columns) {
            requireNonBlank(column.binding(), "binding");
            joiner.add(":" + column.binding());
        }
        return joiner.toString();
    }

    private static String joinIdentifiers(List<String> identifiers) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String identifier : identifiers) {
            requireNonBlank(identifier, "identifier");
            joiner.add(identifier);
        }
        return joiner.toString();
    }

    private static List<String> buildUpdateAssignments(List<ColumnBinding> columns, List<String> conflictColumns) {
        List<String> updates = new ArrayList<>();
        for (ColumnBinding column : columns) {
            if (conflictColumns.contains(column.column())) {
                continue;
            }
            updates.add(column.column() + " = EXCLUDED." + column.column());
        }
        return updates;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }
}
