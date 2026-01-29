package datingapp.module;

import datingapp.core.AppConfig;
import datingapp.storage.DatabaseManager;
import java.util.Objects;

/**
 * Application composition root. Creates and wires all modules together, providing a single entry
 * point for dependency injection.
 *
 * <p>Usage:
 *
 * <pre>
 * try (var app = AppContext.create(dbManager, config)) {
 *     app.validate();
 *     app.start();
 *     // Use app.matching(), app.messaging(), etc.
 * }
 * </pre>
 *
 * <p>For backward compatibility with ServiceRegistry, use:
 *
 * <pre>
 * ServiceRegistry services = ServiceRegistry.Builder.fromAppContext(app);
 * </pre>
 */
public record AppContext(
        AppConfig config,
        StorageModule storage,
        MatchingModule matching,
        MessagingModule messaging,
        SafetyModule safety,
        StatsModule stats)
        implements Module {

    public AppContext {
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(matching, "matching cannot be null");
        Objects.requireNonNull(messaging, "messaging cannot be null");
        Objects.requireNonNull(safety, "safety cannot be null");
        Objects.requireNonNull(stats, "stats cannot be null");
    }

    /**
     * Creates a fully configured AppContext with H2 database storage.
     *
     * @param dbManager The H2 database manager
     * @param config Application configuration
     * @return Fully wired AppContext
     */
    public static AppContext create(DatabaseManager dbManager, AppConfig config) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        StorageModule storage = StorageModule.forH2(dbManager);
        MatchingModule matching = MatchingModule.create(storage, config);
        MessagingModule messaging = MessagingModule.create(storage);
        SafetyModule safety = SafetyModule.create(storage, config);
        StatsModule stats = StatsModule.create(storage, config);

        return new AppContext(config, storage, matching, messaging, safety, stats);
    }

    /**
     * Creates a fully configured AppContext with default configuration.
     *
     * @param dbManager The H2 database manager
     * @return Fully wired AppContext with default config
     */
    public static AppContext createWithDefaults(DatabaseManager dbManager) {
        return create(dbManager, AppConfig.defaults());
    }

    @Override
    public void validate() {
        // Currently no validation needed - storage validation would go here
        // when migrating to JDBI (e.g., test DB connection)
    }

    @Override
    public void start() {
        // Start any background services if needed
    }

    @Override
    public void close() {
        // Close in reverse order of creation
        // Currently modules don't hold resources that need closing,
        // but this is the extension point for future cleanup
    }
}
