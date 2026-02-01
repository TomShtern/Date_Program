package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Edge case regression tests - consolidated from BugInvestigationTest and
 * Round2BugInvestigationTest.
 *
 * <p>These tests cover edge cases that were discovered during development and
 * serve as regression tests to prevent reintroduction of bugs.
 */
@SuppressWarnings("unused")
@DisplayName("Edge Case Regression Tests")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class EdgeCaseRegressionTest {

    // ============================================================
    // USER DATA INTEGRITY
    // ============================================================

    @Nested
    @DisplayName("User Data Integrity")
    class UserDataIntegrity {

        @Test
        @DisplayName("getInterestedIn handles empty set without throwing")
        void getInterestedInHandlesEmptySet() {
            User user = new User(UUID.randomUUID(), "Test");

            assertDoesNotThrow(
                    user::getInterestedIn, "getInterestedIn() should not throw on new user with empty interestedIn");
        }

        @Test
        @DisplayName("New user starts with empty interestedIn set")
        void newUserHasEmptyInterestedIn() {
            User user = new User(UUID.randomUUID(), "Test");

            assertTrue(user.getInterestedIn().isEmpty(), "New user should have empty interestedIn set");
        }

        @Test
        @DisplayName("Clearing interestedIn does not break getter")
        void clearingInterestedInWorks() {
            User user = new User(UUID.randomUUID(), "Test");
            user.setInterestedIn(EnumSet.of(User.Gender.MALE));
            user.setInterestedIn(EnumSet.noneOf(User.Gender.class)); // Clear it

            Set<User.Gender> interested = user.getInterestedIn();

            assertNotNull(interested);
            assertDoesNotThrow(user::getInterestedIn);
            assertTrue(interested.isEmpty());
        }

        @Test
        @DisplayName("User at 0,0 coordinates is technically complete")
        void userAtZeroZeroIsComplete() {
            User user = new User(UUID.randomUUID(), "Test");
            user.setBio("Bio");
            user.setBirthDate(LocalDate.of(1990, 1, 1));
            user.setGender(User.Gender.MALE);
            user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
            // Note: location defaults to 0,0
            user.setMaxDistanceKm(50);
            user.setAgeRange(18, 40);
            user.addPhotoUrl("photo.jpg");
            user.setPacePreferences(new Preferences.PacePreferences(
                    MessagingFrequency.OFTEN,
                    TimeToFirstDate.FEW_DAYS,
                    CommunicationStyle.TEXT_ONLY,
                    DepthPreference.DEEP_CHAT));

            // Known limitation: isComplete() doesn't validate 0,0 location
            assertTrue(user.isComplete(), "User is technically complete at 0,0");
            assertEquals(0.0, user.getLat());
            assertEquals(0.0, user.getLon());
        }
    }

    // ============================================================
    // AGE CALCULATION EDGE CASES
    // ============================================================

    @Nested
    @DisplayName("Age Calculation")
    class AgeCalculation {

        @Test
        @DisplayName("User born today has age 0")
        void userBornTodayHasAgeZero() {
            User user = new User(UUID.randomUUID(), "Baby");
            user.setBirthDate(LocalDate.now());

            assertEquals(0, user.getAge(), "User born today should be 0 years old");
        }

        @Test
        @DisplayName("User turning 18 today is exactly 18")
        void userTurning18Today() {
            User user = new User(UUID.randomUUID(), "Teen");
            user.setBirthDate(LocalDate.now().minusYears(18));

            assertEquals(18, user.getAge(), "User exactly 18 years");
        }

        @Test
        @DisplayName("17-year-old can have complete profile (known limitation)")
        void minorCanHaveCompleteProfile() {
            User user = new User(UUID.randomUUID(), "Teen");
            user.setBio("Bio");
            user.setBirthDate(LocalDate.now().minusYears(17)); // 17 years old
            user.setGender(User.Gender.MALE);
            user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
            user.setLocation(32.0, 34.0);
            user.setMaxDistanceKm(50);
            user.setAgeRange(18, 40);
            user.addPhotoUrl("photo.jpg");
            user.setPacePreferences(new Preferences.PacePreferences(
                    MessagingFrequency.OFTEN,
                    TimeToFirstDate.FEW_DAYS,
                    CommunicationStyle.TEXT_ONLY,
                    DepthPreference.DEEP_CHAT));

            assertEquals(17, user.getAge());
            // Known limitation: app doesn't enforce minimum age at profile completion
            assertTrue(user.isComplete(), "17yo profile can be complete (known limitation)");
        }
    }

    // ============================================================
    // MATCH STATE TRANSITIONS
    // ============================================================

    @Nested
    @DisplayName("Match State Transitions")
    class MatchStateTransitions {

        @Test
        @DisplayName("Blocking already blocked match is idempotent")
        void blockingAlreadyBlockedMatch() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            match.block(a);

            // Second block should be defensive, not throw
            assertDoesNotThrow(() -> match.block(b), "Blocking already blocked match should be defensive");
            assertEquals(Match.State.BLOCKED, match.getState());
        }
    }

    // ============================================================
    // CANDIDATE FINDER EDGE CASES
    // ============================================================

    @Nested
    @DisplayName("CandidateFinder Edge Cases")
    class CandidateFinderEdgeCases {

        @Test
        @DisplayName("Empty active users list returns empty candidates")
        void emptyActiveUsersReturnsEmpty() {
            // Create minimal stubs for storage dependencies (not used by findCandidates)
            CandidateFinder finder =
                    new CandidateFinder(createStubUserStorage(), createStubLikeStorage(), createStubBlockStorage());
            User seeker = createCompleteActiveUser("Seeker");

            var candidates = finder.findCandidates(seeker, List.of(), Set.of());

            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("Seeker with null interestedIn returns no candidates")
        void seekerWithNullInterestedIn() {
            // Create minimal stubs for storage dependencies (not used by findCandidates)
            CandidateFinder finder =
                    new CandidateFinder(createStubUserStorage(), createStubLikeStorage(), createStubBlockStorage());
            User seeker = createCompleteActiveUser("Seeker");
            seeker.setInterestedIn(null);

            User candidate = createCompleteActiveUser("Candidate");

            var candidates = finder.findCandidates(seeker, List.of(candidate), Set.of());

            assertTrue(candidates.isEmpty());
        }

        @Test
        @DisplayName("Banned users are filtered out")
        void candidateFinderFiltersBannedUsers() {
            // Create minimal stubs for storage dependencies (not used by findCandidates)
            CandidateFinder finder =
                    new CandidateFinder(createStubUserStorage(), createStubLikeStorage(), createStubBlockStorage());

            User seeker = createCompleteActiveUser("Seeker");
            User bannedUser = createCompleteActiveUser("Banned");
            bannedUser.ban();

            var candidates = finder.findCandidates(seeker, List.of(bannedUser), Collections.emptySet());

            assertTrue(candidates.isEmpty(), "CandidateFinder should filter banned users");
        }
    }

    // ============================================================
    // MATCHING SERVICE EDGE CASES
    // ============================================================

    @Nested
    @DisplayName("MatchingService Edge Cases")
    class MatchingServiceEdgeCases {

        @Test
        @DisplayName("Handles duplicate match creation gracefully")
        void handlesDuplicateMatchCreation() {
            InMemoryLikeStorage likeStorage = new InMemoryLikeStorage();

            // Simulate race condition: exists() returns false, but save() hits duplicate
            MatchStorage raceConditionStorage = new MatchStorage() {
                private final Set<String> existing = new HashSet<>();

                @Override
                public void save(Match match) {
                    if (existing.contains(match.getId())) {
                        throw new RuntimeException("DUPLICATE_KEY: Match already exists " + match.getId());
                    }
                    existing.add(match.getId());
                }

                @Override
                public Optional<Match> get(String matchId) {
                    return Optional.empty();
                }

                @Override
                public boolean exists(String matchId) {
                    return false; // Simulate race: check passes for both "threads"
                }

                @Override
                public void update(Match match) {
                    // Intentionally empty - not needed for this test
                }

                @Override
                public List<Match> getActiveMatchesFor(UUID userId) {
                    return List.of();
                }

                @Override
                public List<Match> getAllMatchesFor(UUID userId) {
                    return List.of();
                }

                @Override
                public void delete(String matchId) {
                    existing.remove(matchId);
                }
            };

            MatchingService service = new MatchingService(likeStorage, raceConditionStorage);

            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            // Pre-seed mutual likes
            likeStorage.save(Like.create(a, b, Like.Direction.LIKE));
            likeStorage.save(Like.create(b, a, Like.Direction.LIKE));

            // Simulate duplicate processing
            Like duplicateAction = Like.create(a, b, Like.Direction.LIKE);

            assertDoesNotThrow(
                    () -> service.recordLike(duplicateAction),
                    "Service should handle duplicate match creation gracefully");

            assertEquals(3, likeStorage.getLikesCount(), "Should have recorded 3 likes total");
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private User createCompleteActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Bio");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.MALE);
        user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 60);
        user.addPhotoUrl("photo.jpg");
        user.setPacePreferences(new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    // ============================================================
    // IN-MEMORY MOCK STORAGE FOR TESTS
    // ============================================================

    private static class InMemoryLikeStorage implements LikeStorage {
        private final List<Like> likes = new ArrayList<>();

        public int getLikesCount() {
            return likes.size();
        }

        @Override
        public void save(Like like) {
            likes.add(like);
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return false; // Always allow for test
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            return true; // Always yes for test
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return Set.of();
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return Set.of();
        }

        @Override
        public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            Map<UUID, Instant> result = new HashMap<>();
            for (Like like : likes) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.put(like.whoLikes(), like.createdAt());
                }
            }
            return result;
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLikedAsList(UUID userId) {
            return new ArrayList<>(getLikeTimesForUsersWhoLiked(userId).entrySet());
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return 0;
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return 0;
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return 0;
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return likes.stream()
                    .filter(l ->
                            l.whoLikes().equals(fromUserId) && l.whoGotLiked().equals(toUserId))
                    .findFirst();
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public void delete(UUID likeId) {
            likes.removeIf(like -> like.id().equals(likeId));
        }
    }

    // Helper methods to create stub storage for CandidateFinder tests
    private static datingapp.core.storage.UserStorage createStubUserStorage() {
        return new datingapp.core.storage.UserStorage() {
            @Override
            public void save(User user) {}

            @Override
            public User get(UUID id) {
                return null;
            }

            @Override
            public List<User> findAll() {
                return List.of();
            }

            @Override
            public List<User> findActive() {
                return List.of();
            }

            @Override
            public void delete(UUID id) {}

            @Override
            public void saveProfileNote(User.ProfileNote note) {}

            @Override
            public Optional<User.ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
                return Optional.empty();
            }

            @Override
            public List<User.ProfileNote> getProfileNotesByAuthor(UUID authorId) {
                return List.of();
            }

            @Override
            public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
                return false;
            }
        };
    }

    private static datingapp.core.storage.LikeStorage createStubLikeStorage() {
        return new datingapp.core.storage.LikeStorage() {
            @Override
            public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
                return Set.of();
            }

            @Override
            public void save(UserInteractions.Like like) {}

            @Override
            public boolean exists(UUID from, UUID to) {
                return false;
            }

            @Override
            public boolean mutualLikeExists(UUID a, UUID b) {
                return false;
            }

            @Override
            public Set<UUID> getUserIdsWhoLiked(UUID userId) {
                return Set.of();
            }

            @Override
            public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
                return Map.of();
            }

            @Override
            public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLikedAsList(UUID userId) {
                return List.of();
            }

            @Override
            public int countByDirection(UUID userId, UserInteractions.Like.Direction direction) {
                return 0;
            }

            @Override
            public int countReceivedByDirection(UUID userId, UserInteractions.Like.Direction direction) {
                return 0;
            }

            @Override
            public Optional<UserInteractions.Like> getLike(UUID fromUserId, UUID toUserId) {
                return Optional.empty();
            }

            @Override
            public int countLikesToday(UUID userId, Instant startOfDay) {
                return 0;
            }

            @Override
            public int countPassesToday(UUID userId, Instant startOfDay) {
                return 0;
            }

            @Override
            public int countMutualLikes(UUID userId) {
                return 0;
            }

            @Override
            public void delete(UUID likeId) {}
        };
    }

    private static datingapp.core.storage.BlockStorage createStubBlockStorage() {
        return new datingapp.core.storage.BlockStorage() {
            @Override
            public Set<UUID> getBlockedUserIds(UUID userId) {
                return Set.of();
            }

            @Override
            public void save(UserInteractions.Block block) {}

            @Override
            public boolean isBlocked(UUID userA, UUID userB) {
                return false;
            }

            @Override
            public List<UserInteractions.Block> findByBlocker(UUID blockerId) {
                return List.of();
            }

            @Override
            public boolean delete(UUID blockerId, UUID blockedId) {
                return false;
            }

            @Override
            public int countBlocksGiven(UUID userId) {
                return 0;
            }

            @Override
            public int countBlocksReceived(UUID userId) {
                return 0;
            }
        };
    }
}
