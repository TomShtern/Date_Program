package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.storage.DatabaseManager;
import datingapp.storage.DevDataSeeder;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
    private Jdbi jdbi;
    private UUID userId;

    @BeforeEach
    void setUp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        DatabaseManager dbManager = DatabaseManager.getInstance();

        jdbi = Jdbi.create(() -> {
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
    @DisplayName("should merge scalar and normalized dealbreaker sources on read")
    void mergesScalarAndNormalizedDealbreakerSources() {
        User user = storage.get(userId).orElseThrow();
        user.setDealbreakers(Dealbreakers.builder()
                .minHeight(165)
                .maxHeight(185)
                .maxAgeDifference(7)
                .acceptSmoking(Lifestyle.Smoking.NEVER)
                .acceptDrinking(Lifestyle.Drinking.SOCIALLY)
                .build());
        storage.save(user);

        User loaded = storage.get(userId).orElseThrow();
        Dealbreakers db = loaded.getDealbreakers();
        assertEquals(165, db.minHeightCm());
        assertEquals(185, db.maxHeightCm());
        assertEquals(7, db.maxAgeDifference());
        assertTrue(db.acceptableSmoking().contains(Lifestyle.Smoking.NEVER));
        assertTrue(db.acceptableDrinking().contains(Lifestyle.Drinking.SOCIALLY));
    }

    @Test
    @DisplayName("findCandidates keeps DB-side location narrowing even at very large radius")
    void findCandidatesKeepsLocationNarrowingAtVeryLargeRadius() {
        UUID seekerId = UUID.randomUUID();
        UUID withLocationId = UUID.randomUUID();
        UUID withoutLocationId = UUID.randomUUID();

        storage.save(createActiveUser(seekerId, "Seeker", Gender.MALE, Set.of(Gender.FEMALE), true));
        storage.save(createActiveUser(withLocationId, "Located", Gender.FEMALE, Set.of(Gender.MALE), true));
        storage.save(createActiveUser(withoutLocationId, "NoLocation", Gender.FEMALE, Set.of(Gender.MALE), false));

        var candidates = storage.findCandidates(seekerId, Set.of(Gender.FEMALE), 18, 99, 32.0853, 34.7818, 100_000);

        assertTrue(candidates.stream().anyMatch(user -> user.getId().equals(withLocationId)));
        assertTrue(candidates.stream().noneMatch(user -> user.getId().equals(withoutLocationId)));
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

    @Test
    @DisplayName("seeded sentinel user should have multiple randomuser portraits")
    void seededSentinelUserHasMultipleRandomuserPortraits() {
        DevDataSeeder.seed(storage);

        UUID sentinelId = UUID.fromString("11111111-1111-1111-1111-000000000001");
        List<String> photos = storage.loadUserPhotos(sentinelId);

        assertTrue(photos.size() >= 2);
        assertTrue(photos.stream().allMatch(url -> url.startsWith("https://randomuser.me/api/portraits/")));
    }

    @Test
    @DisplayName("should soft-delete profile note and hide from queries")
    void shouldSoftDeleteProfileNote() {
        UUID subjectId = UUID.randomUUID();
        storage.save(new User(subjectId, "SubjectUser"));

        // Save a profile note
        ProfileNote note = ProfileNote.create(userId, subjectId, "Test note");
        storage.saveProfileNote(note);

        // Verify note is visible before delete
        assertTrue(storage.getProfileNote(userId, subjectId).isPresent());
        assertEquals(1, storage.getProfileNotesByAuthor(userId).size());

        // Soft-delete the profile note
        boolean deleted = storage.deleteProfileNote(userId, subjectId);
        assertTrue(deleted, "deleteProfileNote should return true when a row was deleted");

        // Verify note is no longer visible in queries (soft-deleted)
        assertTrue(storage.getProfileNote(userId, subjectId).isEmpty());
        assertTrue(storage.getProfileNotesByAuthor(userId).isEmpty());

        Instant deletedAt = jdbi.withHandle(handle -> handle.createQuery("""
                SELECT deleted_at
                FROM profile_notes
                WHERE author_id = :authorId
                  AND subject_id = :subjectId
                """)
                .bind("authorId", userId)
                .bind("subjectId", subjectId)
                .mapTo(Instant.class)
                .one());
        assertNotNull(deletedAt, "profile_notes.deleted_at should be set on soft delete");
    }

    @Test
    @DisplayName("should revive soft-deleted profile note when re-saved")
    void shouldReviveSoftDeletedProfileNoteOnResave() {
        UUID subjectId = UUID.randomUUID();
        storage.save(new User(subjectId, "SubjectUser"));

        // Save, delete, then re-save a profile note
        storage.saveProfileNote(ProfileNote.create(userId, subjectId, "Original note"));
        storage.deleteProfileNote(userId, subjectId);
        storage.saveProfileNote(ProfileNote.create(userId, subjectId, "Revived note"));

        // Verify the revived note is visible and has the new content
        assertTrue(storage.getProfileNote(userId, subjectId).isPresent());
        assertEquals(
                "Revived note",
                storage.getProfileNote(userId, subjectId)
                        .map(ProfileNote::content)
                        .orElseThrow());
        assertEquals(1, storage.getProfileNotesByAuthor(userId).size());
    }

    private static User createActiveUser(
            UUID id, String name, Gender gender, Set<Gender> interestedIn, boolean withLocation) {
        User.StorageBuilder builder = User.StorageBuilder.create(id, name, Instant.now())
                .birthDate(LocalDate.now().minusYears(28))
                .gender(gender)
                .interestedIn(interestedIn)
                .maxDistanceKm(500)
                .ageRange(18, 99)
                .state(UserState.ACTIVE)
                .updatedAt(Instant.now());

        if (withLocation) {
            builder.location(32.0853, 34.7818).hasLocationSet(true);
        } else {
            builder.location(0.0, 0.0).hasLocationSet(false);
        }

        return builder.build();
    }
}
