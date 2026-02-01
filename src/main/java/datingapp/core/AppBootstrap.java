package datingapp.core;

import datingapp.storage.DatabaseManager;

/**
 * Centralized application initialization for both CLI and JavaFX entry points.
 *
 * <p>Eliminates duplicated initialization code between Main.java and DatingApp.java by providing a
 * single initialization method that sets up the database and service registry.
 *
 * <p>Thread-safe singleton pattern ensures only one initialization occurs.
 */
public final class AppBootstrap {
    private static ServiceRegistry services;
    private static DatabaseManager dbManager;
    private static boolean initialized = false;

    private AppBootstrap() {}

    /**
     * Initializes the application with default configuration.
     *
     * @return The initialized ServiceRegistry
     */
    public static synchronized ServiceRegistry initialize() {
        return initialize(AppConfig.defaults());
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
            services = ServiceRegistry.Builder.buildH2(dbManager, config);
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
        if (!initialized) {
            throw new IllegalStateException("AppBootstrap.initialize() must be called first");
        }
        return services;
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
