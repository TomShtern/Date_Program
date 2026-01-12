package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for Like domain model. */
class LikeTest {

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
}
