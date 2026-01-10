package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for Match domain model. */
class MatchTest {

  @Test
  @DisplayName("Match ID is deterministic regardless of UUID order")
  void matchIdIsDeterministic() {
    UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

    Match match1 = Match.create(a, b);
    Match match2 = Match.create(b, a);

    assertEquals(match1.getId(), match2.getId());
  }

  @Test
  @DisplayName("userA is always the lexicographically smaller UUID")
  void userAIsAlwaysSmaller() {
    UUID a = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    Match match = Match.create(a, b);

    assertTrue(match.getUserA().toString().compareTo(match.getUserB().toString()) < 0);
  }

  @Test
  @DisplayName("Cannot create match with same user")
  void cannotMatchWithSelf() {
    UUID a = UUID.randomUUID();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> Match.create(a, a));
    assertNotNull(ex);
  }

  @Test
  @DisplayName("getOtherUser returns correct user")
  void getOtherUserReturnsCorrectly() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();

    Match match = Match.create(a, b);

    assertEquals(b, match.getOtherUser(a));
    assertEquals(a, match.getOtherUser(b));
  }
}
