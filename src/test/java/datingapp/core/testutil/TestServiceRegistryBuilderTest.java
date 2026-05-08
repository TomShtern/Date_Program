package datingapp.core.testutil;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.matching.BrowseRankingService;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.profile.ProfileService;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TestServiceRegistryBuilder")
class TestServiceRegistryBuilderTest {

    @Test
    @DisplayName("builder(userStorage, interactionStorage, communicationStorage) supports the RestApiTestFixture seam")
    void builderSupportsTheRestApiTestFixtureSeam() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(communications);
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Standouts standouts = new TestStorages.Standouts();
        TestStorages.Undos undos = new TestStorages.Undos();
        AppConfig config =
                AppConfig.builder().userTimeZone(ZoneId.of("Asia/Tokyo")).build();
        BrowseRankingService browseRankingService =
                new BrowseRankingService(new CompatibilityCalculator(config), new ProfileService(users), config);

        ServiceRegistry services = TestServiceRegistryBuilder.builder(users, interactions, communications)
                .config(config)
                .analyticsStorage(analytics)
                .trustSafetyStorage(trustSafety)
                .standoutStorage(standouts)
                .undoStorage(undos)
                .browseRankingService(browseRankingService)
                .build();

        assertAll(
                () -> assertEquals(
                        config.safety().userTimeZone(),
                        services.getCandidateFinder().getTimezone()),
                () -> assertNotNull(services.getRecommendationService()));
    }
}
