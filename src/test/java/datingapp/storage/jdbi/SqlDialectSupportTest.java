package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.storage.DatabaseDialect;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SqlDialectSupport")
class SqlDialectSupportTest {

    @Test
    @DisplayName("upsertSql should emit H2 MERGE syntax")
    void upsertSqlEmitsH2MergeSyntax() {
        String sql = SqlDialectSupport.upsertSql(
                DatabaseDialect.H2,
                "users",
                List.of(
                        new SqlDialectSupport.ColumnBinding("id", "id"),
                        new SqlDialectSupport.ColumnBinding("name", "name"),
                        new SqlDialectSupport.ColumnBinding("updated_at", "updatedAt")),
                List.of("id"));

        assertEquals(
                normalize("MERGE INTO users (id, name, updated_at) KEY (id) VALUES (:id, :name, :updatedAt)"),
                normalize(sql));
    }

    @Test
    @DisplayName("upsertSql should emit PostgreSQL ON CONFLICT syntax")
    void upsertSqlEmitsPostgresqlOnConflictSyntax() {
        String sql = SqlDialectSupport.upsertSql(
                DatabaseDialect.POSTGRESQL,
                "users",
                List.of(
                        new SqlDialectSupport.ColumnBinding("id", "id"),
                        new SqlDialectSupport.ColumnBinding("name", "name"),
                        new SqlDialectSupport.ColumnBinding("updated_at", "updatedAt")),
                List.of("id"));

        assertEquals(normalize("""
                        INSERT INTO users (id, name, updated_at)
                        VALUES (:id, :name, :updatedAt)
                        ON CONFLICT (id) DO UPDATE
                        SET name = EXCLUDED.name,
                            updated_at = EXCLUDED.updated_at
                        """), normalize(sql));
    }

    @Test
    @DisplayName("sessionDurationSecondsExpression should emit dialect-specific duration SQL")
    void sessionDurationSecondsExpressionEmitsDialectSpecificSql() {
        assertEquals(
                "DATEDIFF('SECOND', started_at, ended_at)",
                SqlDialectSupport.sessionDurationSecondsExpression(DatabaseDialect.H2, "started_at", "ended_at"));
        assertEquals(
                "EXTRACT(EPOCH FROM (ended_at - started_at))",
                SqlDialectSupport.sessionDurationSecondsExpression(
                        DatabaseDialect.POSTGRESQL, "started_at", "ended_at"));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
