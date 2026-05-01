package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestStorages;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("REST API photo routes")
class RestApiPhotoRoutesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String BASE_URL = "http://localhost:";

    @TempDir
    Path tempDir;

    private RestApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    @DisplayName("authenticated upload, DTO exposure, static serving, and delete work end to end")
    void authenticatedUploadDtoExposureStaticServingAndDeleteWorkEndToEnd() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        User alice = activeUser(UUID.randomUUID(), "Alice", "alice@example.com");
        userStorage.save(alice);

        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(photoConfig())
                .build();

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        byte[] imageBytes = pngBytes(0x33AA66);
        HttpResponse<String> uploadResponse =
                uploadPhoto(services, port, alice.getId(), alice.getEmail(), imageBytes, "alpha.png");
        assertEquals(201, uploadResponse.statusCode(), uploadResponse.body());

        JsonNode uploadJson = MAPPER.readTree(uploadResponse.body());
        String photoId = uploadJson.get("photo").get("id").asText();
        String photoUrl = uploadJson.get("photo").get("url").asText();
        assertEquals(photoUrl, uploadJson.get("primaryPhotoUrl").asText());
        assertEquals(List.of(photoUrl), MAPPER.convertValue(uploadJson.get("photoUrls"), List.class));
        assertTrue(photoUrl.startsWith("http://localhost:"), photoUrl);

        User storedUser = userStorage.get(alice.getId()).orElseThrow();
        assertEquals(1, storedUser.getPhotoUrls().size());
        String storedPath = storedUser.getPhotoUrls().getFirst();
        assertTrue(storedPath.startsWith("/photos/" + alice.getId() + "/"), storedPath);
        assertFalse(storedPath.startsWith("http://") || storedPath.startsWith("https://"), storedPath);

        HttpResponse<String> userResponse =
                authorizedGet(services, port, "/api/users/" + alice.getId(), alice.getId(), alice.getEmail());
        assertEquals(200, userResponse.statusCode(), userResponse.body());
        JsonNode userJson = MAPPER.readTree(userResponse.body());
        assertEquals(photoUrl, userJson.get("primaryPhotoUrl").asText());
        assertEquals(photoUrl, userJson.get("photoUrls").get(0).asText());

        HttpResponse<byte[]> staticPhotoResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(photoUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, staticPhotoResponse.statusCode());
        assertArrayEquals(imageBytes, staticPhotoResponse.body());

        HttpResponse<String> deleteResponse = request(
                port,
                "/api/users/" + alice.getId() + "/photos/" + photoId,
                "DELETE",
                RestApiTestFixture.bearerToken(services, alice),
                null,
                null);
        assertEquals(200, deleteResponse.statusCode(), deleteResponse.body());

        HttpResponse<String> userAfterDelete =
                authorizedGet(services, port, "/api/users/" + alice.getId(), alice.getId(), alice.getEmail());
        assertEquals(200, userAfterDelete.statusCode(), userAfterDelete.body());
        JsonNode deletedJson = MAPPER.readTree(userAfterDelete.body());
        assertTrue(deletedJson.get("photoUrls").isArray());
        assertEquals(0, deletedJson.get("photoUrls").size());
        assertTrue(deletedJson.get("primaryPhotoUrl").isNull()
                || deletedJson.get("primaryPhotoUrl").asText().isBlank());
    }

    @Test
    @DisplayName("photo upload requires authentication and a matching bearer subject")
    void photoUploadRequiresAuthenticationAndMatchingBearerSubject() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        User alice = activeUser(UUID.randomUUID(), "Alice", "alice@example.com");
        User bob = activeUser(UUID.randomUUID(), "Bob", "bob@example.com");
        userStorage.save(alice);
        userStorage.save(bob);

        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(photoConfig())
                .build();

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        byte[] imageBytes = pngBytes(0x2244CC);
        HttpResponse<String> missingTokenResponse = request(
                port,
                "/api/users/" + alice.getId() + "/photos",
                "POST",
                null,
                multipartContentType("missing"),
                multipartBody("missing", "photo", "missing.png", "image/png", imageBytes));
        assertEquals(401, missingTokenResponse.statusCode(), missingTokenResponse.body());

        HttpResponse<String> wrongUserResponse = request(
                port,
                "/api/users/" + alice.getId() + "/photos",
                "POST",
                RestApiTestFixture.bearerToken(services, bob),
                multipartContentType("wrong-user"),
                multipartBody("wrong-user", "photo", "wrong-user.png", "image/png", imageBytes));
        assertEquals(403, wrongUserResponse.statusCode(), wrongUserResponse.body());
    }

    @Test
    @DisplayName("reorder changes gallery order and primary photo")
    void reorderChangesGalleryOrderAndPrimaryPhoto() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        User alice = activeUser(UUID.randomUUID(), "Alice", "alice@example.com");
        userStorage.save(alice);

        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(photoConfig())
                .build();

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        JsonNode firstUpload = MAPPER.readTree(
                uploadPhoto(services, port, alice.getId(), alice.getEmail(), pngBytes(0xAA3300), "one.png")
                        .body());
        JsonNode secondUpload = MAPPER.readTree(
                uploadPhoto(services, port, alice.getId(), alice.getEmail(), pngBytes(0x0033AA), "two.png")
                        .body());

        String firstPhotoId = firstUpload.get("photo").get("id").asText();
        String firstPhotoUrl = firstUpload.get("photo").get("url").asText();
        String secondPhotoId = secondUpload.get("photo").get("id").asText();
        String secondPhotoUrl = secondUpload.get("photo").get("url").asText();

        HttpResponse<String> reorderResponse = request(
                port,
                "/api/users/" + alice.getId() + "/photos/order",
                "PUT",
                RestApiTestFixture.bearerToken(services, alice),
                "application/json",
                "{\"photoIds\":[\"%s\",\"%s\"]}"
                        .formatted(secondPhotoId, firstPhotoId)
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals(200, reorderResponse.statusCode(), reorderResponse.body());
        JsonNode reorderJson = MAPPER.readTree(reorderResponse.body());
        assertEquals(secondPhotoUrl, reorderJson.get("primaryPhotoUrl").asText());
        assertEquals(secondPhotoUrl, reorderJson.get("photoUrls").get(0).asText());
        assertEquals(firstPhotoUrl, reorderJson.get("photoUrls").get(1).asText());
    }

    @Test
    @DisplayName("deleted and banned users cannot mutate photos")
    void deletedAndBannedUsersCannotMutatePhotos() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        User deletedUser = activeUser(UUID.randomUUID(), "Deleted", "deleted@example.com");
        deletedUser.markDeleted(AppClock.now());
        User bannedUser = activeUser(UUID.randomUUID(), "Banned", "banned@example.com");
        bannedUser.ban();
        userStorage.save(deletedUser);
        userStorage.save(bannedUser);

        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(photoConfig())
                .build();

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();
        byte[] imageBytes = pngBytes(0xAA66FF);

        HttpResponse<String> deletedUploadResponse = uploadPhotoWithToken(
                port,
                deletedUser.getId(),
                RestApiTestFixture.bearerToken(services, deletedUser),
                imageBytes,
                "deleted.png");
        assertEquals(401, deletedUploadResponse.statusCode(), deletedUploadResponse.body());

        HttpResponse<String> bannedUploadResponse = uploadPhotoWithToken(
                port,
                bannedUser.getId(),
                RestApiTestFixture.bearerToken(services, bannedUser),
                imageBytes,
                "banned.png");
        assertEquals(401, bannedUploadResponse.statusCode(), bannedUploadResponse.body());
    }

    @Test
    @DisplayName("static photo route rejects unexpected filename characters")
    void staticPhotoRouteRejectsUnexpectedFilenameCharacters() throws Exception {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Communications communicationStorage = new TestStorages.Communications();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions(communicationStorage);
        User alice = activeUser(UUID.randomUUID(), "Alice", "alice@example.com");
        userStorage.save(alice);

        ServiceRegistry services = RestApiTestFixture.builder(userStorage, interactionStorage, communicationStorage)
                .config(photoConfig())
                .build();

        Path userDirectory = tempDir.resolve("photos").resolve(alice.getId().toString());
        Files.createDirectories(userDirectory);
        Files.write(userDirectory.resolve("evil$.png"), pngBytes(0x337799));

        server = new RestApiServer(services, 0);
        server.start();
        int port = server.getApp().port();

        HttpResponse<byte[]> response = CLIENT.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + "/photos/" + alice.getId() + "/evil$.png"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(404, response.statusCode());

        HttpResponse<byte[]> traversalResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + "/photos/" + alice.getId() + "/..%2Fsecret.png"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, traversalResponse.statusCode());

        HttpResponse<byte[]> separatorResponse = CLIENT.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + port + "/photos/" + alice.getId() + "/evil%5Cname.png"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, separatorResponse.statusCode());
    }

    private AppConfig photoConfig() {
        return AppConfig.builder()
                .photoStorageRoot(tempDir.resolve("photos").toString())
                .photoPublicBaseUrl("")
                .maxPhotoUploadBytes(1024 * 1024)
                .build();
    }

    private static HttpResponse<String> uploadPhoto(
            ServiceRegistry services, int port, UUID userId, String email, byte[] imageBytes, String filename)
            throws Exception {
        return uploadPhotoWithToken(
                port, userId, RestApiTestFixture.bearerToken(services, userId, email), imageBytes, filename);
    }

    private static HttpResponse<String> uploadPhotoWithToken(
            int port, UUID userId, String bearerToken, byte[] imageBytes, String filename) throws Exception {
        String boundary = "boundary-" + UUID.randomUUID();
        return request(
                port,
                "/api/users/" + userId + "/photos",
                "POST",
                bearerToken,
                multipartContentType(boundary),
                multipartBody(boundary, "photo", filename, "image/png", imageBytes));
    }

    private static HttpResponse<String> authorizedGet(
            ServiceRegistry services, int port, String path, UUID userId, String email) throws Exception {
        return request(port, path, "GET", RestApiTestFixture.bearerToken(services, userId, email), null, null);
    }

    private static HttpResponse<String> request(
            int port, String path, String method, String bearerToken, String contentType, byte[] body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + port + path));
        if (bearerToken != null) {
            builder.header(AUTHORIZATION_HEADER, bearerToken);
        }
        if (contentType != null) {
            builder.header(CONTENT_TYPE_HEADER, contentType);
        }
        switch (method) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String multipartContentType(String boundary) {
        return "multipart/form-data; boundary=" + boundary;
    }

    private static byte[] multipartBody(
            String boundary, String fieldName, String filename, String contentType, byte[] fileBytes) {
        byte[] prefix = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename
                        + "\"\r\n"
                        + "Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + fileBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(fileBytes, 0, body, prefix.length, fileBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + fileBytes.length, suffix.length);
        return body;
    }

    private static byte[] pngBytes(int rgb) throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static User activeUser(UUID id, String name, String email) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .email(email)
                .birthDate(LocalDate.of(1998, 1, 1))
                .gender(Gender.FEMALE)
                .interestedIn(EnumSet.of(Gender.MALE))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .build();
    }
}
