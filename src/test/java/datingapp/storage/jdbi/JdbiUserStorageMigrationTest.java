package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.storage.DatabaseManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
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

@Timeout(10)
@DisplayName("JdbiUserStorage normalized migration compatibility")
class JdbiUserStorageMigrationTest {

    private JdbiUserStorage storage;
    private Jdbi jdbi;
    private UUID userId;
    private static final String DB_PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        System.setProperty(DB_PROFILE_PROPERTY, "test");
        String dbName = "migrationdb_" + UUID.randomUUID().toString().replace("-", "");
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
        JdbiTypeCodecs.registerInstantCodec(jdbi);

        storage = new JdbiUserStorage(jdbi);
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(DB_PROFILE_PROPERTY);
        AppClock.reset();
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("save writes normalized tables and users table no longer exposes legacy serialized columns")
    void saveWritesNormalizedTablesAndDropsLegacyColumns() {
        User user = createUser();
        storage.save(user);

        assertEquals(user.getPhotoUrls(), storage.loadUserPhotos(userId));
        assertEquals(Set.of("MUSIC", "TRAVEL"), storage.loadUserInterests(userId));
        assertEquals(Set.of("FEMALE"), storage.loadUserInterestedIn(userId));

        User loaded = storage.get(userId).orElseThrow();
        assertEquals(user.getPhotoUrls(), loaded.getPhotoUrls());
        assertEquals(EnumSet.of(Interest.MUSIC, Interest.TRAVEL), loaded.getInterests());
        assertEquals(EnumSet.of(Gender.FEMALE), loaded.getInterestedIn());
        assertEquals(Set.of(Lifestyle.Smoking.NEVER), loaded.getDealbreakers().acceptableSmoking());

        jdbi.useHandle(handle -> {
            var columns = handle.createQuery("""
                            SELECT column_name
                            FROM information_schema.columns
                            WHERE table_name = 'USERS'
                            """).mapTo(String.class).list();

            assertFalse(columns.contains("PHOTO_URLS"));
            assertFalse(columns.contains("INTERESTS"));
            assertFalse(columns.contains("INTERESTED_IN"));
            assertFalse(columns.contains("DB_SMOKING"));
            assertFalse(columns.contains("DB_DRINKING"));
            assertFalse(columns.contains("DB_WANTS_KIDS"));
            assertFalse(columns.contains("DB_LOOKING_FOR"));
            assertFalse(columns.contains("DB_EDUCATION"));
        });
    }

    @Test
    @DisplayName("findCandidates binds gender filters as SQL values")
    void findCandidatesBindsGenderFiltersAsSqlValues() {
        User seeker = createUser();
        storage.save(seeker);

        User femaleCandidate = createCandidate("CandidateFemale", Gender.FEMALE, 40.7228, -74.0060);
        User maleCandidate = createCandidate("CandidateMale", Gender.MALE, 40.7328, -74.0060);
        storage.save(femaleCandidate);
        storage.save(maleCandidate);

        List<User> results = storage.findCandidates(userId, EnumSet.of(Gender.FEMALE), 21, 40, 40.7128, -74.0060, 25);

        assertEquals(1, results.size());
        assertEquals(femaleCandidate.getId(), results.getFirst().getId());
    }

    @Test
    @DisplayName("findCandidates uses exact UTC age boundaries rather than year-difference shortcuts")
    void findCandidatesUsesExactUtcAgeBoundaries() {
        AppClock.setFixed(Instant.parse("2026-03-25T12:00:00Z"));

        User seeker = createUser();
        seeker.setAgeRange(26, 26, 18, 120);
        storage.save(seeker);

        User borderlineCandidate = createCandidate("BorderlineCandidate", Gender.FEMALE, 40.7228, -74.0060);
        borderlineCandidate.setBirthDate(LocalDate.of(2000, 3, 26));
        storage.save(borderlineCandidate);

        List<User> results = storage.findCandidates(userId, EnumSet.of(Gender.FEMALE), 26, 26, 40.7128, -74.0060, 25);

        assertEquals(0, results.size());
    }

    private User createUser() {
        User user = new User(userId, "MigrationUser");
        user.setBirthDate(AppClock.today().minusYears(28));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(21, 40, 18, 120);
        user.setMaxDistanceKm(25, 500);
        user.setLocation(40.7128, -74.0060);
        user.setPhotoUrls(List.of("https://example.com/photo-a.png", "https://example.com/photo-b.png"));
        user.setInterests(EnumSet.of(Interest.MUSIC, Interest.TRAVEL));
        user.setDealbreakers(
                Dealbreakers.builder().acceptSmoking(Lifestyle.Smoking.NEVER).build());
        return user;
    }

    private static User createCandidate(String name, Gender gender, double lat, double lon) {
        return User.StorageBuilder.create(UUID.randomUUID(), name, AppClock.now())
                .birthDate(AppClock.today().minusYears(27))
                .gender(gender)
                .interestedIn(EnumSet.of(Gender.MALE, Gender.FEMALE))
                .maxDistanceKm(25)
                .ageRange(21, 40)
                .location(lat, lon)
                .hasLocationSet(true)
                .state(User.UserState.ACTIVE)
                .build();
    }
}
