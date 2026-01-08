package datingapp.core;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Bug investigation tests - these tests expose bugs in the codebase.
 * Tests marked as "BUG" are expected to fail until the bug is fixed.
 */
class BugInvestigationTest {

    @Nested
    @DisplayName("BUG 1: User.getInterestedIn() empty set")
    class DataIntegrityBugs {

        @Test
        @DisplayName("getInterestedIn should handle empty set")
        void getInterestedInHandlesEmptySet() {
            User user = new User(UUID.randomUUID(), "Test");

            // This should NOT throw, but currently EnumSet.copyOf() throws on empty
            assertDoesNotThrow(user::getInterestedIn,
                    "getInterestedIn() should not throw on new user with empty interestedIn");
        }

        @Test
        @DisplayName("New user starts with empty interestedIn set")
        void newUserHasEmptyInterestedIn() {
            User user = new User(UUID.randomUUID(), "Test");

            // This should return an empty set
            assertTrue(user.getInterestedIn().isEmpty(),
                    "New user should have empty interestedIn set");
        }

        @Test
        @DisplayName("Clearing interestedIn should not break getInterestedIn")
        void clearingInterestedInWorks() {
            User user = new User(UUID.randomUUID(), "Test");
            user.setInterestedIn(EnumSet.of(User.Gender.MALE));
            user.setInterestedIn(EnumSet.noneOf(User.Gender.class)); // Clear it

            // Should not throw NullPointerException
            // Act
            Set<User.Gender> interested = user.getInterestedIn();

            // Assert
            assertNotNull(interested);
            assertDoesNotThrow(user::getInterestedIn);
            assertTrue(interested.isEmpty());
        }
    }

    @Nested
    @DisplayName("BUG 2: Match consistency - missing location check in isComplete")
    class PaginationBugs {

        @Test
        @DisplayName("User at 0,0 coordinates - is this intentional?")
        void userAtZeroZeroCoordinates() {
            User user = new User(UUID.randomUUID(), "Test");
            user.setBio("Bio");
            user.setBirthDate(LocalDate.of(1990, 1, 1));
            user.setGender(User.Gender.MALE);
            user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
            // Note: location defaults to 0,0 - is this valid?
            user.setMaxDistanceKm(50);
            user.setAgeRange(18, 40);
            user.addPhotoUrl("photo.jpg");

            // isComplete() doesn't validate that location is set (lat/lon = 0,0)
            // This might be a bug - 0,0 is in the ocean off Africa
            assertTrue(user.isComplete(), "User is technically complete but at 0,0");
            assertEquals(0.0, user.getLat());
            assertEquals(0.0, user.getLon());
        }
    }

    @Nested
    @DisplayName("BUG 3: Edge cases in age calculation")
    class StateTransitionBugs {

        @Test
        @DisplayName("User born today has age 0")
        void userBornTodayHasAgeZero() {
            User user = new User(UUID.randomUUID(), "Baby");
            user.setBirthDate(LocalDate.now());

            assertEquals(0, user.getAge(), "User born today should be 0 years old");
        }

        @Test
        @DisplayName("User turning 18 today")
        void userTurning18Today() {
            User user = new User(UUID.randomUUID(), "Teen");
            user.setBirthDate(LocalDate.now().minusYears(18));

            assertEquals(18, user.getAge(), "User exactly 18 years");
        }

        @Test
        @DisplayName("Age for 17-year-old - can register but not be active")
        void minorCannotBeActive() {
            User user = new User(UUID.randomUUID(), "Teen");
            user.setBio("Bio");
            user.setBirthDate(LocalDate.now().minusYears(17)); // 17 years old
            user.setGender(User.Gender.MALE);
            user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
            user.setLocation(32.0, 34.0);
            user.setMaxDistanceKm(50);
            user.setAgeRange(18, 40);
            user.addPhotoUrl("photo.jpg");

            assertEquals(17, user.getAge());

            // A 17-year-old can have a "complete" profile and activate
            // This is arguably a bug - minors shouldn't be on dating apps
            assertTrue(user.isComplete(), "17yo profile can be marked complete - BUG?");
        }
    }

    @Nested
    @DisplayName("BUG 4: Match unmatch race condition")
    class MatchingServiceBugs {

        @Test
        @DisplayName("Blocking already blocked match is defensive")
        void blockingAlreadyBlockedMatch() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            // Block once
            match.block(a);

            // Block again - this should be idempotent, not throw
            assertDoesNotThrow(() -> match.block(b),
                    "Blocking an already blocked match should be defensive");

            assertEquals(Match.State.BLOCKED, match.getState());
        }
    }

    @Nested
    @DisplayName("BUG 5: CandidateFinder edge cases")
    class CandidateFinderBugs {

        @Test
        @DisplayName("Empty active users list returns empty candidates")
        void emptyActiveUsersReturnsEmpty() {
            CandidateFinder finder = new CandidateFinder();
            User seeker = createCompleteUser("Seeker");

            var candidates = finder.findCandidates(seeker, java.util.List.of(), java.util.Set.of());

            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("Seeker with null interestedIn returns no candidates")
        void seekerWithNullInterestedIn() {
            CandidateFinder finder = new CandidateFinder();
            User seeker = createCompleteUser("Seeker");
            seeker.setInterestedIn(null); // Clear to null

            User candidate = createCompleteUser("Candidate");

            var candidates = finder.findCandidates(seeker, java.util.List.of(candidate), java.util.Set.of());

            // Should not throw, should return empty since preferences don't match
            assertTrue(candidates.isEmpty());
        }
    }

    private User createCompleteUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Bio");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.MALE);
        user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 60);
        user.addPhotoUrl("photo.jpg");
        user.activate();
        return user;
    }
}
