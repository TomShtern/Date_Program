package datingapp.core.testutil;

import datingapp.core.AppConfig;
import datingapp.core.metrics.ActivityMetricsService;

/**
 * No-op test stub for {@link ActivityMetricsService}.
 *
 * <p>Wraps a real {@code ActivityMetricsService} backed by empty test storages
 * so that callers can safely invoke any method without null checks.
 */
public final class TestActivityMetricsService {

    private final ActivityMetricsService delegate;

    private TestActivityMetricsService(ActivityMetricsService delegate) {
        this.delegate = delegate;
    }

    /** Returns a no-op instance backed by empty in-memory test storages. */
    public static ActivityMetricsService empty() {
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        ActivityMetricsService delegate =
                new ActivityMetricsService(interactions, trustSafety, analytics, AppConfig.defaults());
        return new TestActivityMetricsService(delegate).delegate;
    }

    /**
     * Returns a no-op instance backed by the supplied test storages and config.
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
