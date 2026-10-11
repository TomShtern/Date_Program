package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.media.LocalDiskPhotoStore;
import datingapp.core.media.PhotoStore;
import datingapp.core.model.User;
import datingapp.storage.jdbi.JdbiPhotoStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
@DisplayName("JdbiPhotoStore")
class JdbiPhotoStoreTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";
    private static final String EXIF_MARKER_TEXT = "GPS-LAT-32.0853-SECRET";
    private static final AppConfig DATABASE_PHOTOS =
            AppConfig.builder().photoStorageBackend("database").build();

    private DatabaseManager dbManager;
    private ServiceRegistry services;
    private PhotoStore store;
    private UUID userId;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        dbManager = DatabaseManager.createIsolated(
                "jdbc:h2:mem:photos_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        services = StorageFactory.buildSqlDatabase(dbManager, DATABASE_PHOTOS);
        store = services.getPhotoStore();
        userId = saveUser("Photo Owner");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        if (dbManager != null) {
            dbManager.shutdown();
        }
    }

    @Test
    @DisplayName("the DATABASE backend wires JdbiPhotoStore and the default stays on local disk")
    void backendIsChosenByConfig() {
        assertInstanceOf(JdbiPhotoStore.class, store);

        DatabaseManager diskManager = DatabaseManager.createIsolated(
                "jdbc:h2:mem:photos_disk_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        try {
            ServiceRegistry diskServices = StorageFactory.buildSqlDatabase(diskManager, AppConfig.defaults());
            assertInstanceOf(LocalDiskPhotoStore.class, diskServices.getPhotoStore());
        } finally {
            diskManager.shutdown();
        }
    }

    @Test
    @DisplayName("an unknown backend name is rejected when the config is built")
    void unknownBackendIsRejected() {
        AppConfig.Builder builder = AppConfig.builder().photoStorageBackend("s3");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("photoStorageBackend must be DISK or DATABASE", error.getMessage());
    }

    @Test
    @DisplayName("store keeps the disk path layout and load returns the re-encoded image without EXIF")
    void storeThenLoadRoundTrips() throws Exception {
        byte[] withExif = jpegWithExifSegment();

        PhotoStore.ManagedPhoto stored = store.store(userId, "image/jpeg", withExif);

        String fileName = stored.id() + ".jpg";
        assertEquals("/photos/" + userId + "/" + fileName, stored.storedPath());
        assertTrue(store.exists(userId, fileName));
        PhotoStore.PhotoContent loaded = store.load(userId, fileName).orElseThrow();
        assertEquals("image/jpeg", loaded.contentType());
        assertFalse(contains(loaded.bytes(), EXIF_MARKER_TEXT), "EXIF text must not survive the upload");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(loaded.bytes()));
        assertEquals(8, decoded.getWidth());
    }

    @Test
    @DisplayName("a photo is still served by a new registry on the same database, as after a host restart")
    void photoSurvivesANewRegistry() throws Exception {
        PhotoStore.ManagedPhoto stored = store.store(userId, "image/png", png());
        byte[] original = store.load(userId, stored.id() + ".png").orElseThrow().bytes();

        ServiceRegistry restarted = StorageFactory.buildSqlDatabase(dbManager, DATABASE_PHOTOS);

        PhotoStore.PhotoContent reloaded =
                restarted.getPhotoStore().load(userId, stored.id() + ".png").orElseThrow();
        assertEquals("image/png", reloaded.contentType());
        assertArrayEquals(original, reloaded.bytes());
    }

    @Test
    @DisplayName("delete removes one photo, by stored path or by public URL, and leaves the others")
    void deleteRemovesOnlyTheNamedPhoto() throws Exception {
        PhotoStore.ManagedPhoto first = store.store(userId, "image/png", png());
        PhotoStore.ManagedPhoto second = store.store(userId, "image/png", png());
        PhotoStore.ManagedPhoto third = store.store(userId, "image/png", png());

        store.delete(first.storedPath());
        store.delete("https://example.onrender.com" + second.storedPath());
        store.delete("https://cdn.example.com/elsewhere/picture.png");

        assertFalse(store.exists(userId, first.id() + ".png"));
        assertFalse(store.exists(userId, second.id() + ".png"));
        assertTrue(store.exists(userId, third.id() + ".png"));
        assertEquals(1, photoRowCount());
    }

    @Test
    @DisplayName("deleteAllForUser removes that user's photos and nobody else's")
    void deleteAllForUserIsScopedToTheUser() throws Exception {
        UUID otherUserId = saveUser("Other Owner");
        store.store(userId, "image/png", png());
        store.store(userId, "image/png", png());
        PhotoStore.ManagedPhoto kept = store.store(otherUserId, "image/png", png());

        store.deleteAllForUser(userId);

        assertEquals(1, photoRowCount());
        assertTrue(store.exists(otherUserId, kept.id() + ".png"));
    }

    @Test
    @DisplayName("an upload the disk store would reject is rejected here too and nothing is stored")
    void invalidUploadsAreRejected() throws Exception {
        byte[] png = png();

        assertThrows(IllegalArgumentException.class, () -> store.store(userId, "image/jpeg", png));
        assertThrows(IllegalArgumentException.class, () -> store.store(userId, "image/gif", png));
        assertThrows(IllegalArgumentException.class, () -> store.store(userId, "image/png", new byte[0]));

        assertEquals(0, photoRowCount());
    }

    @Test
    @DisplayName("unsafe file names find nothing and a malformed managed path is refused")
    void unsafeNamesAndPathsAreRefused() throws Exception {
        PhotoStore.ManagedPhoto stored = store.store(userId, "image/png", png());

        assertTrue(store.load(userId, "../" + stored.id() + ".png").isEmpty());
        assertFalse(store.exists(userId, "a/b.png"));
        assertTrue(store.load(UUID.randomUUID(), stored.id() + ".png").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.delete("/photos/not-a-uuid/photo.png"));
        assertThrows(IllegalArgumentException.class, () -> store.delete("/photos/" + userId + "/a/b.png"));
        assertEquals(1, photoRowCount());
    }

    private UUID saveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        services.getUserStorage().save(user);
        return user.getId();
    }

    private int photoRowCount() throws Exception {
        try (Connection conn = dbManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM photo_blobs")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /** A real JPEG with an APP1 "Exif" segment spliced in right after the start-of-image marker. */
    private static byte[] jpegWithExifSegment() throws Exception {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", plain);
        byte[] jpeg = plain.toByteArray();

        byte[] payload = ("Exif\0\0" + EXIF_MARKER_TEXT).getBytes(StandardCharsets.ISO_8859_1);
        int segmentLength = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((segmentLength >> 8) & 0xFF);
        out.write(segmentLength & 0xFF);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static boolean contains(byte[] bytes, String text) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(text);
    }
}
