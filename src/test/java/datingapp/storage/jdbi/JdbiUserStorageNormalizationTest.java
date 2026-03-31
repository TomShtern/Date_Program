package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.storage.PageData;
import datingapp.core.testutil.TestClock;
import datingapp.storage.DatabaseManager;
import datingapp.storage.DevDataSeeder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

    private static final String SUBJECT_USER_NAME = "SubjectUser";
    private static final String ACTIVE_OLD_PHOTO_URL = "https://example.com/active-old.jpg";
    private static final String ACTIVE_NEW_PHOTO_URL = "https://example.com/active-new.jpg";
    private static final String PAUSED_NEWEST_PHOTO_URL = "https://example.com/paused-newest.jpg";
    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private JdbiUserStorage storage;
    private Jdbi jdbi;
    private CountingConnectionFactory connectionFactory;
    private UUID userId;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        DatabaseManager dbManager = DatabaseManager.getInstance();

        connectionFactory = new CountingConnectionFactory(dbManager);

        jdbi = Jdbi.create(connectionFactory::open).installPlugin(new SqlObjectPlugin());

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
        connectionFactory.reset();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        TestClock.reset();
        storage.clearCache();
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("get caches repeated reads until invalidated")
    void getCachesRepeatedReadsUntilInvalidated() {
        storage.clearCache();
        connectionFactory.reset();

        User first = storage.get(userId).orElseThrow();
        int afterFirstRead = connectionFactory.statementCount();

        User second = storage.get(userId).orElseThrow();

        assertEquals(afterFirstRead, connectionFactory.statementCount());
        assertNotSame(first, second);
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getName(), second.getName());
    }

    @Test
    @DisplayName("save invalidates cached user reads")
    void saveInvalidatesCachedUserReads() {
        storage.clearCache();
        connectionFactory.reset();

        User cached = storage.get(userId).orElseThrow();
        cached.setBio("Updated bio");
        storage.save(cached);

        connectionFactory.reset();

        User reloaded = storage.get(userId).orElseThrow();

        assertEquals("Updated bio", reloaded.getBio());
        assertTrue(connectionFactory.statementCount() > 0);
    }

    @Test
    @DisplayName("get expires cached users after the TTL")
    void getExpiresCachedUsersAfterTtl() {
        storage.clearCache();
        Instant now = Instant.parse("2026-03-24T12:00:00Z");
        TestClock.setFixed(now);
        connectionFactory.reset();

        User first = storage.get(userId).orElseThrow();
        assertTrue(connectionFactory.statementCount() > 0);

        TestClock.setFixed(now.plus(Duration.ofMinutes(6)));
        connectionFactory.reset();

        User second = storage.get(userId).orElseThrow();

        assertEquals(first.getName(), second.getName());
        assertTrue(connectionFactory.statementCount() > 0);
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
    @DisplayName("findByIds batches normalized reads and preserves profile data")
    void findByIdsBatchesNormalizedReadsAndPreservesProfileData() {
        User first = createActiveUser(UUID.randomUUID(), "BatchOne", Gender.FEMALE, Set.of(Gender.MALE), true);
        first.setPhotoUrls(List.of("https://example.com/one-a.jpg", "https://example.com/one-b.jpg"));
        first.setInterests(Set.of(Interest.MUSIC, Interest.TRAVEL));
        first.setDealbreakers(Dealbreakers.builder()
                .minHeight(165)
                .maxHeight(185)
                .acceptSmoking(Lifestyle.Smoking.NEVER)
                .acceptDrinking(Lifestyle.Drinking.SOCIALLY)
                .build());

        User second = createActiveUser(UUID.randomUUID(), "BatchTwo", Gender.MALE, Set.of(Gender.FEMALE), true);
        second.setPhotoUrls(List.of("https://example.com/two-a.jpg"));
        second.setInterests(Set.of(Interest.COOKING));
        second.setDealbreakers(Dealbreakers.builder()
                .maxAgeDifference(4)
                .acceptKidsStance(Lifestyle.WantsKids.OPEN)
                .requireEducation(Lifestyle.Education.BACHELORS)
                .build());

        storage.save(first);
        storage.save(second);
        connectionFactory.reset();

        Map<UUID, User> loaded = storage.findByIds(Set.of(first.getId(), second.getId()));

        assertEquals(2, connectionFactory.statementCount());
        assertEquals(2, loaded.size());

        User loadedFirst = loaded.get(first.getId());
        assertNotNull(loadedFirst);
        assertEquals(first.getPhotoUrls(), loadedFirst.getPhotoUrls());
        assertEquals(first.getInterests(), loadedFirst.getInterests());
        assertEquals(first.getInterestedIn(), loadedFirst.getInterestedIn());
        assertEquals(165, loadedFirst.getDealbreakers().minHeightCm());
        assertEquals(185, loadedFirst.getDealbreakers().maxHeightCm());
        assertTrue(loadedFirst.getDealbreakers().acceptableSmoking().contains(Lifestyle.Smoking.NEVER));

        User loadedSecond = loaded.get(second.getId());
        assertNotNull(loadedSecond);
        assertEquals(second.getPhotoUrls(), loadedSecond.getPhotoUrls());
        assertEquals(second.getInterests(), loadedSecond.getInterests());
        assertEquals(second.getInterestedIn(), loadedSecond.getInterestedIn());
        assertEquals(4, loadedSecond.getDealbreakers().maxAgeDifference());
        assertTrue(loadedSecond.getDealbreakers().acceptableKidsStance().contains(Lifestyle.WantsKids.OPEN));
    }

    @Test
    @DisplayName("findCandidates hydrates normalized profile data for matching results")
    void findCandidatesHydratesNormalizedProfileDataForMatchingResults() {
        UUID seekerId = UUID.randomUUID();
        User seeker = createActiveUser(seekerId, "Seeker", Gender.MALE, Set.of(Gender.FEMALE), true);

        User candidate = createActiveUser(UUID.randomUUID(), "Candidate", Gender.FEMALE, Set.of(Gender.MALE), true);
        candidate.setPhotoUrls(List.of("https://example.com/candidate-a.jpg", "https://example.com/candidate-b.jpg"));
        candidate.setInterests(Set.of(Interest.MUSIC, Interest.TRAVEL));
        candidate.setDealbreakers(Dealbreakers.builder()
                .acceptSmoking(Lifestyle.Smoking.NEVER)
                .acceptDrinking(Lifestyle.Drinking.SOCIALLY)
                .requireEducation(Lifestyle.Education.BACHELORS)
                .build());

        User wrongGender = createActiveUser(UUID.randomUUID(), "WrongGender", Gender.MALE, Set.of(Gender.FEMALE), true);
        wrongGender.setPhotoUrls(List.of("https://example.com/wrong-gender.jpg"));

        storage.save(seeker);
        storage.save(candidate);
        storage.save(wrongGender);

        List<User> candidates = storage.findCandidates(seekerId, Set.of(Gender.FEMALE), 18, 99, 32.0853, 34.7818, 10);

        assertEquals(1, candidates.size());
        User loadedCandidate = candidates.getFirst();
        assertEquals(candidate.getId(), loadedCandidate.getId());
        assertEquals(candidate.getPhotoUrls(), loadedCandidate.getPhotoUrls());
        assertEquals(candidate.getInterests(), loadedCandidate.getInterests());
        assertEquals(candidate.getInterestedIn(), loadedCandidate.getInterestedIn());
        assertTrue(loadedCandidate.getDealbreakers().acceptableSmoking().contains(Lifestyle.Smoking.NEVER));
        assertTrue(loadedCandidate.getDealbreakers().acceptableDrinking().contains(Lifestyle.Drinking.SOCIALLY));
        assertTrue(loadedCandidate.getDealbreakers().acceptableEducation().contains(Lifestyle.Education.BACHELORS));
    }

    @Test
    @DisplayName("findActive hydrates normalized profile data for multiple users")
    void findActiveHydratesNormalizedProfileDataForMultipleUsers() {
        User first = createActiveUser(UUID.randomUUID(), "ActiveOne", Gender.FEMALE, Set.of(Gender.MALE), true);
        first.setPhotoUrls(List.of("https://example.com/active-one.jpg"));
        first.setInterests(Set.of(Interest.MUSIC));
        first.setDealbreakers(
                Dealbreakers.builder().acceptSmoking(Lifestyle.Smoking.NEVER).build());

        User second = createActiveUser(UUID.randomUUID(), "ActiveTwo", Gender.MALE, Set.of(Gender.FEMALE), true);
        second.setPhotoUrls(List.of("https://example.com/active-two-a.jpg", "https://example.com/active-two-b.jpg"));
        second.setInterests(Set.of(Interest.TRAVEL, Interest.COOKING));
        second.setDealbreakers(Dealbreakers.builder()
                .acceptDrinking(Lifestyle.Drinking.SOCIALLY)
                .requireEducation(Lifestyle.Education.MASTERS)
                .build());

        storage.save(first);
        storage.save(second);

        List<User> activeUsers = storage.findActive();

        assertEquals(2, activeUsers.size());
        assertTrue(activeUsers.stream()
                .anyMatch(user -> user.getId().equals(first.getId())
                        && user.getPhotoUrls().equals(first.getPhotoUrls())
                        && user.getInterests().equals(first.getInterests())
                        && user.getDealbreakers().acceptableSmoking().contains(Lifestyle.Smoking.NEVER)));
        assertTrue(activeUsers.stream()
                .anyMatch(user -> user.getId().equals(second.getId())
                        && user.getPhotoUrls().equals(second.getPhotoUrls())
                        && user.getInterests().equals(second.getInterests())
                        && user.getDealbreakers().acceptableDrinking().contains(Lifestyle.Drinking.SOCIALLY)));
    }

    @Test
    @DisplayName("getPageOfActiveUsers applies SQL paging and normalized enrichment")
    void getPageOfActiveUsersAppliesSqlPagingAndNormalizedEnrichment() {
        Instant base = Instant.parse("2026-03-25T10:00:00Z");

        User oldest = createPagedUser(
                UUID.randomUUID(), "ActiveOld", Gender.FEMALE, Set.of(Gender.MALE), UserState.ACTIVE, base, true);
        oldest.setPhotoUrls(List.of(ACTIVE_OLD_PHOTO_URL));
        oldest.setInterests(Set.of(Interest.COOKING));

        User middle = createPagedUser(
                UUID.randomUUID(),
                "ActiveMid",
                Gender.MALE,
                Set.of(Gender.FEMALE),
                UserState.ACTIVE,
                base.plusSeconds(60),
                true);
        middle.setPhotoUrls(List.of("https://example.com/active-mid-a.jpg", "https://example.com/active-mid-b.jpg"));
        middle.setInterests(Set.of(Interest.MUSIC, Interest.TRAVEL));
        middle.setDealbreakers(
                Dealbreakers.builder().acceptSmoking(Lifestyle.Smoking.NEVER).build());

        User newestActive = createPagedUser(
                UUID.randomUUID(),
                "ActiveNew",
                Gender.OTHER,
                Set.of(Gender.MALE),
                UserState.ACTIVE,
                base.plusSeconds(120),
                true);
        newestActive.setPhotoUrls(List.of(ACTIVE_NEW_PHOTO_URL));
        newestActive.setInterests(Set.of(Interest.GYM));

        User newestButInactive = createPagedUser(
                UUID.randomUUID(),
                "PausedNewest",
                Gender.FEMALE,
                Set.of(Gender.MALE),
                UserState.PAUSED,
                base.plusSeconds(180),
                true);
        newestButInactive.setPhotoUrls(List.of(PAUSED_NEWEST_PHOTO_URL));
        newestButInactive.setInterests(Set.of(Interest.READING));

        storage.save(oldest);
        storage.save(middle);
        storage.save(newestActive);
        storage.save(newestButInactive);

        PageData<User> page = storage.getPageOfActiveUsers(1, 10);

        assertEquals(3, page.totalCount());
        assertEquals(1, page.offset());
        assertEquals(10, page.limit());
        assertEquals(List.of(middle, oldest), page.items());
        assertEquals(
                List.of("https://example.com/active-mid-a.jpg", "https://example.com/active-mid-b.jpg"),
                page.items().get(0).getPhotoUrls());
        assertEquals(
                Set.of(Interest.MUSIC, Interest.TRAVEL), page.items().get(0).getInterests());
        assertTrue(page.items().get(0).getDealbreakers().acceptableSmoking().contains(Lifestyle.Smoking.NEVER));
        assertEquals(List.of(ACTIVE_OLD_PHOTO_URL), page.items().get(1).getPhotoUrls());
        assertEquals(Set.of(Interest.COOKING), page.items().get(1).getInterests());
    }

    @Test
    @DisplayName("getPageOfAllUsers applies SQL paging and returns empty pages past the end")
    void getPageOfAllUsersAppliesSqlPagingAndReturnsEmptyPagesPastTheEnd() {
        int baselineTotalCount = storage.getPageOfAllUsers(0, 100).totalCount();
        Instant base = Instant.parse("2026-03-25T10:00:00Z");

        User newestInactive = createPagedUser(
                UUID.randomUUID(),
                "PausedNewest",
                Gender.FEMALE,
                Set.of(Gender.MALE),
                UserState.PAUSED,
                base.plusSeconds(180),
                true);
        newestInactive.setPhotoUrls(List.of(PAUSED_NEWEST_PHOTO_URL));
        newestInactive.setInterests(Set.of(Interest.READING));

        User newestActive = createPagedUser(
                UUID.randomUUID(),
                "ActiveNew",
                Gender.OTHER,
                Set.of(Gender.FEMALE),
                UserState.ACTIVE,
                base.plusSeconds(120),
                true);
        newestActive.setPhotoUrls(List.of(ACTIVE_NEW_PHOTO_URL));
        newestActive.setInterests(Set.of(Interest.GYM));

        User middleActive = createPagedUser(
                UUID.randomUUID(),
                "ActiveMid",
                Gender.MALE,
                Set.of(Gender.FEMALE),
                UserState.ACTIVE,
                base.plusSeconds(60),
                true);
        middleActive.setPhotoUrls(List.of("https://example.com/active-mid.jpg"));
        middleActive.setInterests(Set.of(Interest.MUSIC));

        User oldestActive = createPagedUser(
                UUID.randomUUID(), "ActiveOld", Gender.FEMALE, Set.of(Gender.MALE), UserState.ACTIVE, base, true);
        oldestActive.setPhotoUrls(List.of(ACTIVE_OLD_PHOTO_URL));
        oldestActive.setInterests(Set.of(Interest.COOKING));

        storage.save(newestInactive);
        storage.save(newestActive);
        storage.save(middleActive);
        storage.save(oldestActive);

        PageData<User> page = storage.getPageOfAllUsers(baselineTotalCount, 2);

        int expectedTotalCount = baselineTotalCount + 4;
        assertEquals(expectedTotalCount, page.totalCount());
        assertEquals(baselineTotalCount, page.offset());
        assertEquals(2, page.limit());
        assertEquals(List.of(newestInactive, newestActive), page.items());
        assertEquals(List.of(PAUSED_NEWEST_PHOTO_URL), page.items().get(0).getPhotoUrls());
        assertEquals(Set.of(Interest.READING), page.items().get(0).getInterests());
        assertEquals(List.of(ACTIVE_NEW_PHOTO_URL), page.items().get(1).getPhotoUrls());
        assertEquals(Set.of(Interest.GYM), page.items().get(1).getInterests());

        PageData<User> empty = storage.getPageOfAllUsers(expectedTotalCount, 10);

        assertTrue(empty.isEmpty());
        assertEquals(expectedTotalCount, empty.totalCount());
        assertEquals(0, empty.offset());
        assertEquals(10, empty.limit());
    }

    @Test
    @DisplayName("getPage methods reject invalid arguments")
    void getPageMethodsRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> storage.getPageOfActiveUsers(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> storage.getPageOfActiveUsers(0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.getPageOfAllUsers(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> storage.getPageOfAllUsers(0, -1));
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
        storage.save(new User(subjectId, SUBJECT_USER_NAME));

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
        storage.save(new User(subjectId, SUBJECT_USER_NAME));

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

        String deletedAtSql = """
            SELECT deleted_at
            FROM profile_notes
            WHERE author_id = :authorId
              AND subject_id = :subjectId
            """;

        Instant deletedAt = jdbi.withHandle(handle -> handle.createQuery(deletedAtSql)
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
        storage.save(new User(subjectId, SUBJECT_USER_NAME));

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

    @Test
    @DisplayName("executeWithUserLock supports get and save within lock context")
    void executeWithUserLockSupportsGetAndSaveWithinLock() {
        // Create and save a user
        User original = new User(userId, "TestUser");
        original.setBio("Original bio");
        storage.save(original);

        // Execute within lock: get user, modify, and save
        storage.executeWithUserLock(userId, () -> {
            // Get within lock
            User locked = storage.get(userId).orElseThrow(() -> new RuntimeException("User not found"));

            // Modify within lock
            locked.setBio("Modified bio");

            // Save within lock (reuses same transaction)
            storage.save(locked);
        });

        // Verify that the modification was persisted
        User reloaded = storage.get(userId).orElseThrow();
        assertEquals("Modified bio", reloaded.getBio(), "Bio should be persisted after lock-based modification");
    }

    private static User createActiveUser(
            UUID id, String name, Gender gender, Set<Gender> interestedIn, boolean withLocation) {
        return createActiveUser(id, name, gender, interestedIn, withLocation, Instant.now());
    }

    private static User createPagedUser(
            UUID id,
            String name,
            Gender gender,
            Set<Gender> interestedIn,
            UserState state,
            Instant createdAt,
            boolean withLocation) {
        return createUser(id, name, gender, interestedIn, withLocation, createdAt, state);
    }

    private static User createActiveUser(
            UUID id, String name, Gender gender, Set<Gender> interestedIn, boolean withLocation, Instant createdAt) {
        return createUser(id, name, gender, interestedIn, withLocation, createdAt, UserState.ACTIVE);
    }

    private static User createUser(
            UUID id,
            String name,
            Gender gender,
            Set<Gender> interestedIn,
            boolean withLocation,
            Instant createdAt,
            UserState state) {
        User.StorageBuilder builder = User.StorageBuilder.create(id, name, createdAt)
                .birthDate(LocalDate.now().minusYears(28))
                .gender(gender)
                .interestedIn(interestedIn)
                .maxDistanceKm(500)
                .ageRange(18, 99)
                .state(state)
                .updatedAt(createdAt);

        if (withLocation) {
            builder.location(32.0853, 34.7818).hasLocationSet(true);
        } else {
            builder.location(0.0, 0.0).hasLocationSet(false);
        }

        return builder.build();
    }

    private static final class CountingConnectionFactory {
        private final DatabaseManager dbManager;
        private final AtomicInteger statementCount = new AtomicInteger();

        private CountingConnectionFactory(DatabaseManager dbManager) {
            this.dbManager = dbManager;
        }

        Connection open() {
            try {
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        new CountingConnectionHandler(dbManager.getConnection()));
            } catch (java.sql.SQLException e) {
                throw new DatabaseManager.StorageException("Failed to get database connection", e);
            }
        }

        void reset() {
            statementCount.set(0);
        }

        int statementCount() {
            return statementCount.get();
        }

        private final class CountingConnectionHandler implements InvocationHandler {
            private final Connection delegate;

            private CountingConnectionHandler(Connection delegate) {
                this.delegate = delegate;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String methodName = method.getName();
                if (methodName.startsWith("prepareStatement")
                        || methodName.startsWith("prepareCall")
                        || methodName.equals("createStatement")) {
                    statementCount.incrementAndGet();
                }

                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
    }
}
