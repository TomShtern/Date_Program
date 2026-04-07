package datingapp.core.testutil;

import datingapp.core.AppConfig;
import datingapp.core.metrics.ActivityMetricsService;

/**
 * Static test utility for creating {@link ActivityMetricsService} instances.
 *
 * <p>Builds real {@code ActivityMetricsService} instances backed by test storages
 * so that callers can safely invoke methods without null checks.
 */
public final class TestActivityMetricsService {

    private TestActivityMetricsService() {}

    /** Returns an instance backed by empty in-memory test storages. */
    public static ActivityMetricsService empty() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        return new ActivityMetricsService(interactions, trustSafety, analytics, AppConfig.defaults());
    }

    /**
     * Returns an instance backed by the supplied test storages and config.
     * Useful when the caller wants a single shared set of storages across tests.
     */
    public static ActivityMetricsService withStorages(
            TestStorages.Interactions interactions,
            TestStorages.TrustSafety trustSafety,
            TestStorages.Analytics analytics,
            AppConfig config) {
        return new ActivityMetricsService(interactions, trustSafety, analytics, config);
    }
}
