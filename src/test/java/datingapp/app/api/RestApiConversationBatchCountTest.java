package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import datingapp.core.time.DefaultTimePolicy;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API conversation batch counts")
class RestApiConversationBatchCountTest {

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
    @DisplayName("conversations endpoint uses batch count path instead of per-conversation counting")
    void conversationsEndpointUsesBatchCountPath() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        SpyCommunications communicationStorage = new SpyCommunications();
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();

        userStorage.save(activeUser(userA, "Alice"));
        userStorage.save(activeUser(userB, "Bob"));
        userStorage.save(activeUser(userC, "Cara"));

        interactionStorage.save(Match.create(userA, userB));
        interactionStorage.save(Match.create(userA, userC));

        ConnectionService connectionService = services.getConnectionService();
        connectionService.sendMessage(userA, userB, "AB-1");
        connectionService.sendMessage(userA, userC, "AC-1");

        server = new RestApiServer(services, 0);
        server.start();

        int port = server.getApp().port();
        URI uri = URI.create("http://localhost:" + port + "/api/users/" + userA + "/conversations");

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        List<?> conversations = MAPPER.readValue(response.body(), List.class);
        assertEquals(2, conversations.size());
        assertEquals(1, communicationStorage.batchCountCalls());
        assertEquals(0, communicationStorage.singleCountCalls());
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
        RecommendationService recommendationService = RecommendationService.builder()
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(new InMemoryStandoutStorage())
                .profileService(profileService)
                .config(config)
                .build();
        UndoService undoService = new UndoService(interactionStorage, new InMemoryUndoStorage(), config);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .activityMetricsService(activityMetricsService)
                .dailyService(recommendationService)
                .undoService(undoService)
                .build();
        TrustSafetyService trustSafetyService = new TrustSafetyService(
                trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage);
        ConnectionService connectionService = new ConnectionService(
                config, communicationStorage, interactionStorage, userStorage, activityMetricsService);
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, interactionStorage, config);
        ValidationService validationService = new ValidationService(config);

        return new ServiceRegistry(
                config,
                userStorage,
                interactionStorage,
                communicationStorage,
                analyticsStorage,
                trustSafetyStorage,
                candidateFinder,
                matchingService,
                trustSafetyService,
                activityMetricsService,
                matchQualityService,
                profileService,
                recommendationService,
                undoService,
                connectionService,
                validationService,
                new DefaultTimePolicy(ZoneId.of("UTC")),
                new InProcessAppEventBus());
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
    }

    private static final class SpyCommunications extends TestStorages.Communications {
        private int singleCountCalls;
        private int batchCountCalls;
        private boolean countingWithinBatch;

        @Override
        public int countMessages(String conversationId) {
            if (!countingWithinBatch) {
                singleCountCalls++;
            }
            return super.countMessages(conversationId);
        }

        @Override
        public Map<String, Integer> countMessagesByConversationIds(Set<String> conversationIds) {
            batchCountCalls++;
            countingWithinBatch = true;
            try {
                return super.countMessagesByConversationIds(conversationIds);
            } finally {
                countingWithinBatch = false;
            }
        }

        private int singleCountCalls() {
            return singleCountCalls;
        }

        private int batchCountCalls() {
            return batchCountCalls;
        }
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
