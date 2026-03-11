package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.DefaultCompatibilityCalculator;
import datingapp.core.matching.DefaultDailyLimitService;
import datingapp.core.matching.DefaultDailyPickService;
import datingapp.core.matching.DefaultStandoutService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API profile-note routes")
class RestApiNotesRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("note endpoints support put get list and delete")
    void noteEndpointsSupportPutGetListAndDelete() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID authorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        userStorage.save(activeUser(authorId, "Author"));
        userStorage.save(activeUser(subjectId, "Subject"));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        HttpClient client = HttpClient.newHttpClient();
        String baseUri = "http://localhost:" + port + "/api/users/" + authorId + "/notes/" + subjectId;

        HttpResponse<String> putResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri))
                        .PUT(HttpRequest.BodyPublishers.ofString("{\"content\":\"Met at coffee shop\"}"))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putResponse.statusCode());
        JsonNode putJson = MAPPER.readTree(putResponse.body());
        assertEquals("Met at coffee shop", putJson.get("content").asText());

        HttpResponse<String> getResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        JsonNode getJson = MAPPER.readTree(getResponse.body());
        assertEquals(subjectId.toString(), getJson.get("subjectId").asText());

        HttpResponse<String> listResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + authorId + "/notes"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        JsonNode listJson = MAPPER.readTree(listResponse.body());
        assertEquals(1, listJson.size());
        assertTrue(listJson.get(0).get("content").asText().contains("coffee shop"));

        HttpResponse<String> deleteResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri)).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteResponse.statusCode());

        HttpResponse<String> missingResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUri)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missingResponse.statusCode());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        AppConfig config = AppConfig.defaults();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();

        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, ZoneId.of("UTC"));
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactionStorage, config);
        DailyPickService dailyPickService =
                new DefaultDailyPickService(userStorage, interactionStorage, analyticsStorage, candidateFinder, config);
        StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator,
                userStorage,
                candidateFinder,
                new InMemoryStandoutStorage(),
                profileService,
                config);
        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactionStorage, new InMemoryUndoStorage(), config);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .activityMetricsService(activityMetricsService)
                .dailyService(recommendationService)
                .undoService(undoService)
                .build();
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage)
                .build();
        ConnectionService connectionService =
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
        MatchQualityService matchQualityService =
                new MatchQualityService(userStorage, interactionStorage, config, compatibilityCalculator);
        ValidationService validationService = new ValidationService(config);
        AchievementService achievementService = new DefaultAchievementService(
                config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, profileService);

        return ServiceRegistry.builder()
                .config(config)
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .communicationStorage(communicationStorage)
                .analyticsStorage(analyticsStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .candidateFinder(candidateFinder)
                .matchingService(matchingService)
                .trustSafetyService(trustSafetyService)
                .activityMetricsService(activityMetricsService)
                .matchQualityService(matchQualityService)
                .profileService(profileService)
                .recommendationService(recommendationService)
                .dailyLimitService(dailyLimitService)
                .dailyPickService(dailyPickService)
                .standoutService(standoutService)
                .undoService(undoService)
                .compatibilityCalculator(compatibilityCalculator)
                .achievementService(achievementService)
                .connectionService(connectionService)
                .validationService(validationService)
                .eventBus(new InProcessAppEventBus())
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }

    private static final class InMemoryUndoStorage implements Undo.Storage {
        private final Map<UUID, Undo> byUserId = new HashMap<>();

        @Override
        public void save(Undo state) {
            byUserId.put(state.userId(), state);
        }

        @Override
        public Optional<Undo> findByUserId(UUID userId) {
            return Optional.ofNullable(byUserId.get(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return byUserId.remove(userId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            List<UUID> toDelete = new ArrayList<>();
            for (Undo undo : byUserId.values()) {
                if (undo.isExpired(now)) {
                    toDelete.add(undo.userId());
                }
            }
            toDelete.forEach(byUserId::remove);
            return toDelete.size();
        }

        @Override
        public List<Undo> findAll() {
            return List.copyOf(byUserId.values());
        }
    }

    private static final class InMemoryStandoutStorage implements Standout.Storage {
        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, java.time.LocalDate date) {
            // no-op for this integration test
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, java.time.LocalDate date) {
            return List.of();
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, java.time.LocalDate date) {
            // no-op for this integration test
        }

        @Override
        public int cleanup(java.time.LocalDate before) {
            return 0;
        }
    }
}
