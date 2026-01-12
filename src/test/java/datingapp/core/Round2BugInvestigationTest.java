package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Round 2 bug investigation tests. */
class Round2BugInvestigationTest {

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("BUG: CandidateFinder filters validity")
    class CandidateFinderFilters {

        @Test
        @DisplayName("CandidateFinder allows non-ACTIVE users")
        void candidateFinderAllowsNonActiveUsers() {
            CandidateFinder finder = new CandidateFinder();

            User seeker = createActiveUser();
            User bannedUser = createActiveUser();
            bannedUser.ban();

            // Current behavior: CandidateFinder DOES NOT filter by state, assuming inputs
            // are active
            // This test verifies that banned users ARE returned if passed in (leaky
            // abstraction)
            var candidates = finder.findCandidates(seeker, List.of(bannedUser), Collections.emptySet());

            // Should return 0 if filtering is correct. Currently returns 1 (bug).
            assertTrue(candidates.isEmpty(), "CandidateFinder should have filtered out the banned user");
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("BUG: MatchingService Concurrency")
    class MatchingServiceConcurrency {

        @Test
        @DisplayName("MatchingService crashes on duplicate match")
        void matchingServiceCrashesOnDuplicate() {
            // Setup
            InMemoryLikeStorage likeStorage = new InMemoryLikeStorage();

            // A "race condition" match storage
            MatchStorage raceConditionStorage = new MatchStorage() {
                private final java.util.Set<String> existing = new java.util.HashSet<>();

                @Override
                public void save(Match match) {
                    if (existing.contains(match.getId())) {
                        // Simulate DB duplicate key exception
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
                    // Simulate "not found" check passing for both threads
                    return false;
                }

                @Override
                public void update(Match match) {
                    // Intentionally empty for test
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
            likeStorage.save(Like.create(b, a, Like.Direction.LIKE)); // This makes it mutual

            // Now record the like again (simulating second thread processing same like or
            // duplicate)
            Like duplicateAction = Like.create(a, b, Like.Direction.LIKE);

            // We expect this to NOT throw. Currently it throws RuntimeException.
            assertDoesNotThrow(
                    () -> service.recordLike(duplicateAction),
                    "Service should handle duplicate match creation gracefully without crashing");

            // Verify likes were recorded
            assertEquals(3, likeStorage.getLikes().size(), "Should have recorded 3 likes total");
        }
    }

    // --- Helpers ---

    private User createActiveUser() {
        User user = new User(UUID.randomUUID(), "Test");
        user.setBio("Bio");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.MALE);
        user.setInterestedIn(EnumSet.of(User.Gender.MALE));
        user.setLocation(0, 0);
        user.setMaxDistanceKm(100);
        user.setAgeRange(18, 99);
        user.addPhotoUrl("http://url");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    // Minimal InMemoryLikeStorage
    static class InMemoryLikeStorage implements LikeStorage {
        private final java.util.List<Like> likes = new java.util.ArrayList<>();

        public java.util.List<Like> getLikes() {
            return Collections.unmodifiableList(likes);
        }

        @Override
        public void save(Like like) {
            likes.add(like);
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return false;
        } // Always allow adding for test

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            return true;
        } // Always say yes for test

        @Override
        public java.util.Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return java.util.Set.of();
        }

        @Override
        public java.util.Map<UUID, java.time.Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            java.util.Map<UUID, java.time.Instant> result = new java.util.HashMap<>();
            for (Like like : likes) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.put(like.whoLikes(), like.createdAt());
                }
            }
            return result;
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
        public int countLikesToday(UUID userId, java.time.Instant startOfDay) {
            return 0;
        }

        @Override
        public int countPassesToday(UUID userId, java.time.Instant startOfDay) {
            return 0;
        }

        @Override
        public void delete(UUID likeId) {
            likes.removeIf(like -> like.id().equals(likeId));
        }
    }
}
