package datingapp.app.api;

import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.Standout;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API DTOs and request/response records for the REST API server.
 *
 * <p>
 * Contains all request, response, and data-transfer objects used by REST API
 * endpoints.
 */
final class RestApiDtos {
    private static final String UNKNOWN_USER = "Unknown";
    private static final double APPROXIMATE_COORDINATE_SCALE = 10.0;
    private static final String APPROXIMATE_COORDINATE_FORMAT = "%.1f, %.1f";

    private RestApiDtos() {}

    // ── Response Records ────────────────────────────────────────────────

    /** Health check response. */
    static record HealthResponse(String status, long timestamp) {}

    /** Error response. */
    static record ErrorResponse(String code, String message) {}

    /** Minimal user info for lists. */
    static record UserSummary(UUID id, String name, int age, String state) {
        /**
         * Creates a UserSummary from a User entity.
         * Uses the provided timezone for age calculation.
         */
        static UserSummary from(User user, ZoneId userTimeZone) {
            return new UserSummary(
                    user.getId(), user.getName(),
                    user.getAge(userTimeZone).orElse(0), user.getState().name());
        }
    }

    /** Full user detail for single-user queries. */
    static record UserDetail(
            UUID id,
            String name,
            int age,
            String bio,
            String gender,
            List<String> interestedIn,
            String approximateLocation,
            int maxDistanceKm,
            List<String> photoUrls,
            String state) {
        /**
         * Creates a UserDetail from a User entity.
         * Uses the provided timezone for age calculation.
         */
        static UserDetail from(User user, ZoneId userTimeZone) {
            return new UserDetail(
                    user.getId(),
                    user.getName(),
                    user.getAge(userTimeZone).orElse(0),
                    user.getBio(),
                    user.getGender() != null ? user.getGender().name() : null,
                    user.getInterestedIn().stream().map(Enum::name).toList(),
                    toApproximateLocation(user),
                    user.getMaxDistanceKm(),
                    user.getPhotoUrls(),
                    user.getState().name());
        }
    }

    /** Match summary for API responses. */
    static record MatchSummary(
            String matchId, UUID otherUserId, String otherUserName, String state, Instant createdAt) {
        static MatchSummary from(Match match, UUID currentUserId, Map<UUID, User> usersById) {
            UUID otherUserId = match.getOtherUser(currentUserId);
            User otherUser = usersById.get(otherUserId);
            return new MatchSummary(
                    match.getId(),
                    otherUserId,
                    otherUser != null ? otherUser.getName() : UNKNOWN_USER,
                    match.getState().name(),
                    match.getCreatedAt());
        }
    }

    /** Response for like action. */
    static record LikeResponse(boolean isMatch, String message, MatchSummary match) {}

    /** Response for pass action. */
    static record PassResponse(String message) {}

    /** Response for undo action. */
    static record UndoResponse(boolean success, String message, boolean matchDeleted) {
        static UndoResponse from(datingapp.core.matching.UndoService.UndoResult result) {
            return new UndoResponse(result.success(), result.message(), result.matchDeleted());
        }
    }

    /**
     * Paginated match list response.
     *
     * @param matches    the matches on this page
     * @param totalCount total number of matches across all pages
     * @param offset     zero-based start index of this page
     * @param limit      maximum items per page that was requested
     * @param hasMore    {@code true} if another page exists
     */
    static record PagedMatchResponse(
            List<MatchSummary> matches, int totalCount, int offset, int limit, boolean hasMore) {}

    /** Conversation summary for API responses. */
    static record ConversationSummary(
            String id, UUID otherUserId, String otherUserName, int messageCount, Instant lastMessageAt) {}

    /** Pending liker DTO for API responses. */
    static record PendingLikerDto(UUID userId, String name, int age, Instant likedAt) {
        static PendingLikerDto from(MatchingService.PendingLiker pendingLiker, ZoneId userTimeZone) {
            return new PendingLikerDto(
                    pendingLiker.user().getId(),
                    pendingLiker.user().getName(),
                    pendingLiker.user().getAge(userTimeZone).orElse(0),
                    pendingLiker.likedAt());
        }
    }

    /** Pending likers response. */
    static record PendingLikersResponse(List<PendingLikerDto> pendingLikers) {}

    /** Standout DTO for API responses. */
    static record StandoutDto(
            UUID id,
            UUID standoutUserId,
            String standoutUserName,
            int standoutUserAge,
            int rank,
            int score,
            String reason,
            Instant createdAt,
            Instant interactedAt) {
        static StandoutDto from(Standout standout, Map<UUID, User> usersById, ZoneId userTimeZone) {
            User user = usersById.get(standout.standoutUserId());
            return new StandoutDto(
                    standout.id(),
                    standout.standoutUserId(),
                    user != null ? user.getName() : UNKNOWN_USER,
                    user != null ? user.getAge(userTimeZone).orElse(0) : 0,
                    standout.rank(),
                    standout.score(),
                    standout.reason(),
                    standout.createdAt(),
                    standout.interactedAt());
        }
    }

    /** Standouts response. */
    static record StandoutsResponse(
            List<StandoutDto> standouts, int totalCandidates, boolean fromCache, String message) {}

    /** Notification DTO. */
    static record NotificationDto(
            UUID id,
            String type,
            String title,
            String message,
            Instant createdAt,
            boolean isRead,
            Map<String, String> data) {
        static NotificationDto from(Notification notification) {
            return new NotificationDto(
                    notification.id(),
                    notification.type().name(),
                    notification.title(),
                    notification.message(),
                    notification.createdAt(),
                    notification.isRead(),
                    notification.data());
        }
    }

    /** Mark-all-notifications-read response. */
    static record MarkAllNotificationsReadResponse(int updatedCount) {}

    /** Message DTO for API responses. */
    static record MessageDto(UUID id, String conversationId, UUID senderId, String content, Instant sentAt) {
        static MessageDto from(Message message) {
            return new MessageDto(
                    message.id(), message.conversationId(), message.senderId(), message.content(), message.createdAt());
        }
    }

    /** Request body for sending a message. */
    static record SendMessageRequest(UUID senderId, String content) {}

    /** Request body for archiving a conversation. */
    static record ArchiveConversationRequest(Match.MatchArchiveReason reason) {}

    /** Request body for profile updates. */
    static record ProfileUpdateRequest(
            String bio,
            java.time.LocalDate birthDate,
            User.Gender gender,
            Set<User.Gender> interestedIn,
            Double latitude,
            Double longitude,
            Integer maxDistanceKm,
            Integer minAge,
            Integer maxAge,
            Integer heightCm,
            Lifestyle.Smoking smoking,
            Lifestyle.Drinking drinking,
            Lifestyle.WantsKids wantsKids,
            Lifestyle.LookingFor lookingFor,
            Lifestyle.Education education,
            Set<Interest> interests,
            Dealbreakers dealbreakers) {}

    /** Response body for profile updates. */
    static record ProfileUpdateResponse(
            UUID id,
            String name,
            int age,
            String bio,
            String gender,
            List<String> interestedIn,
            String approximateLocation,
            int maxDistanceKm,
            String state,
            boolean activated) {
        static ProfileUpdateResponse from(User user, boolean activated, ZoneId userTimeZone) {
            return new ProfileUpdateResponse(
                    user.getId(),
                    user.getName(),
                    user.getAge(userTimeZone).orElse(0),
                    user.getBio(),
                    user.getGender() != null ? user.getGender().name() : null,
                    user.getInterestedIn().stream().map(Enum::name).toList(),
                    toApproximateLocation(user),
                    user.getMaxDistanceKm(),
                    user.getState().name(),
                    activated);
        }
    }

    /** Match quality response. */
    static record MatchQualityDto(
            String matchId,
            UUID perspectiveUserId,
            UUID otherUserId,
            int compatibilityScore,
            String compatibilityLabel,
            String starDisplay,
            String paceSyncLevel,
            double distanceKm,
            int ageDifference,
            List<String> highlights) {
        static MatchQualityDto from(MatchQualityService.MatchQuality quality) {
            return new MatchQualityDto(
                    quality.matchId(),
                    quality.perspectiveUserId(),
                    quality.otherUserId(),
                    quality.compatibilityScore(),
                    quality.getCompatibilityLabel(),
                    quality.getStarDisplay(),
                    quality.paceSyncLevel(),
                    quality.distanceKm(),
                    quality.ageDifference(),
                    quality.highlights());
        }
    }

    /** Response for relationship transitions. */
    static record TransitionResponse(boolean success, UUID friendRequestId, String errorMessage) {
        static TransitionResponse from(ConnectionService.TransitionResult result) {
            FriendRequest request = result.friendRequest();
            return new TransitionResponse(
                    result.success(), request != null ? request.id() : null, result.errorMessage());
        }
    }

    /** Response for moderation operations. */
    static record ModerationResponse(boolean success, boolean alreadyHandled, String errorMessage) {}

    /** Response for reporting a user. */
    static record ReportResponse(boolean success, boolean autoBanned, String errorMessage, boolean blockedByReporter) {}

    /** User stats DTO. */
    static record UserStatsDto(
            UUID userId,
            Instant computedAt,
            int totalSwipesGiven,
            int likesGiven,
            int passesGiven,
            String likeRatio,
            int totalSwipesReceived,
            int likesReceived,
            int passesReceived,
            String incomingLikeRatio,
            int totalMatches,
            int activeMatches,
            String matchRate,
            int blocksGiven,
            int blocksReceived,
            int reportsGiven,
            int reportsReceived,
            String reciprocityScore,
            double selectivenessScore,
            double attractivenessScore) {
        static UserStatsDto from(UserStats stats) {
            return new UserStatsDto(
                    stats.userId(),
                    stats.computedAt(),
                    stats.totalSwipesGiven(),
                    stats.likesGiven(),
                    stats.passesGiven(),
                    stats.getLikeRatioDisplay(),
                    stats.totalSwipesReceived(),
                    stats.likesReceived(),
                    stats.passesReceived(),
                    stats.getIncomingLikeRatioDisplay(),
                    stats.totalMatches(),
                    stats.activeMatches(),
                    stats.getMatchRateDisplay(),
                    stats.blocksGiven(),
                    stats.blocksReceived(),
                    stats.reportsGiven(),
                    stats.reportsReceived(),
                    stats.getReciprocityDisplay(),
                    stats.selectivenessScore(),
                    stats.attractivenessScore());
        }
    }

    private static String toApproximateLocation(User user) {
        if (user == null || !user.hasLocationSet()) {
            return null;
        }
        double coarseLat = coarsenCoordinate(user.getLat());
        double coarseLon = coarsenCoordinate(user.getLon());
        return String.format(Locale.ENGLISH, APPROXIMATE_COORDINATE_FORMAT, coarseLat, coarseLon);
    }

    private static double coarsenCoordinate(double value) {
        return Math.round(value * APPROXIMATE_COORDINATE_SCALE) / APPROXIMATE_COORDINATE_SCALE;
    }

    /** Achievement unlocked DTO. */
    static record AchievementUnlockedDto(
            UUID id,
            String achievementName,
            String description,
            String icon,
            String iconLiteral,
            String category,
            int xp,
            Instant unlockedAt) {
        static AchievementUnlockedDto from(UserAchievement achievement) {
            return new AchievementUnlockedDto(
                    achievement.id(),
                    achievement.achievement().getDisplayName(),
                    achievement.achievement().getDescription(),
                    achievement.achievement().getIcon(),
                    achievement.achievement().getIconLiteral(),
                    achievement.achievement().getCategory().name(),
                    achievement.achievement().getXp(),
                    achievement.unlockedAt());
        }
    }

    /** Achievement snapshot DTO. */
    static record AchievementSnapshotDto(
            List<AchievementUnlockedDto> unlocked,
            List<AchievementUnlockedDto> newlyUnlocked,
            int unlockedCount,
            int newlyUnlockedCount) {
        static AchievementSnapshotDto from(datingapp.app.usecase.profile.ProfileUseCases.AchievementSnapshot snapshot) {
            return new AchievementSnapshotDto(
                    snapshot.unlocked().stream()
                            .map(AchievementUnlockedDto::from)
                            .toList(),
                    snapshot.newlyUnlocked().stream()
                            .map(AchievementUnlockedDto::from)
                            .toList(),
                    snapshot.unlocked().size(),
                    snapshot.newlyUnlocked().size());
        }
    }

    /** Private profile note DTO. */
    static record ProfileNoteDto(UUID authorId, UUID subjectId, String content, Instant createdAt, Instant updatedAt) {
        static ProfileNoteDto from(ProfileNote note) {
            return new ProfileNoteDto(
                    note.authorId(), note.subjectId(), note.content(), note.createdAt(), note.updatedAt());
        }
    }

    /** Request body for creating or updating a private profile note. */
    static record ProfileNoteUpsertRequest(String content) {}

    /** Request body for reporting a user. */
    static record ReportUserRequest(Report.Reason reason, String description, boolean blockUser) {}
}
