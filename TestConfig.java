import datingapp.core.AppConfig;
import datingapp.app.bootstrap.ApplicationStartup;
import java.nio.file.Path;

public class TestConfig {
    public static void main(String[] args) throws Exception {
        System.setProperty("DATING_APP_CONFIG_PATH", "config/app-config.json");
        AppConfig config = ApplicationStartup.loadConfig();
        System.out.println("Config database URL: " + config.storage().databaseUrl());
        System.out.println("Config dialect: " + config.storage().databaseDialect());
        
        System.out.println("DatabaseManager config URL: " + datingapp.storage.DatabaseManager.getInstance().getConfiguredJdbcUrl());
        
        System.out.println("Current directory: " + Path.of(".").toAbsolutePath());
    }
}
