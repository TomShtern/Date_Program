package datingapp.app;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;

/**
 * Centralized application initialization for both CLI and JavaFX entry points.
 *
 * <p>Eliminates duplicated initialization code between Main.java and DatingApp.java
 * by providing a single initialization method that sets up the database and service registry.
 *
 * <p>Thread-safe singleton pattern ensures only one initialization occurs.
 */
public final class AppBootstrap {
    private static volatile ServiceRegistry services;
    private static volatile DatabaseManager dbManager;
    private static volatile boolean initialized = false;

    private AppBootstrap() {}

    /**
     * Initializes the application with default configuration.
     * Loads config from ./config/app-config.json if present, otherwise uses defaults.
     *
     * @return The initialized ServiceRegistry
     */
    public static synchronized ServiceRegistry initialize() {
        return initialize(ConfigLoader.load());
    }

    /**
     * Initializes the application with custom configuration.
     *
     * @param config The application configuration
     * @return The initialized ServiceRegistry
     */
    public static synchronized ServiceRegistry initialize(AppConfig config) {
        if (!initialized) {
            dbManager = DatabaseManager.getInstance();
            services = StorageFactory.buildH2(dbManager, config);
            initialized = true;
        }
        return services;
    }

    /**
     * Gets the current ServiceRegistry.
     *
     * @return The ServiceRegistry
     * @throws IllegalStateException if initialize() has not been called
     */
    public static ServiceRegistry getServices() {
        ServiceRegistry current = services;
        if (!initialized || current == null) {
            throw new IllegalStateException("AppBootstrap.initialize() must be called first");
        }
        return current;
    }

    /**
     * Shuts down the application, closing database connections.
     */
    public static synchronized void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        initialized = false;
        services = null;
        dbManager = null;
    }

    /**
     * Resets both AppBootstrap and AppSession state. Used primarily for testing.
     */
    public static synchronized void reset() {
        shutdown();
        AppSession.getInstance().reset();
    }
}
