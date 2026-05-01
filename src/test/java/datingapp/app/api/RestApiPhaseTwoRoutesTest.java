package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.matching.Standout;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.storage.OperationalCommunicationStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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

@DisplayName("REST API Phase 2 routes")
class RestApiPhaseTwoRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("notification, stats, and achievement routes support list/read/read-all")
    void notificationStatsAndAchievementRoutesSupportListReadAndReadAll() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        Notification firstNotification = Notification.create(
                aliceId,
                Notification.Type.NEW_MESSAGE,
                "New Message",
                "Bob sent a message.",
                Map.of(
                        "conversationId", Match.generateId(aliceId, bobId),
                        "senderId", bobId.toString(),
                        "messageId", UUID.randomUUID().toString()));
        Notification secondNotification = Notification.create(
                aliceId,
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You matched with Bob.",
                Map.of(
                        "matchId", Match.generateId(aliceId, bobId),
                        "conversationId", Match.generateId(aliceId, bobId),
                        "otherUserId", bobId.toString()));
        communicationStorage.saveNotification(firstNotification);
        communicationStorage.saveNotification(secondNotification);
        services.getAchievementService().checkAndUnlock(aliceId);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> notificationsResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/notifications"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, notificationsResponse.statusCode());
        JsonNode notificationsJson = MAPPER.readTree(notificationsResponse.body());
        assertEquals(2, notificationsJson.size());
        for (JsonNode notificationJson : notificationsJson) {
            JsonNode data = notificationJson.get("data");
            assertTrue(data.isObject());
            if ("NEW_MESSAGE".equals(notificationJson.get("type").asText())) {
                assertFalse(data.get("conversationId").asText().isBlank());
                assertFalse(data.get("senderId").asText().isBlank());
                assertFalse(data.get("messageId").asText().isBlank());
            }
            if ("MATCH_FOUND".equals(notificationJson.get("type").asText())) {
                assertEquals(
                        data.get("matchId").asText(), data.get("conversationId").asText());
                assertFalse(data.get("otherUserId").asText().isBlank());
            }
        }

        HttpResponse<String> unreadOnlyResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/notifications?unreadOnly=true"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, unreadOnlyResponse.statusCode());
        assertEquals(2, MAPPER.readTree(unreadOnlyResponse.body()).size());

        HttpResponse<String> readSingleResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/notifications/" + firstNotification.id() + "/read"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, readSingleResponse.statusCode());

        HttpResponse<String> forbiddenReadResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + bobId + "/notifications/"
                                + secondNotification.id() + "/read"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, bobId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenReadResponse.statusCode());

        HttpResponse<String> readAllResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/users/" + aliceId + "/notifications/read-all"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, readAllResponse.statusCode());
        JsonNode readAllJson = MAPPER.readTree(readAllResponse.body());
        assertEquals(1, readAllJson.get("updatedCount").asInt());

        HttpResponse<String> statsResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/stats"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, statsResponse.statusCode());
        JsonNode statsJson = MAPPER.readTree(statsResponse.body());
        assertEquals(aliceId.toString(), statsJson.get("userId").asText());

        HttpResponse<String> achievementsResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/achievements?checkForNew=true"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, achievementsResponse.statusCode());
        JsonNode achievementsJson = MAPPER.readTree(achievementsResponse.body());
        assertTrue(achievementsJson.get("unlockedCount").asInt() >= 1);
    }

    @Test
    @DisplayName("matching support routes expose pending likers, standouts, quality, and undo")
    void matchingSupportRoutesExposePendingLikersStandoutsQualityAndUndo() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID danaId = UUID.randomUUID();
        UUID eveId = UUID.randomUUID();

        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        User carol = activeUser(carolId, "Carol");
        User dana = activeUser(danaId, "Dana");
        User eve = activeUser(eveId, "Eve");
        userStorage.save(alice);
        userStorage.save(bob);
        userStorage.save(carol);
        userStorage.save(dana);
        userStorage.save(eve);

        Match aliceBobMatch = Match.create(aliceId, bobId);
        interactionStorage.save(aliceBobMatch);
        interactionStorage.save(Like.create(carolId, aliceId, Like.Direction.LIKE));

        standoutStorage.saveStandouts(
                aliceId,
                List.of(Standout.create(aliceId, danaId, LocalDate.now(), 1, 97, "Shared interests and strong fit")),
                LocalDate.now());

        services.getMatchingUseCases()
                .processSwipe(new ProcessSwipeCommand(UserContext.api(aliceId), alice, eve, true, false, false));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> pendingLikersResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/pending-likers"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pendingLikersResponse.statusCode());
        JsonNode pendingLikersJson = MAPPER.readTree(pendingLikersResponse.body());
        assertEquals(1, pendingLikersJson.get("pendingLikers").size());
        JsonNode pendingLikerJson = pendingLikersJson.get("pendingLikers").get(0);
        assertEquals(carolId.toString(), pendingLikerJson.get("userId").asText());
        assertEquals(
                "https://example.com/carol.jpg",
                pendingLikerJson.get("primaryPhotoUrl").asText());
        assertTrue(pendingLikerJson.get("photoUrls").isArray());
        assertFalse(pendingLikerJson.get("summaryLine").isNull());

        HttpResponse<String> standoutsResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/standouts"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, standoutsResponse.statusCode());
        JsonNode standoutsJson = MAPPER.readTree(standoutsResponse.body());
        assertEquals(1, standoutsJson.get("standouts").size());
        JsonNode standoutJson = standoutsJson.get("standouts").get(0);
        assertEquals(danaId.toString(), standoutJson.get("standoutUserId").asText());
        assertEquals(
                "https://example.com/dana.jpg",
                standoutJson.get("primaryPhotoUrl").asText());
        assertTrue(standoutJson.get("photoUrls").isArray());
        assertFalse(standoutJson.get("approximateLocation").isNull());

        HttpResponse<String> matchQualityResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/match-quality/" + aliceBobMatch.getId()))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, matchQualityResponse.statusCode());
        JsonNode matchQualityJson = MAPPER.readTree(matchQualityResponse.body());
        assertEquals(aliceBobMatch.getId(), matchQualityJson.get("matchId").asText());

        HttpResponse<String> undoResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/undo"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, undoResponse.statusCode());
        JsonNode undoJson = MAPPER.readTree(undoResponse.body());
        assertTrue(undoJson.get("success").asBoolean());
    }

    @Test
    @DisplayName("profile edit snapshot mirrors editable profile state and read-only identity")
    void profileEditSnapshotMirrorsEditableProfileStateAndReadOnlyIdentity() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice Snapshot");
        alice.setBio("Runner, coffee person, and weekend hiker.");
        alice.setGender(Gender.FEMALE);
        alice.setInterestedIn(EnumSet.of(Gender.MALE));
        alice.setLocation(32.0853, 34.7818);
        alice.setMaxDistanceKm(25, 500);
        alice.setAgeRange(27, 38, 18, 100);
        alice.setHeightCm(168);
        alice.setSmoking(Lifestyle.Smoking.NEVER);
        alice.setDrinking(Lifestyle.Drinking.SOCIALLY);
        alice.setWantsKids(Lifestyle.WantsKids.OPEN);
        alice.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
        alice.setEducation(Lifestyle.Education.BACHELORS);
        alice.setInterests(EnumSet.of(Interest.COFFEE, Interest.HIKING, Interest.TRAVEL));
        alice.setDealbreakers(Dealbreakers.builder()
                .acceptSmoking(Lifestyle.Smoking.NEVER)
                .acceptLookingFor(Lifestyle.LookingFor.LONG_TERM, Lifestyle.LookingFor.MARRIAGE)
                .maxAgeDifference(6)
                .build());
        alice.setPhotoUrls(List.of("https://example.com/alice-1.jpg", "https://example.com/alice-2.jpg"));
        alice.startVerification(User.VerificationMethod.EMAIL, "123456");
        alice.markVerified();
        userStorage.save(alice);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                        + "/profile-edit-snapshot"))
                                .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode snapshotJson = MAPPER.readTree(response.body());
        assertEquals(aliceId.toString(), snapshotJson.get("userId").asText());
        JsonNode editable = snapshotJson.get("editable");
        assertEquals(
                "Runner, coffee person, and weekend hiker.", editable.get("bio").asText());
        assertEquals("FEMALE", editable.get("gender").asText());
        assertEquals("MALE", editable.get("interestedIn").get(0).asText());
        assertEquals(25, editable.get("maxDistanceKm").asInt());
        assertEquals(27, editable.get("minAge").asInt());
        assertEquals(38, editable.get("maxAge").asInt());
        assertEquals(168, editable.get("heightCm").asInt());
        assertTrue(editable.get("interests").isArray());
        assertTrue(editable.get("dealbreakers").get("acceptableSmoking").isArray());
        assertEquals(6, editable.get("dealbreakers").get("maxAgeDifference").asInt());
        assertTrue(List.of("CITY", "ZIP")
                .contains(editable.get("location").get("precision").asText()));
        assertEquals("IL", editable.get("location").get("countryCode").asText());

        JsonNode readOnly = snapshotJson.get("readOnly");
        assertEquals("Alice Snapshot", readOnly.get("name").asText());
        assertEquals("ACTIVE", readOnly.get("state").asText());
        assertEquals(
                "https://example.com/alice-1.jpg",
                readOnly.get("photoUrls").get(0).asText());
        assertTrue(readOnly.get("verified").asBoolean());
        assertEquals("EMAIL", readOnly.get("verificationMethod").asText());
        assertFalse(readOnly.get("verifiedAt").isNull());
    }

    @Test
    @DisplayName("presentation context explains why a target profile is shown")
    void presentationContextExplainsWhyTargetProfileIsShown() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User viewer = activeUser(viewerId, "Viewer Context");
        User target = activeUser(targetId, "Target Context");
        target.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
        userStorage.save(viewer);
        userStorage.save(target);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + viewerId
                                        + "/presentation-context/" + targetId))
                                .header(AUTHORIZATION_HEADER, bearerToken(services, viewerId))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode contextJson = MAPPER.readTree(response.body());
        assertEquals(viewerId.toString(), contextJson.get("viewerUserId").asText());
        assertEquals(targetId.toString(), contextJson.get("targetUserId").asText());
        assertFalse(contextJson.get("summary").asText().isBlank());
        assertTrue(contextJson.get("reasonTags").isArray());
        assertTrue(contextJson.get("reasonTags").size() >= 1);
        assertTrue(contextJson.get("details").isArray());
        assertTrue(contextJson.get("details").size() >= 1);
        assertFalse(contextJson.get("generatedAt").isNull());
    }

    @Test
    @DisplayName("profile update and conversation mutation routes work end to end")
    void profileUpdateAndConversationMutationRoutesWorkEndToEnd() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        alice.setBio("Original bio");
        User bob = activeUser(bobId, "Bob");
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> updateProfileResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/profile"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "bio":"Updated bio",
                                  "maxDistanceKm":60,
                                  "minAge":21,
                                  "maxAge":32,
                                  "heightCm":170,
                                  "smoking":"NEVER",
                                  "drinking":"SOCIALLY",
                                  "wantsKids":"OPEN",
                                  "lookingFor":"LONG_TERM",
                                  "education":"BACHELORS",
                                  "interests":["MUSIC","TRAVEL"]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateProfileResponse.statusCode());
        JsonNode profileJson = MAPPER.readTree(updateProfileResponse.body());
        assertEquals("Updated bio", profileJson.get("bio").asText());

        User savedAlice = userStorage.get(aliceId).orElseThrow();
        assertEquals("Updated bio", savedAlice.getBio());
        assertEquals(60, savedAlice.getMaxDistanceKm());
        assertTrue(savedAlice.getInterests().contains(Interest.MUSIC));
        assertEquals(Lifestyle.LookingFor.LONG_TERM, savedAlice.getLookingFor());

        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                + datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId)
                                + "/messages"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello Bob\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, sendMessageResponse.statusCode());
        UUID messageId = UUID.fromString(
                MAPPER.readTree(sendMessageResponse.body()).get("id").asText());
        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);

        HttpResponse<String> deleteMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/" + conversationId
                                + "/messages/" + messageId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteMessageResponse.statusCode());

        HttpResponse<String> archiveConversationResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/conversations/" + conversationId + "/archive"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"reason\":\"UNMATCH\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, archiveConversationResponse.statusCode());
        var archivedConversation =
                communicationStorage.getConversation(conversationId).orElseThrow();
        MatchArchiveReason archiveReason = archivedConversation.getUserA().equals(aliceId)
                ? archivedConversation.getUserAArchiveReason()
                : archivedConversation.getUserBArchiveReason();
        assertEquals(MatchArchiveReason.UNMATCH, archiveReason);

        HttpResponse<String> deleteConversationResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/conversations/" + conversationId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteConversationResponse.statusCode());
        assertTrue(communicationStorage.getConversation(conversationId).isEmpty());

        HttpResponse<String> archiveMatchResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/matches/"
                                + Match.generateId(aliceId, bobId) + "/archive"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, archiveMatchResponse.statusCode());
        Match archivedMatch =
                interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.MatchState.UNMATCHED, archivedMatch.getState());
        assertEquals(Match.MatchArchiveReason.UNMATCH, archivedMatch.getEndReason());
        assertFalse(archivedMatch.isDeleted());
    }

    @Test
    @DisplayName("mutating user routes reject mismatched acting user even with valid payloads")
    void mutatingUserRoutesRejectMismatchedActingUserEvenWithValidPayloads() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice");
        User bob = activeUser(bobId, "Bob");
        userStorage.save(alice);
        userStorage.save(bob);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> updateProfileResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/profile"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, bobId))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "bio":"Updated bio",
                                  "maxDistanceKm":60,
                                  "minAge":21,
                                  "maxAge":32,
                                  "heightCm":170,
                                  "smoking":"NEVER",
                                  "drinking":"SOCIALLY",
                                  "wantsKids":"OPEN",
                                  "lookingFor":"LONG_TERM",
                                  "education":"BACHELORS",
                                  "interests":["MUSIC","TRAVEL"]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, updateProfileResponse.statusCode(), updateProfileResponse.body());
        JsonNode errorJson = MAPPER.readTree(updateProfileResponse.body());
        assertEquals("FORBIDDEN", errorJson.get("code").asText());
        assertEquals(
                "Acting user does not match requested user route",
                errorJson.get("message").asText());
    }

    @Test
    @DisplayName("mutating conversation routes require bearer auth and do not accept query fallback")
    void mutatingConversationRoutesRequireHeaderAndDoNotAcceptQueryFallback() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice"));
        userStorage.save(activeUser(bobId, "Bob"));
        interactionStorage.save(Match.create(aliceId, bobId));

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> missingHeaderResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/" + conversationId
                                + "/messages?userId=" + aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello Bob\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, missingHeaderResponse.statusCode(), missingHeaderResponse.body());
    }

    @Test
    @DisplayName("like, pass, conversations, and send message failures use the shared use-case failure mapper")
    void affectedRoutesUseTheSharedUseCaseFailureMapper() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice"));
        userStorage.save(activeUser(bobId, "Bob"));
        interactionStorage.save(Match.create(aliceId, bobId));

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        server = new RestApiServer(services, 0);
        replaceMatchingUseCases(server, new FailingMatchingUseCases(services));
        replaceMessagingUseCases(server, new FailingMessagingUseCases(services));
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> likeResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/like/" + bobId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, likeResponse.statusCode(), likeResponse.body());

        HttpResponse<String> passResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/pass/" + bobId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, passResponse.statusCode(), passResponse.body());

        HttpResponse<String> conversationsResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/conversations"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, conversationsResponse.statusCode(), conversationsResponse.body());

        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello Bob\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, sendMessageResponse.statusCode(), sendMessageResponse.body());
    }

    @Test
    @DisplayName("profile save, match, message, and graceful-exit flow work end to end")
    void profileSaveMatchMessageAndGracefulExitFlowWorksEndToEnd() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice HappyPath");
        alice.setBio("Before update");
        User bob = activeUser(bobId, "Bob HappyPath");
        userStorage.save(alice);
        userStorage.save(bob);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> updateProfileResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/profile"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "bio":"Updated happy-path bio",
                                  "maxDistanceKm":70,
                                  "minAge":22,
                                  "maxAge":34,
                                  "heightCm":168,
                                  "smoking":"NEVER",
                                  "drinking":"SOCIALLY",
                                  "wantsKids":"OPEN",
                                  "lookingFor":"LONG_TERM",
                                  "education":"BACHELORS",
                                  "interests":["MUSIC","TRAVEL"]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateProfileResponse.statusCode());
        assertEquals(
                "Updated happy-path bio", userStorage.get(aliceId).orElseThrow().getBio());

        HttpResponse<String> firstLikeResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/like/" + bobId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, firstLikeResponse.statusCode());

        HttpResponse<String> secondLikeResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + bobId + "/like/" + aliceId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, bobId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, secondLikeResponse.statusCode());
        JsonNode likeJson = MAPPER.readTree(secondLikeResponse.body());
        assertTrue(likeJson.get("isMatch").asBoolean());

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Hello from the happy path\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, sendMessageResponse.statusCode());

        HttpResponse<String> gracefulExitResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId
                                + "/relationships/" + bobId + "/graceful-exit"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, gracefulExitResponse.statusCode());

        Match updatedMatch =
                interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.MatchState.GRACEFUL_EXIT, updatedMatch.getState());
        var storedConversation =
                communicationStorage.getConversation(conversationId).orElseThrow();
        assertNotNull(storedConversation.getLastMessageAt());
    }

    @Test
    @DisplayName("pass route forwards daily-limit enforcement on record-like commands")
    void passRouteForwardsDailyLimitEnforcementOnRecordLikeCommands() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice Pass"));
        userStorage.save(activeUser(bobId, "Bob Pass"));

        CapturingMatchingUseCases matchingUseCases = new CapturingMatchingUseCases(services);
        server = new RestApiServer(services, 0);
        replaceMatchingUseCases(server, matchingUseCases);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> passResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/pass/" + bobId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, passResponse.statusCode());
        assertNotNull(matchingUseCases.lastCommand);
        assertEquals(UserContext.api(aliceId), matchingUseCases.lastCommand.context());
        assertEquals(bobId, matchingUseCases.lastCommand.targetUserId());
        assertEquals(Like.Direction.PASS, matchingUseCases.lastCommand.direction());
        assertTrue(matchingUseCases.lastCommand.enforceDailyLimit());
    }

    @Test
    @DisplayName("report-plus-block flow prevents follow-up messaging and preserves moderation state")
    void reportAndBlockFlowPreventsFollowUpMessaging() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice FailurePath");
        User bob = activeUser(bobId, "Bob FailurePath");
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(aliceId, bobId);
        HttpResponse<String> sendMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"Before block\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, sendMessageResponse.statusCode());

        HttpResponse<String> reportResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/report/" + bobId))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"reason\":\"HARASSMENT\",\"description\":\"Unsafe behavior\",\"blockUser\":true}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reportResponse.statusCode());
        JsonNode reportJson = MAPPER.readTree(reportResponse.body());
        assertTrue(reportJson.get("success").asBoolean());
        assertTrue(reportJson.get("blockedByReporter").asBoolean());

        HttpResponse<String> blockedMessageResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"senderId\":\"" + aliceId + "\",\"content\":\"This should fail\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, blockedMessageResponse.statusCode());
    }

    @Test
    @DisplayName("location lookup routes and selection-based profile updates support API parity")
    void locationLookupRoutesAndSelectionBasedProfileUpdatesSupportApiParity() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        SeededStandoutStorage standoutStorage = new SeededStandoutStorage();
        ServiceRegistry services =
                createServices(userStorage, interactionStorage, communicationStorage, standoutStorage);

        UUID aliceId = UUID.randomUUID();
        User alice = activeUser(aliceId, "Alice Parity");
        userStorage.save(alice);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> countriesResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/location/countries"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, countriesResponse.statusCode());
        JsonNode countriesJson = MAPPER.readTree(countriesResponse.body());
        assertEquals("IL", countriesJson.get(0).get("code").asText());

        HttpResponse<String> citiesResponse = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/location/cities?countryCode=IL&query=tel"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, citiesResponse.statusCode());
        JsonNode citiesJson = MAPPER.readTree(citiesResponse.body());
        assertEquals("Tel Aviv", citiesJson.get(0).get("name").asText());

        HttpResponse<String> resolveResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/location/resolve"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  \"countryCode\":\"IL\",
                                  \"zipCode\":\"9999999\",
                                  \"allowApproximate\":true
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resolveResponse.statusCode());
        JsonNode resolveJson = MAPPER.readTree(resolveResponse.body());
        assertTrue(resolveJson.get("approximate").asBoolean());
        assertTrue(resolveJson.get("label").asText().contains("Approximate"));

        HttpResponse<String> updateProfileResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId + "/profile"))
                        .header(AUTHORIZATION_HEADER, bearerToken(services, aliceId))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString("""
                                {
                                  \"bio\":\"Updated bio\",
                                  \"location\":{
                                    \"countryCode\":\"IL\",
                                    \"cityName\":\"Tel Aviv\"
                                  }
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateProfileResponse.statusCode());
        JsonNode profileJson = MAPPER.readTree(updateProfileResponse.body());
        assertEquals(
                "Tel Aviv, Tel Aviv District",
                profileJson.get("approximateLocation").asText());

        User savedAlice = userStorage.get(aliceId).orElseThrow();
        assertEquals(32.0853, savedAlice.getLat(), 0.0001);
        assertEquals(34.7818, savedAlice.getLon(), 0.0001);
    }

    private static ServiceRegistry createServices(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            OperationalCommunicationStorage communicationStorage,
            Standout.Storage standoutStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .standoutStorage(standoutStorage)
                .undoStorage(new RestApiTestFixture.InMemoryUndoStorage())
                .build();
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .bio("Coffee, hiking, and weekend plans")
                .gender(Gender.FEMALE)
                .interestedIn(EnumSet.of(Gender.MALE))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .maxDistanceKm(100)
                .photoUrls(List.of("https://example.com/" + name.toLowerCase().split(" ")[0] + ".jpg"))
                .interests(EnumSet.of(Interest.COFFEE, Interest.HIKING, Interest.TRAVEL))
                .build();
    }

    private static String bearerToken(ServiceRegistry services, UUID userId) {
        return RestApiTestFixture.bearerToken(services, userId, userId + "@example.com");
    }

    private static void replaceMatchingUseCases(RestApiServer server, MatchingUseCases matchingUseCases) {
        try {
            java.lang.reflect.Field field = RestApiServer.class.getDeclaredField("matchingUseCases");
            field.setAccessible(true);
            field.set(server, matchingUseCases);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to replace matching use cases in RestApiServer", e);
        }
    }

    private static void replaceMessagingUseCases(RestApiServer server, MessagingUseCases messagingUseCases) {
        try {
            java.lang.reflect.Field field = RestApiServer.class.getDeclaredField("messagingUseCases");
            field.setAccessible(true);
            field.set(server, messagingUseCases);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to replace messaging use cases in RestApiServer", e);
        }
    }

    private static final class CapturingMatchingUseCases extends MatchingUseCases {
        private datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand lastCommand;

        private CapturingMatchingUseCases(ServiceRegistry services) {
            super(
                    services.getCandidateFinder(),
                    services.getMatchingService(),
                    services.getDailyLimitService(),
                    services.getDailyPickService(),
                    services.getStandoutService(),
                    services.getUndoService(),
                    services.getInteractionStorage(),
                    services.getUserStorage(),
                    services.getMatchQualityService(),
                    services.getEventBus(),
                    services.getRecommendationService());
        }

        @Override
        public UseCaseResult<datingapp.app.usecase.matching.MatchingUseCases.RecordLikeResult> recordLike(
                datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand command) {
            lastCommand = command;
            Like like = Like.create(command.context().userId(), command.targetUserId(), command.direction());
            return UseCaseResult.success(
                    new datingapp.app.usecase.matching.MatchingUseCases.RecordLikeResult(like, Optional.empty()));
        }
    }

    private static final class FailingMatchingUseCases extends MatchingUseCases {
        private FailingMatchingUseCases(ServiceRegistry services) {
            super(
                    services.getCandidateFinder(),
                    services.getMatchingService(),
                    services.getDailyLimitService(),
                    services.getDailyPickService(),
                    services.getStandoutService(),
                    services.getUndoService(),
                    services.getInteractionStorage(),
                    services.getUserStorage(),
                    services.getMatchQualityService(),
                    services.getEventBus(),
                    services.getRecommendationService());
        }

        @Override
        public UseCaseResult<datingapp.app.usecase.matching.MatchingUseCases.RecordLikeResult> recordLike(
                datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand command) {
            return UseCaseResult.failure(UseCaseError.validation("Simulated matching failure"));
        }
    }

    private static final class FailingMessagingUseCases extends MessagingUseCases {
        private FailingMessagingUseCases(ServiceRegistry services) {
            super(services.getConnectionService(), services.getEventBus());
        }

        @Override
        public UseCaseResult<MessagingUseCases.ConversationListResult> listConversations(ListConversationsQuery query) {
            return UseCaseResult.failure(UseCaseError.validation("Simulated conversation failure"));
        }

        @Override
        public UseCaseResult<datingapp.core.connection.ConnectionService.SendResult> sendMessage(
                SendMessageCommand command) {
            return UseCaseResult.failure(UseCaseError.validation("Simulated message failure"));
        }
    }

    private static final class SeededStandoutStorage implements Standout.Storage {
        private final Map<String, List<Standout>> standoutsByKey = new HashMap<>();

        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            standoutsByKey.put(key(seekerId, date), new ArrayList<>(standouts));
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            return List.copyOf(standoutsByKey.getOrDefault(key(seekerId, date), List.of()));
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            String key = key(seekerId, date);
            List<Standout> existing = standoutsByKey.getOrDefault(key, List.of());
            List<Standout> updated = existing.stream()
                    .map(standout -> standout.standoutUserId().equals(standoutUserId)
                            ? standout.withInteraction(AppClock.now())
                            : standout)
                    .toList();
            standoutsByKey.put(key, new ArrayList<>(updated));
        }

        @Override
        public int cleanup(LocalDate before) {
            int originalSize = standoutsByKey.size();
            standoutsByKey.entrySet().removeIf(entry -> LocalDate.parse(
                            entry.getKey().split("\\|")[1])
                    .isBefore(before));
            return originalSize - standoutsByKey.size();
        }

        private static String key(UUID seekerId, LocalDate date) {
            return seekerId + "|" + date;
        }
    }
}
