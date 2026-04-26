package datingapp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.support.LivePostgresqlTestConfig;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class CheckDbTest {

    @Test
    void testCheckDb() throws Exception {
        LivePostgresqlTestConfig.assumeConfigured();
        try (Connection conn = LivePostgresqlTestConfig.openConnection()) {
            LivePostgresqlTestConfig.initializeSchema(conn);
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next(), "Expected the COUNT(*) query to return a row");
                assertTrue(rs.getInt(1) >= 0, "User count should never be negative");
            }
        }
    }
}
