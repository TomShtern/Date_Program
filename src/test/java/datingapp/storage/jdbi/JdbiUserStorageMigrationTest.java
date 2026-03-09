package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.storage.DatabaseManager;
import java.sql.Timestamp;
import java.time.Instant;
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

    @BeforeEach
    void setUp() {
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
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });

        storage = new JdbiUserStorage(jdbi);
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("save dual-writes normalized tables and reads prefer normalized values")
    void saveDualWritesAndReadPrefersNormalizedValues() {
        User user = createUser();
        storage.save(user);

        assertEquals(user.getPhotoUrls(), storage.loadUserPhotos(userId));
        assertEquals(Set.of("MUSIC", "TRAVEL"), storage.loadUserInterests(userId));
        assertEquals(Set.of("FEMALE"), storage.loadUserInterestedIn(userId));

        jdbi.useHandle(handle -> {
            handle.execute(
                    "UPDATE users SET photo_urls = ?, interests = ?, interested_in = ?, db_smoking = ? WHERE id = ?",
                    "[\"legacy-photo.png\"]",
                    "[\"COOKING\"]",
                    "[\"MALE\"]",
                    "SOMETIMES",
                    userId);
        });

        User loaded = storage.get(userId).orElseThrow();
        assertEquals(user.getPhotoUrls(), loaded.getPhotoUrls());
        assertEquals(EnumSet.of(Interest.MUSIC, Interest.TRAVEL), loaded.getInterests());
        assertEquals(EnumSet.of(Gender.FEMALE), loaded.getInterestedIn());
        assertEquals(Set.of(Lifestyle.Smoking.NEVER), loaded.getDealbreakers().acceptableSmoking());
    }

    @Test
    @DisplayName("read falls back to legacy columns when normalized rows are absent")
    void readFallsBackToLegacyWhenNormalizedRowsAbsent() {
        User user = createUser();
        storage.save(user);

        jdbi.useHandle(handle -> {
            handle.execute("DELETE FROM user_photos WHERE user_id = ?", userId);
            handle.execute("DELETE FROM user_interests WHERE user_id = ?", userId);
            handle.execute("DELETE FROM user_interested_in WHERE user_id = ?", userId);
            handle.execute("DELETE FROM user_db_smoking WHERE user_id = ?", userId);
            handle.execute(
                    "UPDATE users SET photo_urls = ?, interests = ?, interested_in = ?, db_smoking = ? WHERE id = ?",
                    "[\"fallback-photo.png\"]",
                    "[\"COOKING\"]",
                    "[\"MALE\"]",
                    "SOMETIMES",
                    userId);
        });

        User loaded = storage.get(userId).orElseThrow();
        assertEquals(List.of("fallback-photo.png"), loaded.getPhotoUrls());
        assertEquals(EnumSet.of(Interest.COOKING), loaded.getInterests());
        assertEquals(EnumSet.of(Gender.MALE), loaded.getInterestedIn());
        assertEquals(
                Set.of(Lifestyle.Smoking.SOMETIMES), loaded.getDealbreakers().acceptableSmoking());
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

    private User createUser() {
        User user = new User(userId, "MigrationUser");
        user.setBirthDate(AppClock.today().minusYears(28));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(21, 40, 18, 120);
        user.setMaxDistanceKm(25, 500);
        user.setLocation(40.7128, -74.0060);
        user.setPhotoUrls(List.of("photo-a.png", "photo-b.png"));
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
