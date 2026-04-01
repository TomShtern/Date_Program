package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
        assertEquals(
                "Tel Aviv, Tel Aviv District",
                userJson.get("approximateLocation").asText());

        HttpResponse<String> forbiddenUserResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + aliceId))
                        .header("X-User-Id", bobId.toString())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenUserResponse.statusCode());
        JsonNode forbiddenUserJson = MAPPER.readTree(forbiddenUserResponse.body());
        assertEquals("FORBIDDEN", forbiddenUserJson.get("code").asText());

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
        User candidate = activeUser(candidateId, "Candidate", Gender.MALE, EnumSet.of(Gender.FEMALE));
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
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + activeId + "/browse"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());
        assertEquals(1, browseJson.get("candidates").size());
        assertEquals(
                candidateId.toString(),
                browseJson.get("candidates").get(0).get("id").asText());
        assertTrue(browseJson.get("locationMissing").isBoolean());
        assertTrue(!browseJson.get("locationMissing").asBoolean());

        HttpResponse<String> inactiveCandidatesResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + inactiveId + "/candidates"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(409, inactiveCandidatesResponse.statusCode());
    }

    @Test
    @DisplayName("candidates route remains the deliberate direct-read exception")
    void candidatesRouteRemainsTheDeliberateDirectReadException() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        ServiceRegistry services = createServices(userStorage, interactionStorage, communicationStorage);

        UUID activeId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        User active = activeUser(activeId, "Active", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User candidate = activeUser(candidateId, "Candidate", Gender.MALE, EnumSet.of(Gender.FEMALE));
        userStorage.save(active);
        userStorage.save(candidate);

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> browseResponse = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + activeId + "/browse"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> candidatesResponse = client.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + activeId + "/candidates"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, browseResponse.statusCode());
        assertEquals(200, candidatesResponse.statusCode());

        JsonNode browseJson = MAPPER.readTree(browseResponse.body());
        JsonNode candidatesJson = MAPPER.readTree(candidatesResponse.body());

        assertTrue(browseJson.has("candidates"));
        assertTrue(browseJson.has("locationMissing"));
        assertTrue(candidatesJson.isArray());
        assertEquals(candidateId.toString(), candidatesJson.get(0).get("id").asText());
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
        User seeker = activeUser(seekerId, "Seeker", Gender.MALE, EnumSet.of(Gender.FEMALE));
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
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + seekerId + "/browse"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());

        assertTrue(browseJson.get("candidates").isArray());
        for (JsonNode candidate : browseJson.get("candidates")) {
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
        User seeker = activeUser(seekerId, "Seeker", Gender.FEMALE, EnumSet.of(Gender.MALE));
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
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/" + seekerId + "/browse"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());

        assertTrue(browseJson.get("candidates").isArray());
        for (JsonNode candidate : browseJson.get("candidates")) {
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
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + noLocationId + "/browse"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, browseResponse.statusCode());
        JsonNode browseJson = MAPPER.readTree(browseResponse.body());

        assertTrue(
                browseJson.get("locationMissing").asBoolean(),
                "locationMissing must be true when seeker has no location");
        assertEquals(0, browseJson.get("candidates").size(), "Candidates list must be empty when location is missing");
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

        User seeker = activeUser(seekerId, "Seeker", Gender.MALE, EnumSet.of(Gender.FEMALE));
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
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/users/" + seekerId + "/candidates"))
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
}
