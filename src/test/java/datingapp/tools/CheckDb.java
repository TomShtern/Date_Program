package datingapp.tools;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.AppConfig;
import datingapp.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDb {
    public static void main(String[] args) {
        AppConfig config = ApplicationStartup.load();
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.configureStorage(config.storage());
        try (Connection conn = dbManager.getConnection()) {
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next()) {
                    System.out.println("TOTAL USERS IN DB: " + rs.getInt(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseManager.clearInstance();
        }
    }
}
