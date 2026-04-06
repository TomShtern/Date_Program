package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.app.api.RestApiDtos.ErrorResponse;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.core.AppClock;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import io.javalin.http.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST API direct read routes")
class RestApiReadRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = "http://localhost:";
    private static final String USERS_PATH = "/api/users/";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String CANDIDATES_SEGMENT = "/candidates";
    private static final String BROWSE_SEGMENT = "/browse";
    private static final String CANDIDATES_KEY = "candidates";
    private static final String LOCATION_MISSING_KEY = "locationMissing";
    private static final String ALICE_NAME = "Alice";
    private static final String CANDIDATE_NAME = "Candidate";
    private static final String SEEKER_NAME = "Seeker";

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
        User alice = activeUser(aliceId, ALICE_NAME, Gender.FEMALE, EnumSet.of(Gender.MALE));
        alice.setLocation(32.0853, 34.7818);
        User bob = activeUser(bobId, "Bob", Gender.MALE, EnumSet.of(Gender.FEMALE));
        bob.setLocation(32.0870, 34.8877);
        userStorage.save(alice);
        userStorage.save(bob);
        interactionStorage.save(Match.create(aliceId, bobId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> usersResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + "/api/users"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, usersResponse.statusCode());
        assertEquals(2, MAPPER.readTree(usersResponse.body()).size());

        HttpResponse<String> userResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + aliceId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, userResponse.statusCode());
        JsonNode userJson = MAPPER.readTree(userResponse.body());
        assertEquals(ALICE_NAME, userJson.get("name").asText());

        HttpResponse<String> missingUserResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + UUID.randomUUID()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missingUserResponse.statusCode());
        assertTrue(!missingUserResponse.body().isBlank());

        HttpResponse<String> forbiddenUserResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + aliceId))
                        .header(USER_ID_HEADER, bobId.toString())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenUserResponse.statusCode());
        JsonNode forbiddenUserJson = MAPPER.readTree(forbiddenUserResponse.body());
        assertEquals("FORBIDDEN", forbiddenUserJson.get("code").asText());

        HttpResponse<String> candidatesResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + aliceId + CANDIDATES_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, candidatesResponse.statusCode());
        JsonNode candidatesJson = MAPPER.readTree(candidatesResponse.body());
        assertEquals(1, candidatesJson.size());
        assertEquals(bobId.toString(), candidatesJson.get(0).get("id").asText());

        HttpResponse<String> matchesResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + aliceId + "/matches"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, matchesResponse.statusCode());
        JsonNode matchesJson = MAPPER.readTree(matchesResponse.body());
        assertEquals(1, matchesJson.get("matches").size());
    }

    @Test
    @DisplayName("get user route surfaces not found via explicit Optional-based helper flow")
    void getUserRouteSurfacesNotFoundViaExplicitOptionalBasedHelperFlow() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        server = new RestApiServer(services, 0);
        Method method = RestApiServer.class.getDeclaredMethod("getUser", Context.class);
        method.setAccessible(true);

        UUID missingUserId = UUID.randomUUID();
        CapturingContext ctx = capturingContext(USERS_PATH + missingUserId, Map.of("id", missingUserId.toString()));

        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, () -> method.invoke(server, ctx.context));
        assertEquals("NotFoundResponse", thrown.getCause().getClass().getSimpleName());
        assertEquals("User not found", thrown.getCause().getMessage());
    }

    @Test
    @DisplayName("successful null payloads are reported as internal errors instead of collapsing to empty")
    void successfulNullPayloadsAreReportedAsInternalErrorsInsteadOfCollapsingToEmpty() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        server = new RestApiServer(services, 0);
        Method method = RestApiServer.class.getDeclaredMethod(
                "requiredDataOrHandleFailure", Context.class, UseCaseResult.class, String.class);
        method.setAccessible(true);

        CapturingContext ctx = capturingContext("/api/users", Map.of());
        Object result = method.invoke(
                server, ctx.context, UseCaseResult.<Object>success(null), "Unexpected successful null payload");

        assertTrue(result instanceof java.util.Optional);
        assertTrue(((java.util.Optional<?>) result).isEmpty());
        assertEquals(500, ctx.status);
        assertTrue(ctx.jsonPayload instanceof ErrorResponse);
        ErrorResponse error = (ErrorResponse) ctx.jsonPayload;
        assertEquals("INTERNAL_ERROR", error.code());
        assertEquals("Unexpected successful null payload", error.message());
    }

    @Test
    @DisplayName("browse users route returns browse results and candidates route rejects inactive users")
    void browseRouteReturnsBrowseResultsAndCandidatesRouteRejectsInactiveUsers() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID activeId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        User active = activeUser(activeId, "Active", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User candidate = activeUser(candidateId, CANDIDATE_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        User inactive = activeUser(inactiveId, "Inactive", Gender.FEMALE, EnumSet.of(Gender.MALE));
        inactive.pause();
        userStorage.save(active);
        userStorage.save(candidate);
        userStorage.save(inactive);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> browseResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + activeId + BROWSE_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());
        assertEquals(1, browseJson.get(CANDIDATES_KEY).size());
        assertEquals(
                candidateId.toString(),
                browseJson.get(CANDIDATES_KEY).get(0).get("id").asText());
        assertTrue(browseJson.get(LOCATION_MISSING_KEY).isBoolean());
        assertTrue(!browseJson.get(LOCATION_MISSING_KEY).asBoolean());

        HttpResponse<String> inactiveCandidatesResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + inactiveId + CANDIDATES_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, inactiveCandidatesResponse.statusCode());
    }

    @Test
    @DisplayName("candidates route preserves array shape while matching browse ordering")
    void candidatesRoutePreservesArrayShapeWhileMatchingBrowseOrdering() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID seekerId = UUID.randomUUID();
        User seeker = rankedBrowseUser(seekerId, "Seeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 40.7128, -74.0060);
        User nearWeak = sparseBrowseCandidate(
                UUID.randomUUID(),
                "NearWeak",
                40.7130,
                -74.0062,
                Gender.FEMALE,
                EnumSet.of(Gender.MALE),
                AppClock.now().minus(java.time.Duration.ofDays(60)));
        User farStrong = rankedBrowseUser(
                UUID.randomUUID(), "FarStrong", Gender.FEMALE, EnumSet.of(Gender.MALE), 40.7700, -74.0500);

        userStorage.save(seeker);
        userStorage.save(nearWeak);
        userStorage.save(farStrong);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> browseResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + seekerId + BROWSE_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> candidatesResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + seekerId + CANDIDATES_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, browseResponse.statusCode());
        assertEquals(200, candidatesResponse.statusCode());

        JsonNode browseJson = MAPPER.readTree(browseResponse.body());
        JsonNode candidatesJson = MAPPER.readTree(candidatesResponse.body());

        assertTrue(browseJson.has(CANDIDATES_KEY));
        assertTrue(browseJson.has(LOCATION_MISSING_KEY));
        assertTrue(candidatesJson.isArray());
        List<String> browseCandidateIds = new ArrayList<>();
        for (JsonNode candidate : browseJson.get(CANDIDATES_KEY)) {
            browseCandidateIds.add(candidate.get("id").asText());
        }
        List<String> candidatesIds = new ArrayList<>();
        for (JsonNode candidate : candidatesJson) {
            candidatesIds.add(candidate.get("id").asText());
        }

        assertEquals(browseCandidateIds, candidatesIds);
        assertEquals(farStrong.getId().toString(), candidatesIds.getFirst());
        assertEquals(
                "true",
                candidatesResponse.headers().firstValue("Deprecation").orElse(null),
                "/candidates should advertise deprecation as a compatibility alias");
        assertTrue(
                candidatesResponse
                        .headers()
                        .firstValue("Link")
                        .orElse("")
                        .contains(USERS_PATH + seekerId + BROWSE_SEGMENT),
                "/candidates should point callers to /browse as the canonical route");
    }

    @Test
    @DisplayName("/browse endpoint excludes symmetrically blocked users")
    void browseExcludesSymmetricallyBlockedUsers() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .build();

        UUID seekerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        User seeker = activeUser(seekerId, SEEKER_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        seeker.setLocation(32.0853, 34.7818);
        User blocked = activeUser(blockedId, "Blocked", Gender.FEMALE, EnumSet.of(Gender.MALE));
        blocked.setLocation(32.0870, 34.8877);
        userStorage.save(seeker);
        userStorage.save(blocked);
        trustSafetyStorage.save(ConnectionModels.Block.create(blockedId, seekerId));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> browseResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + seekerId + BROWSE_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());

        assertTrue(browseJson.get("candidates").isArray());
        for (JsonNode candidate : browseJson.get(CANDIDATES_KEY)) {
            assertNotEquals(
                    blockedId.toString(),
                    candidate.get("id").asText(),
                    "Symmetrically blocked user should not appear in /browse");
        }
    }

    @Test
    @DisplayName("/browse endpoint excludes recently unmatched users within cooldown")
    void browseExcludesRecentlyUnmatchedUsers() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();

        UUID seekerId = UUID.randomUUID();
        UUID unmatchedId = UUID.randomUUID();
        User seeker = activeUser(seekerId, SEEKER_NAME, Gender.FEMALE, EnumSet.of(Gender.MALE));
        seeker.setLocation(32.0853, 34.7818);
        User recentlyUnmatched = activeUser(unmatchedId, "RecentlyUnmatched", Gender.MALE, EnumSet.of(Gender.FEMALE));
        recentlyUnmatched.setLocation(32.0870, 34.8877);
        userStorage.save(seeker);
        userStorage.save(recentlyUnmatched);
        Match recentlyEndedMatch = Match.create(seekerId, unmatchedId);
        recentlyEndedMatch.unmatch(seekerId);
        interactionStorage.save(recentlyEndedMatch);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> browseResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + seekerId + BROWSE_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());

        assertTrue(browseJson.get("candidates").isArray());
        for (JsonNode candidate : browseJson.get(CANDIDATES_KEY)) {
            assertNotEquals(
                    unmatchedId.toString(),
                    candidate.get("id").asText(),
                    "Recently unmatched user should not appear within cooldown window");
        }
    }

    @Test
    @DisplayName("/browse endpoint still reports locationMissing when seeker has no location")
    void browseReportsLocationMissingWhenSeekerHasNoLocation() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();

        UUID noLocationId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        User noLocation = User.StorageBuilder.create(noLocationId, "NoLocation", AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(Gender.FEMALE)
                .interestedIn(EnumSet.of(Gender.MALE))
                .build();
        User candidate = activeUser(candidateId, "Candidate", Gender.MALE, EnumSet.of(Gender.FEMALE));
        candidate.setLocation(32.0870, 34.8877);
        userStorage.save(noLocation);
        userStorage.save(candidate);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> browseResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + noLocationId + BROWSE_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());

        assertTrue(
                browseJson.get(LOCATION_MISSING_KEY).asBoolean(),
                "locationMissing must be true when seeker has no location");
        assertEquals(
                0, browseJson.get(CANDIDATES_KEY).size(), "Candidates list must be empty when location is missing");
    }

    @Test
    @DisplayName("/candidates endpoint returns an empty array when seeker has no location")
    void candidatesReturnsEmptyArrayWhenSeekerHasNoLocation() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();

        UUID noLocationId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        User noLocation = User.StorageBuilder.create(noLocationId, "NoLocation", AppClock.now())
                .state(UserState.ACTIVE)
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(Gender.FEMALE)
                .interestedIn(EnumSet.of(Gender.MALE))
                .build();
        User candidate = activeUser(candidateId, "Candidate", Gender.MALE, EnumSet.of(Gender.FEMALE));
        candidate.setLocation(32.0870, 34.8877);
        userStorage.save(noLocation);
        userStorage.save(candidate);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> candidatesResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + noLocationId + CANDIDATES_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, candidatesResponse.statusCode());
        JsonNode candidatesJson = MAPPER.readTree(candidatesResponse.body());

        assertTrue(candidatesJson.isArray());
        assertEquals(0, candidatesJson.size(), "Candidates list must be empty when location is missing");
    }

    @Test
    @DisplayName("/candidates endpoint also excludes blocked and recently unmatched users")
    void candidatesEndpointExcludesBlockedAndUnmatchedUsers() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .build();

        UUID seekerId = UUID.randomUUID();
        UUID eligibleId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UUID unmatchedId = UUID.randomUUID();

        User seeker = activeUser(seekerId, SEEKER_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        User eligible = activeUser(eligibleId, "Eligible", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User blocked = activeUser(blockedId, "Blocked", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User unmatched = activeUser(unmatchedId, "Unmatched", Gender.FEMALE, EnumSet.of(Gender.MALE));

        userStorage.save(seeker);
        userStorage.save(eligible);
        userStorage.save(blocked);
        userStorage.save(unmatched);

        trustSafetyStorage.save(ConnectionModels.Block.create(blockedId, seekerId));
        Match recentlyEndedMatch = Match.create(seekerId, unmatchedId);
        recentlyEndedMatch.unmatch(seekerId);
        interactionStorage.save(recentlyEndedMatch);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> candidatesResponse = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + USERS_PATH + seekerId + CANDIDATES_SEGMENT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, candidatesResponse.statusCode());
        JsonNode candidatesJson = MAPPER.readTree(candidatesResponse.body());

        assertTrue(candidatesJson.isArray());
        List<String> candidateIds = new ArrayList<>();
        for (JsonNode candidate : candidatesJson) {
            candidateIds.add(candidate.get("id").asText());
        }
        assertTrue(!candidateIds.isEmpty(), "Eligible candidate should remain visible");
        assertTrue(candidateIds.contains(eligibleId.toString()), "Eligible candidate should remain visible");
        assertTrue(!candidateIds.contains(blockedId.toString()), "Blocked user should not appear in /candidates");
        assertTrue(
                !candidateIds.contains(unmatchedId.toString()),
                "Recently unmatched user should not appear in /candidates");
    }

    @Test
    @DisplayName("delete user route returns no content and soft deletes the account")
    void deleteUserRouteReturnsNoContentAndSoftDeletesTheAccount() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID userId = UUID.randomUUID();
        userStorage.save(activeUser(userId, "DeleteMe", Gender.FEMALE, EnumSet.of(Gender.MALE)));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> deleteResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + userId))
                        .header("X-User-Id", userId.toString())
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteResponse.statusCode());
        assertTrue(userStorage.get(userId).orElseThrow().isDeleted());
    }

    @Test
    @DisplayName("conversation message reads require an acting user header")
    void conversationMessageReadsRequireActingUserHeader() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice", Gender.FEMALE, EnumSet.of(Gender.MALE)));
        userStorage.save(activeUser(bobId, "Bob", Gender.MALE, EnumSet.of(Gender.FEMALE)));
        interactionStorage.save(Match.create(aliceId, bobId));
        services.getConnectionService().sendMessage(aliceId, bobId, "Hello Bob");

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        String conversationId = ConnectionModels.Conversation.generateId(aliceId, bobId);

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                        + conversationId + "/messages"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    @DisplayName("conversation message reads reject acting users outside the conversation")
    void conversationMessageReadsRejectUsersOutsideTheConversation() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID malloryId = UUID.randomUUID();
        userStorage.save(activeUser(aliceId, "Alice", Gender.FEMALE, EnumSet.of(Gender.MALE)));
        userStorage.save(activeUser(bobId, "Bob", Gender.MALE, EnumSet.of(Gender.FEMALE)));
        userStorage.save(activeUser(malloryId, "Mallory", Gender.OTHER, EnumSet.of(Gender.FEMALE)));
        interactionStorage.save(Match.create(aliceId, bobId));
        services.getConnectionService().sendMessage(aliceId, bobId, "Secret hello");

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        String conversationId = ConnectionModels.Conversation.generateId(aliceId, bobId);

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/"
                                        + conversationId + "/messages"))
                                .header("X-User-Id", malloryId.toString())
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode(), response.body());
    }

    private static ServiceRegistry createServices(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .build();
    }

    private static CapturingContext capturingContext(String path, Map<String, String> pathParams) {
        CapturingContext state = new CapturingContext();
        Context ctx = (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[] {Context.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "path" -> path;
                    case "pathParamMap" -> pathParams;
                    case "pathParam" -> pathParams.get(args[0].toString());
                    case "status" -> {
                        state.status = ((Number) args[0]).intValue();
                        yield proxy;
                    }
                    case "json" -> {
                        state.jsonPayload = args[0];
                        yield proxy;
                    }
                    default -> defaultValue(invokedMethod.getReturnType());
                });
        state.context = ctx;
        return state;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(byte.class)) {
            return (byte) 0;
        }
        if (returnType.equals(short.class)) {
            return (short) 0;
        }
        if (returnType.equals(char.class)) {
            return '\0';
        }
        if (returnType.equals(int.class)) {
            return 0;
        }
        if (returnType.equals(long.class)) {
            return 0L;
        }
        if (returnType.equals(float.class)) {
            return 0.0f;
        }
        if (returnType.equals(double.class)) {
            return 0.0d;
        }
        return null;
    }

    private static final class CapturingContext {
        private Context context;
        private int status;
        private Object jsonPayload;
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

    private static User rankedBrowseUser(
            UUID id, String name, Gender gender, EnumSet<Gender> interestedIn, double lat, double lon) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .bio("Shared interests and complete profile")
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(gender)
                .interestedIn(interestedIn)
                .location(lat, lon)
                .hasLocationSet(true)
                .maxDistanceKm(100)
                .photoUrls(List.of("https://example.com/" + name.toLowerCase() + ".jpg"))
                .interests(EnumSet.of(
                        datingapp.core.profile.MatchPreferences.Interest.HIKING,
                        datingapp.core.profile.MatchPreferences.Interest.COFFEE,
                        datingapp.core.profile.MatchPreferences.Interest.MUSIC))
                .smoking(datingapp.core.profile.MatchPreferences.Lifestyle.Smoking.NEVER)
                .drinking(datingapp.core.profile.MatchPreferences.Lifestyle.Drinking.SOCIALLY)
                .wantsKids(datingapp.core.profile.MatchPreferences.Lifestyle.WantsKids.SOMEDAY)
                .lookingFor(datingapp.core.profile.MatchPreferences.Lifestyle.LookingFor.LONG_TERM)
                .pacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
                        datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
                        datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                        datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                        datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.DEEP_CHAT))
                .build();
    }

    private static User sparseBrowseCandidate(
            UUID id,
            String name,
            double lat,
            double lon,
            Gender gender,
            EnumSet<Gender> interestedIn,
            java.time.Instant updatedAt) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .bio("")
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(gender)
                .interestedIn(interestedIn)
                .location(lat, lon)
                .hasLocationSet(true)
                .maxDistanceKm(100)
                .photoUrls(List.of())
                .updatedAt(updatedAt)
                .build();
    }
}
