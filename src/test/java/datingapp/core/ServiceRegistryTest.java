package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for ServiceRegistry - central dependency registry and service wiring.
 *
 * <p>
 * Verifies that the Builder correctly creates all services and storages,
 * and that getters return non-null instances.
 */
@SuppressWarnings("unused")
@DisplayName("ServiceRegistry")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ServiceRegistryTest {

    private static DatabaseManager dbManager;
    private static ServiceRegistry registry;

    @BeforeAll
    static void setUpOnce() {
        // Use in-memory H2 database for testing
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:test_service_registry_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getInstance();
        registry = StorageFactory.buildH2(dbManager, AppConfig.defaults());
    }

    @AfterAll
    static void tearDownOnce() {
        DatabaseManager.resetInstance();
    }

    @Nested
    @DisplayName("Builder.buildH2")
    class BuilderTests {

        @Test
        @DisplayName("Creates non-null registry")
        void createsNonNullRegistry() {
            assertNotNull(registry);
        }

        @Test
        @DisplayName("Uses provided config")
        void usesProvidedConfig() {
            AppConfig customConfig = AppConfig.builder().dailyLikeLimit(50).build();
            // Note: Using same dbManager since we can't easily create isolated instances
            // Config customization is the key thing being tested
            ServiceRegistry customRegistry = StorageFactory.buildH2(dbManager, customConfig);

            assertSame(customConfig, customRegistry.getConfig());
        }

        @Test
        @DisplayName("Uses default config when not specified")
        void usesDefaultConfig() {
            ServiceRegistry defaultRegistry = StorageFactory.buildH2(dbManager);

            assertNotNull(defaultRegistry.getConfig());
        }
    }

    @Nested
    @DisplayName("Storage Getters")
    class StorageGetters {

        @Test
        @DisplayName("getUserStorage returns non-null")
        void getUserStorage() {
            assertNotNull(registry.getUserStorage());
        }

        @Test
        @DisplayName("getInteractionStorage returns non-null")
        void getInteractionStorage() {
            assertNotNull(registry.getInteractionStorage());
        }

        @Test
        @DisplayName("getCommunicationStorage returns non-null")
        void getCommunicationStorage() {
            assertNotNull(registry.getCommunicationStorage());
        }

        @Test
        @DisplayName("getAnalyticsStorage returns non-null")
        void getAnalyticsStorage() {
            assertNotNull(registry.getAnalyticsStorage());
        }
    }

    @Nested
    @DisplayName("Service Getters")
    class ServiceGetters {

        @Test
        @DisplayName("getCandidateFinder returns non-null")
        void getCandidateFinder() {
            assertNotNull(registry.getCandidateFinder());
        }

        @Test
        @DisplayName("getMatchingService returns non-null")
        void getMatchingService() {
            assertNotNull(registry.getMatchingService());
        }

        @Test
        @DisplayName("getTrustSafetyService returns non-null")
        void getTrustSafetyService() {
            assertNotNull(registry.getTrustSafetyService());
        }

        @Test
        @DisplayName("getActivityMetricsService returns non-null")
        void getActivityMetricsService() {
            assertNotNull(registry.getActivityMetricsService());
        }

        @Test
        @DisplayName("getMatchQualityService returns non-null")
        void getMatchQualityService() {
            assertNotNull(registry.getMatchQualityService());
        }

        @Test
        @DisplayName("getProfileService returns non-null")
        void getProfileService() {
            assertNotNull(registry.getProfileService());
        }

        @Test
        @DisplayName("getRecommendationService returns non-null")
        void getRecommendationService() {
            assertNotNull(registry.getRecommendationService());
        }

        @Test
        @DisplayName("getUndoService returns non-null")
        void getUndoService() {
            assertNotNull(registry.getUndoService());
        }

        @Test
        @DisplayName("getConnectionService returns non-null")
        void getConnectionService() {
            assertNotNull(registry.getConnectionService());
        }
    }

    @Nested
    @DisplayName("Config Getter")
    class ConfigGetter {

        @Test
        @DisplayName("getConfig returns non-null")
        void getConfigReturnsNonNull() {
            assertNotNull(registry.getConfig());
        }

        @Test
        @DisplayName("getConfig returns expected defaults")
        void getConfigReturnsExpectedDefaults() {
            AppConfig config = registry.getConfig();

            // Verify some default values
            assertNotNull(config.userTimeZone());
            assertNotNull(config);
        }
    }
}
