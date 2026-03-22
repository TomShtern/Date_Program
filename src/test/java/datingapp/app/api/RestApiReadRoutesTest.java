package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API direct read routes")
class RestApiReadRoutesTest {

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
    @DisplayName("list users, get user, get candidates, and get matches remain available")
    void listUsersGetUserGetCandidatesAndGetMatchesRemainAvailable() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User bob = activeUser(bobId, "Bob", Gender.MALE, EnumSet.of(Gender.FEMALE));
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> usersResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, usersResponse.statusCode());
        assertEquals(2, MAPPER.readTree(usersResponse.body()).size());

        HttpResponse<String> userResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, userResponse.statusCode());
        JsonNode userJson = MAPPER.readTree(userResponse.body());
        assertEquals("Alice", userJson.get("name").asText());

        HttpResponse<String> candidatesResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/candidates"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, candidatesResponse.statusCode());
        JsonNode candidatesJson = MAPPER.readTree(candidatesResponse.body());
        assertEquals(1, candidatesJson.size());
        assertEquals(bobId.toString(), candidatesJson.get(0).get("id").asText());

        HttpResponse<String> matchesResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/matches"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, matchesResponse.statusCode());
        JsonNode matchesJson = MAPPER.readTree(matchesResponse.body());
        assertEquals(1, matchesJson.get("matches").size());
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
                .candidateFinder(candidateFinder)
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

    private static User activeUser(UUID id, String name, Gender gender, EnumSet<Gender> interestedIn) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(gender)
                .interestedIn(interestedIn)
                .location(40.7128, -74.0060)
                .hasLocationSet(true)
                .maxDistanceKm(100)
                .photoUrls(List.of("https://example.com/" + name.toLowerCase() + ".jpg"))
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
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            // no-op
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            return List.of();
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            // no-op
        }

        @Override
        public int cleanup(LocalDate before) {
            return 0;
        }
    }
}
