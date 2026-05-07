package datingapp.app.api;

import datingapp.app.api.RestApiUserDtos.UserSummary;
import datingapp.app.usecase.matching.MatchingUseCases.MatchQualitySnapshot;
import datingapp.app.usecase.matching.MatchingUseCases.UndoOutcome;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MatchDtos {
    private static final String UNKNOWN_USER = "Unknown";

    private MatchDtos() {}

    /** Candidate browsing response. */
    static record BrowseCandidatesResponse(
            List<UserSummary> candidates,
            RestApiUserDtos.DailyPickDto dailyPick,
            boolean dailyPickViewed,
            boolean locationMissing) {
        BrowseCandidatesResponse {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        static BrowseCandidatesResponse from(
                datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesResult result,
                java.time.ZoneId userTimeZone) {
            return new BrowseCandidatesResponse(
                    UserSummary.fromUsers(result.candidates(), userTimeZone),
                    result.dailyPick()
                            .map(dailyPick -> RestApiUserDtos.DailyPickDto.from(dailyPick, userTimeZone))
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
        MatchSummary {
            photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
        }

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
                    otherUser != null ? List.copyOf(otherUser.getPhotoUrls()) : List.of(),
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
            List<MatchSummary> matches, int totalCount, int offset, int limit, boolean hasMore) {
        PagedMatchResponse {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    /** Pending likers response. */
    static record PendingLikersResponse(List<RestApiUserDtos.PendingLikerDto> pendingLikers) {
        PendingLikersResponse {
            pendingLikers = pendingLikers == null ? List.of() : List.copyOf(pendingLikers);
        }
    }

    /** Standouts response. */
    static record StandoutsResponse(
            List<RestApiUserDtos.StandoutDto> standouts, int totalCandidates, boolean fromCache, String message) {
        StandoutsResponse {
            standouts = standouts == null ? List.of() : List.copyOf(standouts);
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
        MatchQualityDto {
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
        }

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
}
