package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.storage.DatabaseManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
@DisplayName("JdbiMatchmakingStorage atomic transition rollback")
class JdbiMatchmakingStorageTransitionAtomicityTest {

    private DatabaseManager dbManager;
    private JdbiMatchmakingStorage interactionStorage;
    private JdbiUserStorage userStorage;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

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

        interactionStorage = new JdbiMatchmakingStorage(jdbi);
        userStorage = new JdbiUserStorage(jdbi);

        User a = new User(UUID.randomUUID(), "Atomicity-A");
        User b = new User(UUID.randomUUID(), "Atomicity-B");
        userStorage.save(a);
        userStorage.save(b);

        userA = a.getId();
        userB = b.getId();
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Test
    @DisplayName("acceptFriendZoneTransition rolls back match update when request update fails")
    void acceptFriendZoneTransitionRollsBackMatchUpdateWhenRequestUpdateFails() {
        Match match = Match.create(userA, userB);
        interactionStorage.save(match);

        match.transitionToFriends(userA);
        FriendRequest nonExistentAcceptedRequest = new FriendRequest(
                UUID.randomUUID(), userA, userB, Instant.now(), FriendRequest.Status.ACCEPTED, Instant.now());

        assertThrows(
                DatabaseManager.StorageException.class,
                () -> interactionStorage.acceptFriendZoneTransition(match, nonExistentAcceptedRequest, null));

        Match persisted = interactionStorage.get(match.getId()).orElseThrow();
        assertEquals(MatchState.ACTIVE, persisted.getState());
    }

    @Test
    @DisplayName("gracefulExitTransition rolls back match update when conversation archive fails")
    void gracefulExitTransitionRollsBackMatchUpdateWhenConversationArchiveFails() {
        Match match = Match.create(userA, userB);
        interactionStorage.save(match);

        match.gracefulExit(userA);

        Conversation missingConversation = Conversation.create(userA, userB);
        missingConversation.archive(missingConversation.getUserA(), MatchArchiveReason.GRACEFUL_EXIT);
        missingConversation.archive(missingConversation.getUserB(), MatchArchiveReason.GRACEFUL_EXIT);

        assertThrows(
                DatabaseManager.StorageException.class,
                () -> interactionStorage.gracefulExitTransition(match, Optional.of(missingConversation), null));

        Match persisted = interactionStorage.get(match.getId()).orElseThrow();
        assertEquals(MatchState.ACTIVE, persisted.getState());
    }

    @Test
    @DisplayName("unmatchTransition rolls back match update when conversation archive fails")
    void unmatchTransitionRollsBackMatchUpdateWhenConversationArchiveFails() {
        Match match = Match.create(userA, userB);
        interactionStorage.save(match);

        match.unmatch(userA);

        Conversation missingConversation = Conversation.create(userA, userB);
        missingConversation.archive(missingConversation.getUserA(), MatchArchiveReason.UNMATCH);
        missingConversation.archive(missingConversation.getUserB(), MatchArchiveReason.UNMATCH);

        assertThrows(
                DatabaseManager.StorageException.class,
                () -> interactionStorage.unmatchTransition(match, Optional.of(missingConversation)));

        Match persisted = interactionStorage.get(match.getId()).orElseThrow();
        assertEquals(MatchState.ACTIVE, persisted.getState());
        assertTrue(persisted.getEndedBy() == null || !persisted.getEndedBy().equals(userA));
    }
}
