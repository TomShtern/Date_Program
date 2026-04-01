package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private static DatabaseManager dbManager;
    private static ServiceRegistry registry;

    @BeforeAll
    static void setUpOnce() {
        System.setProperty(PROFILE_PROPERTY, "test");
        // Use in-memory H2 database for testing
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:test_service_registry_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getInstance();
        registry = StorageFactory.buildH2(dbManager, AppConfig.defaults());
    }

    @AfterAll
    static void tearDownOnce() {
        System.clearProperty(PROFILE_PROPERTY);
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
        @DisplayName("Uses explicit builder default config")
        void usesExplicitBuilderDefaultConfig() {
            AppConfig defaultConfig = AppConfig.builder().build();
            ServiceRegistry defaultRegistry = StorageFactory.buildH2(dbManager, defaultConfig);

            assertNotNull(defaultRegistry.getConfig());
            assertSame(defaultConfig, defaultRegistry.getConfig());
        }

        @Test
        @DisplayName("buildH2(DatabaseManager) overload no longer exists")
        void buildH2OverloadRemovedWithoutAppConfig() {
            assertThrows(
                    NoSuchMethodException.class,
                    () -> StorageFactory.class.getMethod("buildH2", DatabaseManager.class));
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
    @DisplayName("Shared Graph Usability")
    class SharedGraphUsability {

        @Test
        @DisplayName("getProfileUseCases can read users saved through the registry storage")
        void getProfileUseCasesCanReadUsersSavedThroughRegistryStorage() {
            UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            User user = User.StorageBuilder.create(userId, "RegistryUser", AppClock.now())
                    .state(UserState.ACTIVE)
                    .birthDate(LocalDate.of(1998, 1, 1))
                    .gender(Gender.FEMALE)
                    .interestedIn(Set.of(Gender.MALE))
                    .location(32.0853, 34.7818)
                    .photoUrls(List.of("https://example.com/registry-user.jpg"))
                    .build();

            registry.getUserStorage().save(user);

            var result = registry.getProfileUseCases().listUsers();

            assertTrue(result.success());
            assertTrue(
                    result.data().stream().anyMatch(u -> u.getId().equals(userId)),
                    "Saved user should be present in listUsers result");
        }

        @Test
        @DisplayName("getLocationService can resolve a supported city")
        void getLocationServiceCanResolveASupportedCity() {
            var locationService = registry.getLocationService();

            assertTrue(locationService.findCountry("IL").isPresent());
            assertTrue(locationService.findCityByName("IL", "Tel Aviv").isPresent());
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
            assertNotNull(config.safety().userTimeZone());
            assertNotNull(config);
        }
    }

    @Nested
    @DisplayName("Foundation Getters")
    class FoundationGetters {

        @Test
        @DisplayName("getEventBus returns non-null")
        void getEventBus() {
            assertNotNull(registry.getEventBus());
        }
    }

    @Nested
    @DisplayName("In-Memory Wiring Regression (Task 5)")
    class InMemoryWiringRegression {

        @Test
        @DisplayName("buildInMemory returns usable ServiceRegistry with all core getters non-null")
        void buildInMemoryReturnsUsableRegistry() {
            // Arrange
            AppConfig config = AppConfig.defaults();

            // Act - builtin Memory should wrap buildH2 and return a valid registry
            ServiceRegistry inMemoryRegistry = StorageFactory.buildInMemory(config);

            // Assert - registry itself is non-null
            assertNotNull(inMemoryRegistry, "ServiceRegistry should not be null");

            // Storage getters are non-null
            assertNotNull(inMemoryRegistry.getUserStorage(), "getUserStorage must be non-null");
            assertNotNull(inMemoryRegistry.getInteractionStorage(), "getInteractionStorage must be non-null");
            assertNotNull(inMemoryRegistry.getCommunicationStorage(), "getCommunicationStorage must be non-null");
            assertNotNull(inMemoryRegistry.getAnalyticsStorage(), "getAnalyticsStorage must be non-null");

            // Core services are non-null
            assertNotNull(inMemoryRegistry.getProfileService(), "getProfileService must be non-null");
            assertNotNull(inMemoryRegistry.getMatchingService(), "getMatchingService must be non-null");
            assertNotNull(inMemoryRegistry.getCandidateFinder(), "getCandidateFinder must be non-null");

            // Use-cases are non-null
            assertNotNull(inMemoryRegistry.getProfileUseCases(), "getProfileUseCases must be non-null");
            assertNotNull(inMemoryRegistry.getMatchingUseCases(), "getMatchingUseCases must be non-null");
            assertNotNull(inMemoryRegistry.getMessagingUseCases(), "getMessagingUseCases must be non-null");
            assertNotNull(inMemoryRegistry.getSocialUseCases(), "getSocialUseCases must be non-null");

            assertNotNull(inMemoryRegistry.getConfig(), "getConfig must be non-null");
        }
    }
}
