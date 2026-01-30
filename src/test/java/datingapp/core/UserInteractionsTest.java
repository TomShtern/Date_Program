package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Report;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Consolidated unit tests for UserInteractions domain models (Like, Block,
 * Report).
 *
 * <p>
 * Grouped using JUnit 5 {@code @Nested} classes for logical organization.
 */
@SuppressWarnings("unused") // Test class with @Nested
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class UserInteractionsTest {

    // ==================== LIKE TESTS ====================

    @Nested
    @DisplayName("Like Domain Model")
    class LikeTests {

        @Test
        @DisplayName("Cannot like yourself")
        void cannotLikeSelf() {
            UUID userId = UUID.randomUUID();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> Like.create(userId, userId, Like.Direction.LIKE),
                    "Should throw when trying to like yourself");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Cannot pass on yourself")
        void cannotPassSelf() {
            UUID userId = UUID.randomUUID();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> Like.create(userId, userId, Like.Direction.PASS),
                    "Should throw when trying to pass yourself");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Like creation succeeds with different users")
        void likeCreationSucceeds() {
            UUID whoLikes = UUID.randomUUID();
            UUID whoGotLiked = UUID.randomUUID();

            Like like = Like.create(whoLikes, whoGotLiked, Like.Direction.LIKE);

            assertNotNull(like.id(), "Like should have an ID");
            assertEquals(whoLikes, like.whoLikes(), "Who likes should match");
            assertEquals(whoGotLiked, like.whoGotLiked(), "Who got liked should match");
            assertEquals(Like.Direction.LIKE, like.direction(), "Direction should be LIKE");
            assertNotNull(like.createdAt(), "Created timestamp should be set");
        }

        @Test
        @DisplayName("Pass creation succeeds")
        void passCreationSucceeds() {
            UUID whoLikes = UUID.randomUUID();
            UUID whoGotLiked = UUID.randomUUID();

            Like pass = Like.create(whoLikes, whoGotLiked, Like.Direction.PASS);

            assertEquals(Like.Direction.PASS, pass.direction(), "Direction should be PASS");
        }

        @Test
        @DisplayName("Like IDs are unique")
        void likeIdsAreUnique() {
            UUID whoLikes = UUID.randomUUID();
            UUID whoGotLiked = UUID.randomUUID();

            Like like1 = Like.create(whoLikes, whoGotLiked, Like.Direction.LIKE);
            Like like2 = Like.create(whoLikes, whoGotLiked, Like.Direction.LIKE);

            assertNotEquals(like1.id(), like2.id(), "Different likes should have different IDs");
        }

        @Test
        @DisplayName("Like with null direction throws")
        void nullDirectionThrows() {
            UUID whoLikes = UUID.randomUUID();
            UUID whoGotLiked = UUID.randomUUID();

            NullPointerException ex =
                    assertThrows(NullPointerException.class, () -> Like.create(whoLikes, whoGotLiked, null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Like with null whoLikes throws")
        void nullWhoLikesThrows() {
            UUID whoGotLiked = UUID.randomUUID();

            NullPointerException ex =
                    assertThrows(NullPointerException.class, () -> Like.create(null, whoGotLiked, Like.Direction.LIKE));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Like with null whoGotLiked throws")
        void nullWhoGotLikedThrows() {
            UUID whoLikes = UUID.randomUUID();

            NullPointerException ex =
                    assertThrows(NullPointerException.class, () -> Like.create(whoLikes, null, Like.Direction.LIKE));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Direction enum contains exactly LIKE and PASS")
        void directionEnumValues() {
            Like.Direction[] values = Like.Direction.values();

            assertEquals(2, values.length, "Should have exactly 2 directions");
            assertEquals(Like.Direction.LIKE, Like.Direction.valueOf("LIKE"));
            assertEquals(Like.Direction.PASS, Like.Direction.valueOf("PASS"));
        }

        @Test
        @DisplayName("Like direct constructor throws on null ID")
        void nullIdThrows() {
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            Instant now = Instant.now();

            NullPointerException ex = assertThrows(
                    NullPointerException.class, () -> new Like(null, user1, user2, Like.Direction.LIKE, now));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Like direct constructor throws on null createdAt")
        void nullCreatedAtThrows() {
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            UUID id = UUID.randomUUID();

            NullPointerException ex = assertThrows(
                    NullPointerException.class, () -> new Like(id, user1, user2, Like.Direction.LIKE, null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Like equality contract")
        void likeEquality() {
            UUID id = UUID.randomUUID();
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            Instant now = Instant.now();

            Like like1 = new Like(id, u1, u2, Like.Direction.LIKE, now);
            Like like2 = new Like(id, u1, u2, Like.Direction.LIKE, now);
            Like diffId = new Like(UUID.randomUUID(), u1, u2, Like.Direction.LIKE, now);

            assertEquals(like1, like2, "Same data should be equal");
            assertEquals(like1.hashCode(), like2.hashCode(), "Same data should have same hash");
            assertNotEquals(like1, diffId, "Different ID should not be equal");
        }
    }

    // ==================== BLOCK TESTS ====================

    @Nested
    @DisplayName("Block Domain Model")
    class BlockTests {

        @Test
        @DisplayName("Cannot block yourself")
        void cannotBlockSelf() {
            UUID userId = UUID.randomUUID();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> Block.create(userId, userId),
                    "Should throw when trying to block yourself");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Block creation succeeds with different users")
        void blockCreationSucceeds() {
            UUID blockerId = UUID.randomUUID();
            UUID blockedId = UUID.randomUUID();

            Block block = Block.create(blockerId, blockedId);

            assertNotNull(block.id(), "Block should have an ID");
            assertEquals(blockerId, block.blockerId(), "Blocker ID should match");
            assertEquals(blockedId, block.blockedId(), "Blocked ID should match");
            assertNotNull(block.createdAt(), "Created timestamp should be set");
        }

        @Test
        @DisplayName("Block IDs are unique")
        void blockIdsAreUnique() {
            UUID blockerId = UUID.randomUUID();
            UUID blockedId = UUID.randomUUID();

            Block block1 = Block.create(blockerId, blockedId);
            Block block2 = Block.create(blockerId, blockedId);

            assertNotEquals(block1.id(), block2.id(), "Different blocks should have different IDs");
        }

        @Test
        @DisplayName("Block with null blocker ID throws NullPointerException")
        void nullBlockerIdThrows() {
            UUID blockedId = UUID.randomUUID();

            NullPointerException ex = assertThrows(NullPointerException.class, () -> Block.create(null, blockedId));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Block with null blocked ID throws NullPointerException")
        void nullBlockedIdThrows() {
            UUID blockerId = UUID.randomUUID();

            NullPointerException ex = assertThrows(NullPointerException.class, () -> Block.create(blockerId, null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Block direct constructor throws on null ID")
        void nullIdThrows() {
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            Instant now = Instant.now();

            NullPointerException ex =
                    assertThrows(NullPointerException.class, () -> new Block(null, user1, user2, now));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Block direct constructor throws on null createdAt")
        void nullCreatedAtThrows() {
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            UUID id = UUID.randomUUID();

            NullPointerException ex = assertThrows(NullPointerException.class, () -> new Block(id, user1, user2, null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Block equality contract")
        void blockEquality() {
            UUID id = UUID.randomUUID();
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            Instant now = Instant.now();

            Block b1 = new Block(id, u1, u2, now);
            Block b2 = new Block(id, u1, u2, now);
            Block diff = new Block(id, u2, u1, now);

            assertEquals(b1, b2, "Same data should be equal");
            assertEquals(b1.hashCode(), b2.hashCode(), "Same data should have same hash");
            assertNotEquals(b1, diff, "Different data should not be equal");
        }
    }

    // ==================== REPORT TESTS ====================

    @Nested
    @DisplayName("Report Domain Model")
    class ReportTests {

        @Test
        @DisplayName("Cannot report yourself")
        void cannotReportSelf() {
            UUID userId = UUID.randomUUID();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> Report.create(userId, userId, Report.Reason.SPAM, null),
                    "Should throw when trying to report yourself");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Report creation succeeds with valid data")
        void reportCreationSucceeds() {
            UUID reporterId = UUID.randomUUID();
            UUID reportedId = UUID.randomUUID();

            Report report =
                    Report.create(reporterId, reportedId, Report.Reason.HARASSMENT, "Sent threatening messages");

            assertNotNull(report.id(), "Report should have an ID");
            assertEquals(reporterId, report.reporterId(), "Reporter ID should match");
            assertEquals(reportedId, report.reportedUserId(), "Reported user ID should match");
            assertEquals(Report.Reason.HARASSMENT, report.reason(), "Reason should match");
            assertEquals("Sent threatening messages", report.description(), "Description should match");
            assertNotNull(report.createdAt(), "Created timestamp should be set");
        }

        @Test
        @DisplayName("Report works without description")
        void reportWorksWithoutDescription() {
            UUID reporterId = UUID.randomUUID();
            UUID reportedId = UUID.randomUUID();

            Report report = Report.create(reporterId, reportedId, Report.Reason.SPAM, null);

            assertNull(report.description(), "Description should be null when not provided");
        }

        @Test
        @DisplayName("Description exceeding 500 characters throws")
        void descriptionTooLongThrows() {
            UUID reporterId = UUID.randomUUID();
            UUID reportedId = UUID.randomUUID();
            String longDescription = "x".repeat(501);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> Report.create(reporterId, reportedId, Report.Reason.OTHER, longDescription),
                    "Should throw when description exceeds 500 characters");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Description at exactly 500 characters is allowed")
        void descriptionAtLimitIsAllowed() {
            UUID reporterId = UUID.randomUUID();
            UUID reportedId = UUID.randomUUID();
            String maxDescription = "x".repeat(500);

            Report report = Report.create(reporterId, reportedId, Report.Reason.OTHER, maxDescription);

            assertEquals(500, report.description().length(), "Description should be exactly 500 chars");
        }

        @Test
        @DisplayName("All report reasons are valid")
        void allReasonsAreValid() {
            UUID reporterId = UUID.randomUUID();
            UUID reportedId = UUID.randomUUID();

            for (Report.Reason reason : Report.Reason.values()) {
                Report report = Report.create(reporterId, reportedId, reason, null);
                assertEquals(reason, report.reason(), "Each reason should be creatable");
            }
        }

        @Test
        @DisplayName("Report with null reason throws")
        void nullReasonThrows() {
            UUID reporterId = UUID.randomUUID();
            UUID reportedId = UUID.randomUUID();

            NullPointerException ex =
                    assertThrows(NullPointerException.class, () -> Report.create(reporterId, reportedId, null, null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Report direct constructor throws on null ID")
        void nullIdThrows() {
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            Instant now = Instant.now();

            NullPointerException ex = assertThrows(
                    NullPointerException.class, () -> new Report(null, u1, u2, Report.Reason.SPAM, "desc", now));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Report direct constructor throws on null createdAt")
        void nullCreatedAtThrows() {
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            UUID id = UUID.randomUUID();

            NullPointerException ex = assertThrows(
                    NullPointerException.class, () -> new Report(id, u1, u2, Report.Reason.SPAM, "desc", null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Report equality contract")
        void reportEquality() {
            UUID id = UUID.randomUUID();
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            Instant now = Instant.now();

            Report r1 = new Report(id, u1, u2, Report.Reason.SPAM, "desc", now);
            Report r2 = new Report(id, u1, u2, Report.Reason.SPAM, "desc", now);
            Report diff = new Report(id, u1, u2, Report.Reason.SPAM, "diff", now);

            assertEquals(r1, r2, "Same data should be equal");
            assertEquals(r1.hashCode(), r2.hashCode(), "Same data should have same hash");
            assertNotEquals(r1, diff, "Different data should not be equal");
        }
    }
}
