package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InterestTest {

  @Test
  void byCategory_outdoors_returnsCorrectInterests() {
    List<Interest> outdoors = Interest.byCategory(Interest.Category.OUTDOORS);
    assertTrue(outdoors.contains(Interest.HIKING));
    assertTrue(outdoors.contains(Interest.CAMPING));
    assertEquals(6, outdoors.size());
  }

  @Test
  void byCategory_null_returnsEmptyList() {
    List<Interest> result = Interest.byCategory(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void allInterests_haveDisplayNames() {
    for (Interest interest : Interest.values()) {
      assertNotNull(interest.getDisplayName());
      assertFalse(interest.getDisplayName().isEmpty());
    }
  }

  @Test
  void allInterests_haveCategories() {
    for (Interest interest : Interest.values()) {
      assertNotNull(interest.getCategory());
    }
  }

  @Test
  void count_returnsCorrectTotal() {
    assertEquals(Interest.values().length, Interest.count());
  }

  @Test
  void constants_areDefined() {
    assertEquals(10, Interest.MAX_PER_USER);
    assertEquals(3, Interest.MIN_FOR_COMPLETE);
  }
}
