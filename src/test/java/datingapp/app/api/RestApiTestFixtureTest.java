package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.testutil.TestStorages;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RestApiTestFixture")
class RestApiTestFixtureTest {

    @Test
    @DisplayName("builder uses the configured timezone for CandidateFinder")
    void builderUsesConfiguredTimezoneForCandidateFinder() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(communications);
        AppConfig config =
                AppConfig.builder().userTimeZone(ZoneId.of("Asia/Tokyo")).build();

        ServiceRegistry services = RestApiTestFixture.builder(users, interactions, communications)
                .config(config)
                .build();

        assertEquals(
                config.safety().userTimeZone(), services.getCandidateFinder().getTimezone());
    }
}
