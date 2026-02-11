package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.*;
import datingapp.core.model.Preferences.PacePreferences;
import datingapp.core.model.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.model.Preferences.PacePreferences.DepthPreference;
import datingapp.core.model.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.model.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.service.*;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for pace compatibility calculation in MatchQualityService. Formerly
 * PaceCompatibilityServiceTest.
 */
@SuppressWarnings("unused")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PaceCompatibilityTest {

    private MatchQualityService service;

    @BeforeEach
    void setUp() {
        service = new MatchQualityService(new MinimalUserStorage(), new MinimalLikeStorage(), AppConfig.defaults());
    }

    @Test
    @DisplayName("Perfect match returns 100")
    void perfectMatch_returns100() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);

        assertEquals(100, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Extreme opposites return low score")
    void extremeOpposites_returnsLowScore() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.RARELY,
                TimeToFirstDate.QUICKLY,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.SMALL_TALK);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.CONSTANTLY,
                TimeToFirstDate.MONTHS,
                CommunicationStyle.IN_PERSON_ONLY,
                DepthPreference.EXISTENTIAL);

        // Messaging: dist 2 -> 5
        // Time: dist 3 -> 5
        // Comm: dist 3 -> 5
        // Depth: dist 2 -> 5
        // Total: 20
        assertEquals(20, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Communication style wildcard applies wildcard score")
    void communicationStyleWildcard_appliesWildcardScore() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.MIX_OF_EVERYTHING,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT);

        // Messaging: 25
        // Time: 25
        // Comm: wildcard -> 20
        // Depth: 25
        // Total: 95
        assertEquals(95, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Depth preference wildcard applies wildcard score")
    void depthPreferenceWildcard_appliesWildcardScore() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEPENDS_ON_VIBE);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.SMALL_TALK);

        // Messaging: 25
        // Time: 25
        // Comm: 25
        // Depth: wildcard -> 20
        // Total: 95
        assertEquals(95, service.calculatePaceCompatibility(p1, p2));
    }

    @Test
    @DisplayName("Null preferences return -1")
    void nullPreferences_returnsNegativeOne() {
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.SMALL_TALK);

        // Null PacePreferences should return -1
        assertEquals(-1, service.calculatePaceCompatibility(null, p2));
        assertEquals(-1, service.calculatePaceCompatibility(p2, null));
        assertEquals(-1, service.calculatePaceCompatibility(null, null));
    }

    @Test
    @DisplayName("Low compatibility threshold detects correctly")
    void lowCompatibilityThreshold_detectsCorrectly() {
        assertTrue(service.isLowPaceCompatibility(45));
        assertTrue(service.isLowPaceCompatibility(0));
        assertFalse(service.isLowPaceCompatibility(50));
        assertFalse(service.isLowPaceCompatibility(100));
        assertFalse(service.isLowPaceCompatibility(-1));
    }

    @Test
    @DisplayName("Adjacency logic works correctly")
    void adjacencyLogic_worksCorrectly() {
        PacePreferences p1 = new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.VOICE_NOTES,
                DepthPreference.DEEP_CHAT);
        PacePreferences p2 = new PacePreferences(
                MessagingFrequency.RARELY, // dist 1 -> 15
                TimeToFirstDate.QUICKLY, // dist 1 -> 15
                CommunicationStyle.TEXT_ONLY, // dist 1 -> 15
                DepthPreference.SMALL_TALK // dist 1 -> 15
                );

        assertEquals(60, service.calculatePaceCompatibility(p1, p2));
    }

    // Minimal mock storage implementations for constructor requirements

    private static class MinimalUserStorage implements UserStorage {
        private final Map<String, ProfileNote> profileNotes = new ConcurrentHashMap<>();

        @Override
        public void save(User user) {
            // no-op for test
        }

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
        public void delete(UUID id) {
            // No-op for minimal storage
        }

        @Override
        public void saveProfileNote(ProfileNote note) {
            profileNotes.put(noteKey(note.authorId(), note.subjectId()), note);
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.ofNullable(profileNotes.get(noteKey(authorId, subjectId)));
        }

        @Override
        public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
            return profileNotes.values().stream()
                    .filter(note -> note.authorId().equals(authorId))
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            String key = noteKey(authorId, subjectId);
            return profileNotes.remove(key) != null;
        }

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
        }
    }

    private static class MinimalLikeStorage implements LikeStorage {
        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.empty();
        }

        @Override
        public void save(Like like) {
            // no-op for test
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return false;
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            return false;
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
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            return List.of();
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
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return 0;
        }

        @Override
        public void delete(UUID likeId) {
            // no-op for test
        }
    }
}
