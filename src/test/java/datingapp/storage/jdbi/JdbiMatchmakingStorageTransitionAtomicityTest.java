package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.storage.DatabaseManager;
import java.time.Duration;
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

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private DatabaseManager dbManager;
    private JdbiMatchmakingStorage interactionStorage;
    private JdbiUserStorage userStorage;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
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
        JdbiTypeCodecs.registerInstantCodec(jdbi);

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
        System.clearProperty(PROFILE_PROPERTY);
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Test
    @DisplayName("saveLikeAndMaybeCreateMatch should persist created match with non-null updatedAt")
    void saveLikeAndMaybeCreateMatchPersistsCreatedMatchWithUpdatedAt() {
        Like firstLike = Like.create(userA, userB, Like.Direction.LIKE);
        Like secondLike = Like.create(userB, userA, Like.Direction.LIKE);

        var firstResult = interactionStorage.saveLikeAndMaybeCreateMatch(firstLike);
        assertTrue(firstResult.likePersisted());
        assertTrue(firstResult.createdMatch().isEmpty());

        var secondResult = interactionStorage.saveLikeAndMaybeCreateMatch(secondLike);
        assertTrue(secondResult.likePersisted());
        assertTrue(secondResult.createdMatch().isPresent());

        Match created = secondResult.createdMatch().orElseThrow();
        Match persisted = interactionStorage.get(created.getId()).orElseThrow();
        assertNotNull(persisted.getUpdatedAt());
        assertTrue(
                !persisted.getUpdatedAt().isBefore(persisted.getCreatedAt()),
                "Persisted updatedAt should be at least createdAt for newly created match");
    }

    @Test
    @DisplayName("saveLikeAndMaybeCreateMatch treats duplicate swipe as idempotent no-op")
    void saveLikeAndMaybeCreateMatchTreatsDuplicateSwipeAsIdempotentNoOp() {
        Like firstLike = Like.create(userA, userB, Like.Direction.LIKE);
        Like duplicateLike = Like.create(userA, userB, Like.Direction.LIKE);

        var firstResult = interactionStorage.saveLikeAndMaybeCreateMatch(firstLike);
        var duplicateResult = interactionStorage.saveLikeAndMaybeCreateMatch(duplicateLike);

        assertTrue(firstResult.likePersisted());
        assertTrue(firstResult.createdMatch().isEmpty());
        assertFalse(duplicateResult.likePersisted());
        assertTrue(duplicateResult.createdMatch().isEmpty());
        assertEquals(1, interactionStorage.countByDirection(userA, Like.Direction.LIKE));
    }

    @Test
    @DisplayName("undoStorage saves and reads back an undo state")
    void undoStorageSavesAndReadsBackUndoState() {
        Like like = Like.create(userA, userB, Like.Direction.LIKE);
        Undo undoState = Undo.create(userA, like, null, Instant.now().plusSeconds(60));
        Undo.Storage undoStorage = interactionStorage.undoStorage();

        assertDoesNotThrow(() -> undoStorage.save(undoState));

        Undo persisted = undoStorage.findByUserId(userA).orElseThrow();
        assertEquals(undoState.userId(), persisted.userId());
        assertEquals(undoState.like().id(), persisted.like().id());
        assertTrue(
                Duration.between(undoState.expiresAt(), persisted.expiresAt())
                                .abs()
                                .compareTo(Duration.ofMillis(1))
                        <= 0,
                "Persisted undo expiry should stay within database timestamp precision");
    }

    @Test
    @DisplayName("unmatchTransition should persist the match updatedAt value")
    void unmatchTransitionPersistsUpdatedAt() {
        Match originalMatch = createPersistedActiveMatchWithOldTimestamp();
        originalMatch.unmatch(userA);

        boolean success = interactionStorage.unmatchTransition(originalMatch, Optional.empty());
        assertTrue(success);

        Match persisted = interactionStorage.get(originalMatch.getId()).orElseThrow();
        assertEquals(MatchState.UNMATCHED, persisted.getState());
        assertTrue(
                persisted.getUpdatedAt().isAfter(persisted.getCreatedAt()),
                "Persisted updatedAt should be advanced on transition");
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

    @Test
    @DisplayName("unmatchTransition clears stale likes and allows the ended pair to rematch")
    void unmatchTransitionClearsStaleLikesAndAllowsEndedPairToRematch() {
        Like firstLike = Like.create(userA, userB, Like.Direction.LIKE);
        Like secondLike = Like.create(userB, userA, Like.Direction.LIKE);

        var firstResult = interactionStorage.saveLikeAndMaybeCreateMatch(firstLike);
        var secondResult = interactionStorage.saveLikeAndMaybeCreateMatch(secondLike);

        assertTrue(firstResult.likePersisted());
        assertTrue(secondResult.createdMatch().isPresent());

        Match matched = interactionStorage.get(Match.generateId(userA, userB)).orElseThrow();
        matched.unmatch(userA);

        assertTrue(interactionStorage.unmatchTransition(matched, Optional.empty()));
        assertEquals(0, interactionStorage.countByDirection(userA, Like.Direction.LIKE));
        assertEquals(0, interactionStorage.countByDirection(userB, Like.Direction.LIKE));
        assertTrue(interactionStorage.getLike(userA, userB).isEmpty());
        assertTrue(interactionStorage.getLike(userB, userA).isEmpty());

        Like rematchLikeA = Like.create(userA, userB, Like.Direction.LIKE);
        Like rematchLikeB = Like.create(userB, userA, Like.Direction.LIKE);

        var rematchResultA = interactionStorage.saveLikeAndMaybeCreateMatch(rematchLikeA);
        var rematchResultB = interactionStorage.saveLikeAndMaybeCreateMatch(rematchLikeB);

        assertTrue(rematchResultA.likePersisted());
        assertTrue(rematchResultA.createdMatch().isEmpty());
        assertTrue(rematchResultB.likePersisted());
        assertTrue(rematchResultB.createdMatch().isPresent());
        assertEquals(
                MatchState.ACTIVE,
                interactionStorage
                        .get(Match.generateId(userA, userB))
                        .orElseThrow()
                        .getState());
    }

    @Test
    @DisplayName("unmatchTransition returns false when the match row is missing and keeps likes intact")
    void unmatchTransitionReturnsFalseWhenMatchRowIsMissingAndKeepsLikesIntact() {
        Like firstLike = Like.create(userA, userB, Like.Direction.LIKE);
        Like secondLike = Like.create(userB, userA, Like.Direction.LIKE);
        interactionStorage.save(firstLike);
        interactionStorage.save(secondLike);

        Match missingMatch = Match.create(userA, userB);
        missingMatch.unmatch(userA);

        assertFalse(interactionStorage.unmatchTransition(missingMatch, Optional.empty()));
        assertEquals(1, interactionStorage.countByDirection(userA, Like.Direction.LIKE));
        assertEquals(1, interactionStorage.countByDirection(userB, Like.Direction.LIKE));
    }

    @Test
    @DisplayName("blockTransition rolls back match update when conversation archive fails")
    void blockTransitionRollsBackMatchUpdateWhenConversationArchiveFails() {
        Match match = Match.create(userA, userB);
        interactionStorage.save(match);

        match.block(userA);

        Conversation missingConversation = Conversation.create(userA, userB);
        missingConversation.archive(missingConversation.getUserA(), MatchArchiveReason.BLOCK);
        missingConversation.setVisibility(missingConversation.getUserA(), false);

        assertThrows(
                DatabaseManager.StorageException.class,
                () -> interactionStorage.blockTransition(
                        userA, userB, Optional.of(match), Optional.of(missingConversation)));

        Match persisted = interactionStorage.get(match.getId()).orElseThrow();
        assertEquals(MatchState.ACTIVE, persisted.getState());
        assertTrue(persisted.getEndedAt() == null
                || persisted.getEndedBy() == null
                || !persisted.getEndedBy().equals(userA));
    }

    private Match createPersistedActiveMatchWithOldTimestamp() {
        Instant baseline = Instant.now().minusSeconds(120);
        UUID firstUser = userA.toString().compareTo(userB.toString()) <= 0 ? userA : userB;
        UUID secondUser = firstUser.equals(userA) ? userB : userA;

        Match match = new Match(
                Match.generateId(userA, userB),
                firstUser,
                secondUser,
                baseline,
                baseline,
                MatchState.ACTIVE,
                null,
                null,
                null,
                null);
        interactionStorage.save(match);
        return match;
    }
}
