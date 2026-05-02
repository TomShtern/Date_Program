package datingapp.app.api;

import datingapp.app.api.RestApiUserDtos.DailyPickDto;
import datingapp.app.api.RestApiUserDtos.UserSummary;
import datingapp.app.usecase.auth.AuthUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.MatchQualitySnapshot;
import datingapp.app.usecase.matching.MatchingUseCases.UndoOutcome;
import datingapp.app.usecase.profile.VerificationUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipTransitionOutcome;
import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.profile.LocationService.SelectionSeed;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    private RestApiDtos() {}

    // ── Response Records ────────────────────────────────────────────────

    /** Health check response. */
    static record HealthResponse(String status, long timestamp) {}

    /** Error response. */
    static record ErrorResponse(String code, String message) {}

    /** Request body for signup. */
    static record SignupRequest(String email, String password, LocalDate dateOfBirth) {}

    /** Request body for login. */
    static record LoginRequest(String email, String password) {}

    /** Request body for refresh/logout token flows. */
    static record RefreshTokenRequest(String refreshToken) {}

    /** Authenticated user DTO. */
    static record AuthUserDto(UUID id, String email, String displayName, String profileCompletionState) {
        static AuthUserDto from(AuthUseCases.AuthUser user) {
            return new AuthUserDto(user.id(), user.email(), user.displayName(), user.profileCompletionState());
        }
    }

    /** Auth session response. */
    static record AuthResponse(String accessToken, String refreshToken, int expiresInSeconds, AuthUserDto user) {
        static AuthResponse from(AuthUseCases.AuthSession session) {
            return new AuthResponse(
                    session.accessToken(),
                    session.refreshToken(),
                    session.expiresInSeconds(),
                    AuthUserDto.from(session.user()));
        }
    }

    /** Country DTO for location metadata responses. */
    static record LocationCountryDto(
            String code, String name, String flagEmoji, boolean available, boolean defaultSelection) {
        static LocationCountryDto from(Country country) {
            return new LocationCountryDto(
                    country.code(),
                    country.name(),
                    country.flagEmoji(),
                    country.available(),
                    country.defaultSelection());
        }
    }

    /** City DTO for location metadata responses. */
    static record LocationCityDto(String name, String district, String countryCode, int priority) {
        static LocationCityDto from(City city) {
            return new LocationCityDto(city.name(), city.district(), city.countryCode(), city.priority());
        }
    }

    /** Request body for resolving a location selection. */
    static record LocationResolveRequest(
            String countryCode, String cityName, String zipCode, Boolean allowApproximate) {}

    /** Response body for resolving a location selection. */
    static record LocationResolveResponse(
            String label, double latitude, double longitude, String precision, boolean approximate, String message) {
        static LocationResolveResponse from(ResolvedLocation location, boolean approximate, String message) {
            return new LocationResolveResponse(
                    location.label(),
                    location.latitude(),
                    location.longitude(),
                    location.precision().name(),
                    approximate,
                    message == null ? "" : message);
        }
    }

    /** Nested location input for selection-based profile updates. */
    static record ProfileLocationRequest(
            String countryCode, String cityName, String zipCode, Boolean allowApproximate) {}

    /** Candidate browsing response. */
    static record BrowseCandidatesResponse(
            List<UserSummary> candidates, DailyPickDto dailyPick, boolean dailyPickViewed, boolean locationMissing) {
        static BrowseCandidatesResponse from(
                datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesResult result, ZoneId userTimeZone) {
            return new BrowseCandidatesResponse(
                    UserSummary.fromUsers(result.candidates(), userTimeZone),
                    result.dailyPick()
                            .map(dailyPick -> DailyPickDto.from(dailyPick, userTimeZone))
                            .orElse(null),
                    result.dailyPickViewed(),
                    result.locationMissing());
        }
    }

    /** Match summary for API responses. */
    static record MatchSummary(
            String matchId,
            UUID otherUserId,
            String otherUserName,
            String state,
            Instant createdAt,
            String primaryPhotoUrl,
            List<String> photoUrls,
            String approximateLocation,
            String summaryLine) {
        static MatchSummary from(Match match, UUID currentUserId, Map<UUID, User> usersById) {
            return from(match, currentUserId, usersById, null);
        }

        static MatchSummary from(
                Match match, UUID currentUserId, Map<UUID, User> usersById, String approximateLocation) {
            UUID otherUserId = match.getOtherUser(currentUserId);
            User otherUser = usersById.get(otherUserId);
            return new MatchSummary(
                    match.getId(),
                    otherUserId,
                    otherUser != null ? otherUser.getName() : UNKNOWN_USER,
                    match.getState().name(),
                    match.getCreatedAt(),
                    RestApiUserDtos.personPrimaryPhotoUrl(otherUser),
                    otherUser != null ? otherUser.getPhotoUrls() : List.of(),
                    approximateLocation,
                    RestApiUserDtos.personSummaryLine(otherUser));
        }
    }

    /** Response for like action. */
    static record LikeResponse(boolean isMatch, String message, MatchSummary match) {}

    /** Response for pass action. */
    static record PassResponse(String message) {}

    /** Response for undo action. */
    static record UndoResponse(boolean success, String message, boolean matchDeleted) {
        static UndoResponse from(UndoOutcome outcome) {
            return new UndoResponse(true, outcome.message(), outcome.matchDeleted());
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

    /** Pending likers response. */
    static record PendingLikersResponse(List<RestApiUserDtos.PendingLikerDto> pendingLikers) {}

    /** Friend request DTO for API responses. */
    static record FriendRequestDto(
            UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, String status, Instant respondedAt) {
        static FriendRequestDto from(FriendRequest request) {
            return new FriendRequestDto(
                    request.id(),
                    request.fromUserId(),
                    request.toUserId(),
                    request.createdAt(),
                    request.status().name(),
                    request.respondedAt());
        }
    }

    /** Friend request listing response. */
    static record FriendRequestsResponse(List<FriendRequestDto> friendRequests) {
        static FriendRequestsResponse from(List<FriendRequest> requests) {
            return new FriendRequestsResponse(
                    requests.stream().map(FriendRequestDto::from).toList());
        }
    }

    /** Standouts response. */
    static record StandoutsResponse(
            List<RestApiUserDtos.StandoutDto> standouts, int totalCandidates, boolean fromCache, String message) {}

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
            LocalDate birthDate,
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
            Dealbreakers dealbreakers,
            ProfileLocationRequest location) {}

    static record PresentationContextDto(
            UUID viewerUserId,
            UUID targetUserId,
            String summary,
            List<String> reasonTags,
            List<String> details,
            Instant generatedAt) {
        static PresentationContextDto from(User viewer, User target, String targetLocation, ZoneId userTimeZone) {
            List<String> tags = new ArrayList<>();
            List<String> details = new ArrayList<>();

            Set<Interest> sharedInterests = new HashSet<>(viewer.getInterests());
            sharedInterests.retainAll(target.getInterests());
            if (!sharedInterests.isEmpty()) {
                tags.add("shared_interests");
                String interestText =
                        sharedInterests.stream().map(Interest::getDisplayName).sorted().limit(3).toList().stream()
                                .reduce((left, right) -> left + " and " + right)
                                .orElse("interests");
                details.add("You both list " + interestText + " as interests.");
            }

            if (viewer.hasLocation() && target.hasLocation()) {
                tags.add("nearby");
                details.add(
                        targetLocation != null
                                ? "This profile is near " + targetLocation + "."
                                : "This profile is within your eligible match area.");
            }

            Integer viewerAge = viewer.getAge(userTimeZone).orElse(null);
            Integer targetAge = target.getAge(userTimeZone).orElse(null);
            if (viewerAge != null
                    && targetAge != null
                    && targetAge >= viewer.getMinAge()
                    && targetAge <= viewer.getMaxAge()) {
                tags.add("age_compatible");
                details.add("This profile is within your preferred age range.");
            }

            if (viewer.getLookingFor() != null && viewer.getLookingFor() == target.getLookingFor()) {
                tags.add("same_relationship_goals");
                details.add("You are both looking for " + viewer.getLookingFor().getDisplayName() + ".");
            }

            if (tags.isEmpty()) {
                tags.add("eligible_match_pool");
                details.add("This profile is currently eligible for your match pool.");
            }

            String summary =
                    switch (tags.getFirst()) {
                        case "shared_interests" -> "Shown because you share interests with this profile.";
                        case "nearby" -> "Shown because this profile is nearby.";
                        case "age_compatible" -> "Shown because this profile fits your current preferences.";
                        case "same_relationship_goals" -> "Shown because your relationship goals line up.";
                        default -> "Shown because this profile is eligible for your current match pool.";
                    };

            return new PresentationContextDto(
                    viewer.getId(), target.getId(), summary, List.copyOf(tags), List.copyOf(details), AppClock.now());
        }
    }

    static record ProfileEditSnapshotDto(UUID userId, ProfileEditableDto editable, ProfileReadOnlyDto readOnly) {
        static ProfileEditSnapshotDto from(User user, SelectionSeed seed) {
            return new ProfileEditSnapshotDto(
                    user.getId(), ProfileEditableDto.from(user, seed), ProfileReadOnlyDto.from(user));
        }
    }

    static record ProfileEditableDto(
            String bio,
            LocalDate birthDate,
            String gender,
            List<String> interestedIn,
            int maxDistanceKm,
            int minAge,
            int maxAge,
            Integer heightCm,
            String smoking,
            String drinking,
            String wantsKids,
            String lookingFor,
            String education,
            List<String> interests,
            DealbreakersDto dealbreakers,
            ProfileEditLocationDto location) {
        static ProfileEditableDto from(User user, SelectionSeed seed) {
            return new ProfileEditableDto(
                    user.getBio(),
                    user.getBirthDate(),
                    user.getGender() != null ? user.getGender().name() : null,
                    enumNames(user.getInterestedIn()),
                    user.getMaxDistanceKm(),
                    user.getMinAge(),
                    user.getMaxAge(),
                    user.getHeightCm(),
                    user.getSmoking() != null ? user.getSmoking().name() : null,
                    user.getDrinking() != null ? user.getDrinking().name() : null,
                    user.getWantsKids() != null ? user.getWantsKids().name() : null,
                    user.getLookingFor() != null ? user.getLookingFor().name() : null,
                    user.getEducation() != null ? user.getEducation().name() : null,
                    enumNames(user.getInterests()),
                    DealbreakersDto.from(user.getDealbreakers()),
                    ProfileEditLocationDto.from(seed));
        }
    }

    static record DealbreakersDto(
            List<String> acceptableSmoking,
            List<String> acceptableDrinking,
            List<String> acceptableKidsStance,
            List<String> acceptableLookingFor,
            List<String> acceptableEducation,
            Integer minHeightCm,
            Integer maxHeightCm,
            Integer maxAgeDifference) {
        static DealbreakersDto from(Dealbreakers dealbreakers) {
            Dealbreakers safe = dealbreakers != null ? dealbreakers : Dealbreakers.none();
            return new DealbreakersDto(
                    enumNames(safe.acceptableSmoking()),
                    enumNames(safe.acceptableDrinking()),
                    enumNames(safe.acceptableKidsStance()),
                    enumNames(safe.acceptableLookingFor()),
                    enumNames(safe.acceptableEducation()),
                    safe.minHeightCm(),
                    safe.maxHeightCm(),
                    safe.maxAgeDifference());
        }
    }

    static record ProfileEditLocationDto(
            String label,
            double latitude,
            double longitude,
            String precision,
            String countryCode,
            String cityName,
            String zipCode,
            boolean approximate) {
        static ProfileEditLocationDto from(SelectionSeed seed) {
            if (seed == null) {
                return null;
            }
            ResolvedLocation location = seed.resolvedLocation();
            return new ProfileEditLocationDto(
                    location.label(),
                    location.latitude(),
                    location.longitude(),
                    location.precision().name(),
                    seed.country().code(),
                    seed.city().map(City::name).orElse(null),
                    seed.zipPrefix().orElse(null),
                    false);
        }
    }

    static record ProfileReadOnlyDto(
            String name,
            String state,
            List<String> photoUrls,
            boolean verified,
            String verificationMethod,
            Instant verifiedAt) {
        static ProfileReadOnlyDto from(User user) {
            return new ProfileReadOnlyDto(
                    user.getName(),
                    user.getState().name(),
                    user.getPhotoUrls(),
                    user.isVerified(),
                    user.getVerificationMethod() != null
                            ? user.getVerificationMethod().name()
                            : null,
                    user.getVerifiedAt());
        }
    }

    private static List<String> enumNames(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(Enum::name).sorted().toList();
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
        static MatchQualityDto from(MatchQualitySnapshot quality) {
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
        static TransitionResponse from(RelationshipTransitionOutcome outcome) {
            FriendRequest request = outcome.friendRequest();
            return new TransitionResponse(true, request != null ? request.id() : null, null);
        }
    }

    /** Response for moderation operations. */
    static record ModerationResponse(boolean success, boolean alreadyHandled, String errorMessage) {}

    /** Blocked user DTO for safety-related responses. */
    static record BlockedUserDto(UUID userId, String name, String statusLabel) {
        static BlockedUserDto from(datingapp.app.usecase.social.SocialUseCases.BlockedUserSummary blockedUser) {
            return new BlockedUserDto(blockedUser.userId(), blockedUser.name(), "Blocked profile");
        }
    }

    /** Blocked users response. */
    static record BlockedUsersResponse(List<BlockedUserDto> blockedUsers) {
        static BlockedUsersResponse from(
                List<datingapp.app.usecase.social.SocialUseCases.BlockedUserSummary> blockedUsers) {
            return new BlockedUsersResponse(
                    blockedUsers.stream().map(BlockedUserDto::from).toList());
        }
    }

    /** Response for reporting a user. */
    static record ReportResponse(boolean success, boolean autoBanned, String errorMessage, boolean blockedByReporter) {}

    /** Request body for starting verification. */
    static record StartVerificationRequest(User.VerificationMethod method, String contact) {}

    /** Response body for starting verification. */
    static record StartVerificationResponse(UUID userId, String method, String contact, String devVerificationCode) {
        static StartVerificationResponse from(VerificationUseCases.StartVerificationResult result) {
            User user = result.user();
            User.VerificationMethod method = user.getVerificationMethod();
            String contact = method == User.VerificationMethod.PHONE ? user.getPhone() : user.getEmail();
            return new StartVerificationResponse(
                    user.getId(), method != null ? method.name() : null, contact, result.generatedCode());
        }
    }

    /** Request body for confirming verification. */
    static record ConfirmVerificationRequest(String verificationCode) {}

    /** Response body for confirming verification. */
    static record ConfirmVerificationResponse(boolean verified, Instant verifiedAt) {
        static ConfirmVerificationResponse from(VerificationUseCases.ConfirmVerificationResult result) {
            return new ConfirmVerificationResponse(
                    result.user().isVerified(), result.user().getVerifiedAt());
        }
    }

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
        static AchievementSnapshotDto from(
                datingapp.app.usecase.profile.ProfileInsightsUseCases.AchievementSnapshot snapshot) {
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

    // ── Photo DTOs ──────────────────────────────────────────────────────

    /** A single uploaded photo reference returned after upload. */
    static record PhotoRef(String id, String url) {}

    /** Response body returned after a successful photo upload. */
    static record PhotoUploadResponse(PhotoRef photo, String primaryPhotoUrl, List<String> photoUrls) {}

    /** Response body returned after a photo delete or photo reorder. */
    static record PhotoMutationResponse(String primaryPhotoUrl, List<String> photoUrls) {}

    /** Request body for reordering a user's photos. */
    static record PhotoOrderRequest(List<String> photoIds) {}

    /** Response body returned when listing a user's photos. */
    static record PhotoListResponse(String primaryUrl, List<PhotoRef> photos) {}
}
