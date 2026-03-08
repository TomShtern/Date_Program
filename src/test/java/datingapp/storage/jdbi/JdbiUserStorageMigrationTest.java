package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.storage.DatabaseManager;
import datingapp.storage.schema.MigrationRunner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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

    @Test
    @DisplayName("V4 migration backfills normalized rows from legacy-only user data")
    void migrationBackfillsNormalizedRowsFromLegacyOnlyData() throws Exception {
        User user = createUser();
        storage.save(user);

        jdbi.useHandle(handle -> {
            handle.execute("DELETE FROM user_photos WHERE user_id = ?", userId);
            handle.execute("DELETE FROM user_interests WHERE user_id = ?", userId);
            handle.execute("DELETE FROM user_interested_in WHERE user_id = ?", userId);
            handle.execute("DELETE FROM user_db_smoking WHERE user_id = ?", userId);
            handle.execute("DELETE FROM schema_version WHERE version = 4");
        });

        jdbi.useHandle(handle -> {
            try (Statement stmt = handle.getConnection().createStatement()) {
                MigrationRunner.runAllPending(stmt);
            }
        });

        assertEquals(user.getPhotoUrls(), storage.loadUserPhotos(userId));
        assertEquals(Set.of("MUSIC", "TRAVEL"), storage.loadUserInterests(userId));
        assertEquals(Set.of("FEMALE"), storage.loadUserInterestedIn(userId));
        assertTrue(storage.loadDealbreaker(userId, "user_db_smoking").contains("NEVER"));
    }

    @Test
    @DisplayName("V5 migration backfills missing users columns for databases that already recorded older migrations")
    void migrationBackfillsMissingUsersColumnsForOlderRecordedSchemas() throws Exception {
        String dbName = "migrationdb_stale_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement stmt = connection.createStatement()) {
            MigrationRunner.runAllPending(stmt);

            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS has_location_set");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS pace_messaging_frequency");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS pace_time_to_first_date");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS pace_communication_style");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS pace_depth_preference");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS deleted_at");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS email");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS phone");
            stmt.execute("DELETE FROM schema_version WHERE version = 5");

            assertTrue(!columnExists(stmt, "USERS", "HAS_LOCATION_SET"));
            assertTrue(!columnExists(stmt, "USERS", "PACE_MESSAGING_FREQUENCY"));

            MigrationRunner.runAllPending(stmt);

            assertTrue(columnExists(stmt, "USERS", "HAS_LOCATION_SET"));
            assertTrue(columnExists(stmt, "USERS", "PACE_MESSAGING_FREQUENCY"));
            assertTrue(columnExists(stmt, "USERS", "PACE_TIME_TO_FIRST_DATE"));
            assertTrue(columnExists(stmt, "USERS", "PACE_COMMUNICATION_STYLE"));
            assertTrue(columnExists(stmt, "USERS", "PACE_DEPTH_PREFERENCE"));
            assertTrue(columnExists(stmt, "USERS", "DELETED_AT"));
            assertTrue(columnExists(stmt, "USERS", "EMAIL"));
            assertTrue(columnExists(stmt, "USERS", "PHONE"));
        }
    }

    @Test
    @DisplayName("V6 migration backfills dashboard query columns for databases that already recorded V5")
    void migrationBackfillsDashboardProjectionColumnsForAlreadyV5Schemas() throws Exception {
        String dbName = "migrationdb_v5stale_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement stmt = connection.createStatement()) {
            MigrationRunner.runAllPending(stmt);

            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_smoking");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_drinking");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_wants_kids");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_looking_for");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_education");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_min_height_cm");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_max_height_cm");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS db_max_age_diff");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS is_verified");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS verification_method");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS verification_code");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS verification_sent_at");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS verified_at");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS pace_communication_style");
            stmt.execute("ALTER TABLE users DROP COLUMN IF EXISTS pace_depth_preference");
            stmt.execute("DELETE FROM schema_version WHERE version = 6");

            MigrationRunner.runAllPending(stmt);

            assertTrue(columnExists(stmt, "USERS", "DB_SMOKING"));
            assertTrue(columnExists(stmt, "USERS", "DB_DRINKING"));
            assertTrue(columnExists(stmt, "USERS", "DB_WANTS_KIDS"));
            assertTrue(columnExists(stmt, "USERS", "DB_LOOKING_FOR"));
            assertTrue(columnExists(stmt, "USERS", "DB_EDUCATION"));
            assertTrue(columnExists(stmt, "USERS", "DB_MIN_HEIGHT_CM"));
            assertTrue(columnExists(stmt, "USERS", "DB_MAX_HEIGHT_CM"));
            assertTrue(columnExists(stmt, "USERS", "DB_MAX_AGE_DIFF"));
            assertTrue(columnExists(stmt, "USERS", "IS_VERIFIED"));
            assertTrue(columnExists(stmt, "USERS", "VERIFICATION_METHOD"));
            assertTrue(columnExists(stmt, "USERS", "VERIFICATION_CODE"));
            assertTrue(columnExists(stmt, "USERS", "VERIFICATION_SENT_AT"));
            assertTrue(columnExists(stmt, "USERS", "VERIFIED_AT"));
            assertTrue(columnExists(stmt, "USERS", "PACE_COMMUNICATION_STYLE"));
            assertTrue(columnExists(stmt, "USERS", "PACE_DEPTH_PREFERENCE"));
            assertDoesNotThrow(() -> stmt.executeQuery("SELECT * FROM users WHERE 1 = 0"));
        }
    }

    private static boolean columnExists(Statement stmt, String tableName, String columnName) throws Exception {
        try (ResultSet rs = stmt.getConnection().getMetaData().getColumns(null, "PUBLIC", tableName, columnName)) {
            return rs.next();
        }
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
