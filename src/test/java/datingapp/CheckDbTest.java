package datingapp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

public class CheckDbTest {

    @Test
    public void testCheckDb() throws Exception {
        System.out.println("========================================================================");
        try (Connection conn =
                DriverManager.getConnection("jdbc:postgresql://localhost:55432/datingapp", "datingapp", "datingapp")) {
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next()) {
                    System.out.println("TOTAL USERS IN POSTGRES DB: " + rs.getInt(1));
                }
            }
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, name, deleted_at FROM users LIMIT 5")) {
                while (rs.next()) {
                    System.out.println(
                            "User: " + rs.getString("name") + ", deleted_at=" + rs.getTimestamp("deleted_at"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("========================================================================");
    }
}
