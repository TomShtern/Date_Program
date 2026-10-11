package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.testutil.TestStorages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Admin routes")
class AdminRoutesTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
        System.clearProperty("datingapp.adminIds");
    }

    @Test
    @DisplayName("non-admin gets 403 and admin gets 200 with ban/dismiss flow")
    void adminAllowlistEnforced() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Communications comms = new TestStorages.Communications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(comms);
        User admin = activeUser("Admin");
        User target = activeUser("Target");
        target.flagForReview();
        users.save(admin);
        users.save(target);
        ServiceRegistry services =
                RestApiTestFixture.builder(users, interactions, comms).build();
        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<String> forbidden = get(port, "/api/admin/reports", token(services, admin));
        assertEquals(403, forbidden.statusCode());

        System.setProperty("datingapp.adminIds", admin.getId().toString());
        HttpResponse<String> ok = get(port, "/api/admin/reports", token(services, admin));
        assertEquals(200, ok.statusCode());

        HttpResponse<String> ban = post(port, "/api/admin/reports/" + target.getId() + "/ban", token(services, admin));
        assertEquals(200, ban.statusCode());
        assertEquals(
                User.UserState.BANNED, users.get(target.getId()).orElseThrow().getState());
    }

    @Test
    @DisplayName("a Clerk user id from the configured allowlist grants admin access")
    void configuredClerkIdGrantsAdminAccess() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Communications comms = new TestStorages.Communications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(comms);
        User admin = activeUser("ClerkAdmin");
        User other = activeUser("NotAdmin");
        users.save(admin);
        users.save(other);
        // TestStorages.Auth links the Clerk id "user_test_<uuid>" to that local user.
        ServiceRegistry services = RestApiTestFixture.builder(users, interactions, comms)
                .config(AppConfig.builder()
                        .adminClerkIds(List.of("user_test_" + admin.getId()))
                        .build())
                .build();
        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        assertEquals(
                200, get(port, "/api/admin/reports", token(services, admin)).statusCode());
        assertEquals(
                403, get(port, "/api/admin/reports", token(services, other)).statusCode());
    }

    private static String token(ServiceRegistry services, User user) {
        return RestApiTestFixture.bearerToken(services, user);
    }

    private static HttpResponse<String> get(int port, String path, String token) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(int port, String path, String token) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static User activeUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setBirthDate(LocalDate.of(1995, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(32.0, 34.0);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("admin test user " + name);
        user.setPacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
                datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
                datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
