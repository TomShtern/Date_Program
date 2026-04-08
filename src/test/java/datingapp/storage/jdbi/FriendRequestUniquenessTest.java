package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.storage.DatabaseManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for friend request uniqueness constraint and deterministic query behavior.
 *
 * Addresses findings F50 and F51 from SOURCE_CODE_ONLY_ISSUES_AUDIT_2026-03-28.md
 */
@Timeout(10)
class FriendRequestUniquenessTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private DatabaseManager dbManager;
    private Jdbi jdbi;
    private JdbiConnectionStorage connectionStorage;
    private UUID user1;
    private UUID user2;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        String dbName = "friend_req_test_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

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

        connectionStorage = new JdbiConnectionStorage(jdbi);

        user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Insert test users for foreign key constraints
        jdbi.withHandle(handle -> {
            handle.execute(
                    "INSERT INTO users (id, name, email, created_at, updated_at, state) VALUES (?, ?, ?, ?, ?, ?)",
                    user1,
                    "User One",
                    "user1@test.com",
                    Instant.now(),
                    Instant.now(),
                    "ACTIVE");
            handle.execute(
                    "INSERT INTO users (id, name, email, created_at, updated_at, state) VALUES (?, ?, ?, ?, ?, ?)",
                    user2,
                    "User Two",
                    "user2@test.com",
                    Instant.now(),
                    Instant.now(),
                    "ACTIVE");
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    private FriendRequest accepted(FriendRequest request) {
        return new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.ACCEPTED,
                Instant.now());
    }

    private FriendRequest declined(FriendRequest request) {
        return new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.DECLINED,
                Instant.now());
    }

    @Nested
    @DisplayName("F50: Pending friend-request uniqueness constraint")
    class PendingRequestUniqueness {

        @Test
        @DisplayName("should prevent duplicate pending requests from same user pair")
        void preventsDuplicatePendingRequests() {
            // Create first pending request
            FriendRequest request1 = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request1);

            // Attempt to create second pending request - should fail
            FriendRequest request2 = FriendRequest.create(user1, user2);
            assertThrows(
                    UnableToExecuteStatementException.class,
                    () -> {
                        connectionStorage.saveFriendRequest(request2);
                    },
                    "Duplicate pending request should fail");
        }

        @Test
        @DisplayName("should prevent duplicate pending requests for reverse user order")
        void preventsDuplicatePendingRequestsForReverseOrder() {
            FriendRequest request1 = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request1);

            FriendRequest request2 = FriendRequest.create(user2, user1);
            assertThrows(
                    UnableToExecuteStatementException.class,
                    () -> {
                        connectionStorage.saveFriendRequest(request2);
                    },
                    "Reverse-order duplicate pending request should fail");
        }

        @Test
        @DisplayName("should allow new pending request after previous is accepted")
        void allowsNewRequestAfterAccepted() {
            // Create and accept first request
            FriendRequest request1 = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request1);
            connectionStorage.updateFriendRequest(accepted(request1));

            // Should allow new pending request now
            FriendRequest request2 = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request2);

            Optional<FriendRequest> pending = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            assertTrue(pending.isPresent());
            assertEquals(request2.id(), pending.get().id());
        }

        @Test
        @DisplayName("should allow new pending request after previous is rejected")
        void allowsNewRequestAfterRejected() {
            // Create and reject first request
            FriendRequest request1 = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request1);
            connectionStorage.updateFriendRequest(declined(request1));

            // Should allow new pending request now
            FriendRequest request2 = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request2);

            Optional<FriendRequest> pending = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            assertTrue(pending.isPresent());
            assertEquals(request2.id(), pending.get().id());
        }
    }

    @Nested
    @DisplayName("F51: Deterministic pending request query")
    class DeterministicQueryBehavior {

        @Test
        @DisplayName("getPendingFriendRequestBetween should return exactly one or none")
        void returnsExactlyOneOrNone() {
            // Test with no requests
            Optional<FriendRequest> none = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            assertTrue(none.isEmpty(), "Should return empty when no pending requests");

            // Test with one request
            FriendRequest request = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request);

            Optional<FriendRequest> one = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            assertTrue(one.isPresent(), "Should return request when one exists");
            assertEquals(request.id(), one.get().id());
        }

        @Test
        @DisplayName("query should work bidirectionally (either from/to order)")
        void worksBidirectionally() {
            FriendRequest request = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request);

            // Both directions should find the same request
            Optional<FriendRequest> forward = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            Optional<FriendRequest> reverse = connectionStorage.getPendingFriendRequestBetween(user2, user1);

            assertTrue(forward.isPresent());
            assertTrue(reverse.isPresent());
            assertEquals(forward.get().id(), reverse.get().id());
        }

        @Test
        @DisplayName("query should have deterministic order with LIMIT 1")
        void hasDeterministicOrder() {
            FriendRequest request = FriendRequest.create(user1, user2);
            connectionStorage.saveFriendRequest(request);

            // Multiple queries should return the same result
            Optional<FriendRequest> result1 = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            Optional<FriendRequest> result2 = connectionStorage.getPendingFriendRequestBetween(user1, user2);
            Optional<FriendRequest> result3 = connectionStorage.getPendingFriendRequestBetween(user1, user2);

            assertTrue(result1.isPresent());
            assertTrue(result2.isPresent());
            assertTrue(result3.isPresent());
            assertEquals(result1.get().id(), result2.get().id());
            assertEquals(result2.get().id(), result3.get().id());
        }
    }
}
