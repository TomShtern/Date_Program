package datingapp.tools;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.AppConfig;
import java.nio.file.Path;

public class TestConfig {
    public static void main(String[] args) throws Exception {
        AppConfig config = ApplicationStartup.load();
        System.out.println("Config database URL: " + config.storage().databaseUrl());
        System.out.println("Config dialect: " + config.storage().databaseDialect());
        System.out.println("Config username: " + config.storage().databaseUsername());
        System.out.println("Current directory: " + Path.of(".").toAbsolutePath());
    }
}
