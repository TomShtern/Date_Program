package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionModels.Report.Reason;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestUserFactory;
import datingapp.storage.DatabaseManager;
import datingapp.storage.schema.MigrationRunner;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
@DisplayName("JdbiAccountCleanupStorage")
class JdbiAccountCleanupStorageTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private DatabaseManager dbManager;
    private Jdbi jdbi;
    private UserStorage userStorage;
    private InteractionStorage interactionStorage;
    private CommunicationStorage communicationStorage;
    private JdbiTrustSafetyStorage trustSafetyStorage;
    private AccountCleanupStorage accountCleanupStorage;

    private User deletedUser;
    private User survivingUser;
    private Conversation conversation;
    private Like like;
    private Match match;
    private ProfileNote profileNote;
    private Message message;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        String dbName = "cleanupdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

        jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException _) {
                        throw new DatabaseManager.StorageException("Failed to get database connection");
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        JdbiTypeCodecs.registerInstantCodec(jdbi);

        jdbi.useHandle(handle -> {
            try (var stmt = handle.getConnection().createStatement()) {
                MigrationRunner.runAllPending(stmt);
            } catch (java.sql.SQLException _) {
                throw new DatabaseManager.StorageException("Failed to initialize schema");
            }
        });

        userStorage = new JdbiUserStorage(jdbi);
        interactionStorage = new JdbiMatchmakingStorage(jdbi);
        communicationStorage = new JdbiConnectionStorage(jdbi);
        trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);
        accountCleanupStorage = new JdbiAccountCleanupStorage(jdbi);

        deletedUser = TestUserFactory.createActiveUser(UUID.randomUUID(), "Cleanup Alice");
        survivingUser = TestUserFactory.createActiveUser(UUID.randomUUID(), "Cleanup Bob");
        userStorage.save(deletedUser);
        userStorage.save(survivingUser);

        like = Like.create(deletedUser.getId(), survivingUser.getId(), Like.Direction.LIKE);
        var likeWriteResult = interactionStorage.saveLikeAndMaybeCreateMatch(like);
        assertTrue(likeWriteResult.likePersisted());
        assertTrue(likeWriteResult.createdMatch().isEmpty());

        match = Match.create(deletedUser.getId(), survivingUser.getId());
        interactionStorage.save(match);

        conversation = Conversation.create(deletedUser.getId(), survivingUser.getId());
        communicationStorage.saveConversation(conversation);

        message = Message.create(conversation.getId(), deletedUser.getId(), "Cleanup message");
        communicationStorage.saveMessage(message);

        profileNote = ProfileNote.create(deletedUser.getId(), survivingUser.getId(), "Cleanup note");
        userStorage.saveProfileNote(profileNote);

        jdbi.useHandle(handle -> handle.createUpdate("""
            INSERT INTO undo_states (
                user_id, like_id, who_likes, who_got_liked, direction, like_created_at, match_id, expires_at
            ) VALUES (:userId, :likeId, :whoLikes, :whoGotLiked, :direction, :likeCreatedAt, NULL, :expiresAt)
            """)
                .bind("userId", deletedUser.getId())
                .bind("likeId", like.id())
                .bind("whoLikes", like.whoLikes())
                .bind("whoGotLiked", like.whoGotLiked())
                .bind("direction", like.direction().name())
                .bind("likeCreatedAt", Timestamp.from(like.createdAt()))
                .bind("expiresAt", Timestamp.from(AppClock.now().plusSeconds(3600)))
                .execute());

        UUID blockId = UUID.randomUUID();
        Timestamp blockCreatedAt = Timestamp.from(AppClock.now());
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO blocks (id, blocker_id, blocked_id, created_at)
                VALUES (:id, :blockerId, :blockedId, :createdAt)
                """)
                .bind("id", blockId)
                .bind("blockerId", deletedUser.getId())
                .bind("blockedId", survivingUser.getId())
                .bind("createdAt", blockCreatedAt)
                .execute());

        Report report = Report.create(deletedUser.getId(), survivingUser.getId(), Reason.HARASSMENT, "Cleanup report");
        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO reports (id, reporter_id, reported_user_id, reason, description, created_at)
                VALUES (:id, :reporterId, :reportedUserId, :reason, :description, :createdAt)
                """)
                .bind("id", report.id())
                .bind("reporterId", report.reporterId())
                .bind("reportedUserId", report.reportedUserId())
                .bind("reason", report.reason().name())
                .bind("description", report.description())
                .bind("createdAt", Timestamp.from(report.createdAt()))
                .execute());

        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO user_stats (id, user_id, computed_at)
                VALUES (:id, :userId, :computedAt)
                """)
                .bind("id", UUID.randomUUID())
                .bind("userId", deletedUser.getId())
                .bind("computedAt", Timestamp.from(AppClock.now()))
                .execute());

        jdbi.useHandle(handle -> handle.createUpdate("""
                INSERT INTO user_stats (id, user_id, computed_at)
                VALUES (:id, :userId, :computedAt)
                """)
                .bind("id", UUID.randomUUID())
                .bind("userId", survivingUser.getId())
                .bind("computedAt", Timestamp.from(AppClock.now()))
                .execute());

        jdbi.useHandle(handle -> handle.createUpdate("""
            INSERT INTO daily_picks (user_id, pick_date, picked_user_id, created_at)
            VALUES (:userId, :pickDate, :pickedUserId, :createdAt)
            """)
                .bind("userId", deletedUser.getId())
                .bind("pickDate", LocalDate.now())
                .bind("pickedUserId", survivingUser.getId())
                .bind("createdAt", Timestamp.from(AppClock.now()))
                .execute());

        jdbi.useHandle(handle -> handle.createUpdate("""
            INSERT INTO daily_picks (user_id, pick_date, picked_user_id, created_at)
            VALUES (:userId, :pickDate, :pickedUserId, :createdAt)
            """)
                .bind("userId", survivingUser.getId())
                .bind("pickDate", LocalDate.now())
                .bind("pickedUserId", deletedUser.getId())
                .bind("createdAt", Timestamp.from(AppClock.now()))
                .execute());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Test
    @DisplayName("softDeleteAccount soft-deletes the user graph and hides related rows")
    void softDeleteAccountSoftDeletesTheUserGraph() {
        assertDailyPickPreconditions();

        Instant deletedAt = AppClock.now();
        accountCleanupStorage.softDeleteAccount(deletedUser, deletedAt);

        assertTrue(userStorage.get(deletedUser.getId()).isEmpty());
        assertTrue(interactionStorage
                .getByUsers(deletedUser.getId(), survivingUser.getId())
                .isEmpty());
        assertEquals(0, interactionStorage.countMatchesFor(deletedUser.getId()));
        assertEquals(0, communicationStorage.countMessages(conversation.getId()));
        assertTrue(communicationStorage.getConversation(conversation.getId()).isEmpty());
        assertTrue(userStorage
                .getProfileNote(deletedUser.getId(), survivingUser.getId())
                .isEmpty());
        assertEquals(0, countUndoStates(deletedUser.getId()));
        assertFalse(trustSafetyStorage.isBlocked(deletedUser.getId(), survivingUser.getId()));
        assertFalse(trustSafetyStorage.hasReported(deletedUser.getId(), survivingUser.getId()));
        assertEquals(0, trustSafetyStorage.countBlocksGiven(deletedUser.getId()));
        assertEquals(0, trustSafetyStorage.countReportsBy(deletedUser.getId()));
        assertEquals(0, trustSafetyStorage.countReportsAgainst(survivingUser.getId()));
        assertEquals(0, countUserStatsRows(deletedUser.getId()));
        assertEquals(1, countUserStatsRows(survivingUser.getId()));
        assertDailyPicksRemovedForDeletedUserGraph();

        assertNotNull(rawDeletedAt("SELECT deleted_at FROM users WHERE id = :id", deletedUser.getId()));
        assertNotNull(rawDeletedAtForLike(
                "SELECT deleted_at FROM likes WHERE who_likes = :whoLikes AND who_got_liked = :whoGotLiked",
                like.whoLikes(),
                like.whoGotLiked()));
        assertNotNull(rawDeletedAt("SELECT deleted_at FROM matches WHERE id = :id", match.getId()));
        assertNotNull(rawDeletedAt("SELECT deleted_at FROM conversations WHERE id = :id", conversation.getId()));
        assertNotNull(rawDeletedAt("SELECT deleted_at FROM messages WHERE id = :id", message.id()));
        assertNotNull(rawDeletedAt(
                "SELECT deleted_at FROM profile_notes WHERE author_id = :authorId AND subject_id = :subjectId",
                deletedUser.getId(),
                survivingUser.getId()));
        assertNotNull(rawDeletedAt(
                "SELECT deleted_at FROM blocks WHERE blocker_id = :blockerId AND blocked_id = :blockedId",
                deletedUser.getId(),
                survivingUser.getId()));
        assertNotNull(rawDeletedAt(
                "SELECT deleted_at FROM reports WHERE reporter_id = :reporterId AND reported_user_id = :reportedUserId",
                deletedUser.getId(),
                survivingUser.getId()));
    }

    private Instant rawDeletedAt(String sql, UUID id) {
        return jdbi.withHandle(handle ->
                handle.createQuery(sql).bind("id", id).mapTo(Instant.class).one());
    }

    private Instant rawDeletedAt(String sql, String id) {
        return jdbi.withHandle(handle ->
                handle.createQuery(sql).bind("id", id).mapTo(Instant.class).one());
    }

    private Instant rawDeletedAt(String sql, UUID firstId, UUID secondId) {
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .bind("authorId", firstId)
                .bind("subjectId", secondId)
                .bind("blockerId", firstId)
                .bind("blockedId", secondId)
                .bind("reporterId", firstId)
                .bind("reportedUserId", secondId)
                .mapTo(Instant.class)
                .one());
    }

    private int countUndoStates(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM undo_states WHERE user_id = :userId")
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    private int countUserStatsRows(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM user_stats WHERE user_id = :userId")
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    private int countDailyPicksRows(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM daily_picks WHERE user_id = :userId")
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    private void assertDailyPickPreconditions() {
        assertEquals(1, countDailyPicksRows(deletedUser.getId()));
        assertEquals(1, countDailyPicksRows(survivingUser.getId()));
    }

    private void assertDailyPicksRemovedForDeletedUserGraph() {
        assertEquals(0, countDailyPicksRows(deletedUser.getId()));
        assertEquals(0, countDailyPicksRows(survivingUser.getId()));
    }

    private Instant rawDeletedAtForLike(String sql, UUID whoLikes, UUID whoGotLiked) {
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .bind("whoLikes", whoLikes)
                .bind("whoGotLiked", whoGotLiked)
                .mapTo(Instant.class)
                .one());
    }
}
