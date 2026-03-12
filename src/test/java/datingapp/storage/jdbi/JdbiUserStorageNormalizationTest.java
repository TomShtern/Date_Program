package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.storage.DatabaseManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for normalized table DAO methods in {@link JdbiUserStorage} (V3
 * schema).
 */
@Timeout(10)
class JdbiUserStorageNormalizationTest {

    private JdbiUserStorage storage;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        DatabaseManager dbManager = DatabaseManager.getInstance();

        Jdbi jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });

        storage = new JdbiUserStorage(jdbi);

        // Create a test user (required for FK constraints)
        userId = UUID.randomUUID();
        User user = new User(userId, "TestUser");
        storage.save(user);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("should save and load photos in order")
    void saveAndLoadPhotosRoundTrip() {
        List<String> urls =
                List.of("https://example.com/1.jpg", "https://example.com/2.jpg", "https://example.com/3.jpg");
        storage.saveUserPhotos(userId, urls);

        List<String> loaded = storage.loadUserPhotos(userId);
        assertEquals(urls, loaded);
    }

    @Test
    @DisplayName("should save and load interests")
    void saveAndLoadInterestsRoundTrip() {
        Set<String> interests = Set.of("MUSIC", "TRAVEL", "COOKING");
        storage.saveUserInterests(userId, interests);

        Set<String> loaded = storage.loadUserInterests(userId);
        assertEquals(interests, loaded);
    }

    @Test
    @DisplayName("should save and load gender preferences")
    void saveAndLoadInterestedInRoundTrip() {
        Set<String> genders = Set.of("MALE", "FEMALE");
        storage.saveUserInterestedIn(userId, genders);

        Set<String> loaded = storage.loadUserInterestedIn(userId);
        assertEquals(genders, loaded);
    }

    @Test
    @DisplayName("should save and load dealbreaker dimensions")
    void saveAndLoadDealbreakerDimensionsRoundTrip() {
        Set<String> smoking = Set.of("NEVER", "SOCIALLY");
        Set<String> drinking = Set.of("SOCIALLY");
        Set<String> wantsKids = Set.of("YES", "OPEN_TO_IT");
        Set<String> lookingFor = Set.of("LONG_TERM");
        Set<String> education = Set.of("BACHELORS", "MASTERS");

        storage.saveDealbreaker(userId, "user_db_smoking", smoking);
        storage.saveDealbreaker(userId, "user_db_drinking", drinking);
        storage.saveDealbreaker(userId, "user_db_wants_kids", wantsKids);
        storage.saveDealbreaker(userId, "user_db_looking_for", lookingFor);
        storage.saveDealbreaker(userId, "user_db_education", education);

        assertEquals(smoking, storage.loadDealbreaker(userId, "user_db_smoking"));
        assertEquals(drinking, storage.loadDealbreaker(userId, "user_db_drinking"));
        assertEquals(wantsKids, storage.loadDealbreaker(userId, "user_db_wants_kids"));
        assertEquals(lookingFor, storage.loadDealbreaker(userId, "user_db_looking_for"));
        assertEquals(education, storage.loadDealbreaker(userId, "user_db_education"));
    }

    @Test
    @DisplayName("should replace photos on re-save")
    void savePhotosReplacesExisting() {
        storage.saveUserPhotos(userId, List.of("a.jpg", "b.jpg", "c.jpg"));
        storage.saveUserPhotos(userId, List.of("x.jpg", "y.jpg"));

        List<String> loaded = storage.loadUserPhotos(userId);
        assertEquals(List.of("x.jpg", "y.jpg"), loaded);
    }

    @Test
    @DisplayName("should return empty list/set for no data")
    void emptySetWritesNoRows() {
        storage.saveUserInterests(userId, Set.of());
        assertTrue(storage.loadUserInterests(userId).isEmpty());

        storage.saveUserPhotos(userId, List.of());
        assertTrue(storage.loadUserPhotos(userId).isEmpty());
    }

    @Test
    @DisplayName("should handle null values gracefully")
    void nullValuesReturnEmpty() {
        storage.saveUserPhotos(userId, null);
        assertTrue(storage.loadUserPhotos(userId).isEmpty());

        storage.saveUserInterests(userId, null);
        assertTrue(storage.loadUserInterests(userId).isEmpty());
    }

    @Test
    @DisplayName("should save and update profile notes with record binding")
    void saveAndUpdateProfileNoteRoundTrip() {
        UUID subjectId = UUID.randomUUID();
        storage.save(new User(subjectId, "SubjectUser"));

        storage.saveProfileNote(ProfileNote.create(userId, subjectId, "First note"));

        assertEquals(
                "First note",
                storage.getProfileNote(userId, subjectId)
                        .map(ProfileNote::content)
                        .orElseThrow());

        storage.saveProfileNote(ProfileNote.create(userId, subjectId, "Updated note"));

        assertEquals(
                "Updated note",
                storage.getProfileNote(userId, subjectId)
                        .map(ProfileNote::content)
                        .orElseThrow());
    }
}
