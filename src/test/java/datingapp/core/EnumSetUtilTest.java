package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("EnumSetUtil")
class EnumSetUtilTest {

    @Nested
    @DisplayName("safeCopy(Collection)")
    class SafeCopyCollection {

        @Test
        @DisplayName("returns empty set for null input")
        void returnsEmptyForNull() {
            EnumSet<Interest> result = EnumSetUtil.safeCopy((Collection<Interest>) null, Interest.class);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty set for empty collection")
        void returnsEmptyForEmptyCollection() {
            EnumSet<Interest> result = EnumSetUtil.safeCopy(List.of(), Interest.class);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("copies elements from non-empty collection")
        void copiesNonEmptyCollection() {
            List<Interest> input = List.of(Interest.HIKING, Interest.COOKING);
            EnumSet<Interest> result = EnumSetUtil.safeCopy(input, Interest.class);
            assertEquals(2, result.size());
            assertTrue(result.contains(Interest.HIKING));
            assertTrue(result.contains(Interest.COOKING));
        }

        @Test
        @DisplayName("returned set is independent of source")
        void returnedSetIsIndependent() {
            var source = new java.util.ArrayList<>(List.of(Interest.HIKING));
            EnumSet<Interest> result = EnumSetUtil.safeCopy(source, Interest.class);
            source.add(Interest.COOKING);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("throws on null enumClass")
        void throwsOnNullEnumClass() {
            assertThrows(NullPointerException.class, () -> EnumSetUtil.safeCopy((Collection<Interest>) null, null));
        }
    }

    @Nested
    @DisplayName("safeCopy(Set)")
    class SafeCopySet {

        @Test
        @DisplayName("returns empty set for null input")
        void returnsEmptyForNull() {
            EnumSet<User.Gender> result = EnumSetUtil.safeCopy((Set<User.Gender>) null, User.Gender.class);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty set for empty set")
        void returnsEmptyForEmptySet() {
            EnumSet<User.Gender> result = EnumSetUtil.safeCopy(Set.of(), User.Gender.class);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("copies elements from non-empty set")
        void copiesNonEmptySet() {
            Set<User.Gender> input = Set.of(User.Gender.MALE, User.Gender.FEMALE);
            EnumSet<User.Gender> result = EnumSetUtil.safeCopy(input, User.Gender.class);
            assertEquals(2, result.size());
            assertTrue(result.contains(User.Gender.MALE));
            assertTrue(result.contains(User.Gender.FEMALE));
        }

        @Test
        @DisplayName("copies from EnumSet input")
        void copiesEnumSetInput() {
            EnumSet<User.Gender> input = EnumSet.of(User.Gender.MALE);
            EnumSet<User.Gender> result = EnumSetUtil.safeCopy(input, User.Gender.class);
            assertEquals(input, result);
            assertNotSame(input, result);
        }
    }

    @Nested
    @DisplayName("defensiveCopy")
    class DefensiveCopy {

        @Test
        @DisplayName("returns empty set for null input")
        void returnsEmptyForNull() {
            EnumSet<Interest> result = EnumSetUtil.defensiveCopy(null, Interest.class);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty set for empty EnumSet")
        void returnsEmptyForEmptyEnumSet() {
            EnumSet<Interest> result = EnumSetUtil.defensiveCopy(EnumSet.noneOf(Interest.class), Interest.class);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("creates independent copy")
        void createsIndependentCopy() {
            EnumSet<Interest> original = EnumSet.of(Interest.HIKING, Interest.YOGA);
            EnumSet<Interest> copy = EnumSetUtil.defensiveCopy(original, Interest.class);

            assertEquals(original, copy);
            assertNotSame(original, copy);

            original.add(Interest.COOKING);
            assertFalse(copy.contains(Interest.COOKING));
        }

        @Test
        @DisplayName("throws on null enumClass")
        void throwsOnNullEnumClass() {
            assertThrows(NullPointerException.class, () -> EnumSetUtil.defensiveCopy(null, null));
        }
    }
}
