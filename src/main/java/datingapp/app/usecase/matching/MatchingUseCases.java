package datingapp.app.usecase.matching;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.RecommendationService.DailyPick;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Candidate browsing, swiping, undo, and standout/match orchestration use-cases. */
public class MatchingUseCases {

    private static final String CONTEXT_REQUIRED = "Context is required";

    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final RecommendationService recommendationService;
    private final UndoService undoService;
    private final InteractionStorage interactionStorage;
    private final UserStorage userStorage;
    private final MatchQualityService matchQualityService;
    private final AppEventBus eventBus;

    public MatchingUseCases(CandidateFinder candidateFinder, MatchingService matchingService, UndoService undoService) {
        this(candidateFinder, matchingService, null, undoService, null, null, null, null);
    }

    public MatchingUseCases(
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            RecommendationService recommendationService,
            UndoService undoService,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            MatchQualityService matchQualityService) {
        this(
                candidateFinder,
                matchingService,
                recommendationService,
                undoService,
                interactionStorage,
                userStorage,
                matchQualityService,
                null);
    }

    public MatchingUseCases(
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            RecommendationService recommendationService,
            UndoService undoService,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            MatchQualityService matchQualityService,
            AppEventBus eventBus) {
        this.candidateFinder = candidateFinder;
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
        this.recommendationService = recommendationService;
        this.undoService = undoService;
        this.interactionStorage = interactionStorage;
        this.userStorage = userStorage;
        this.matchQualityService = matchQualityService;
        this.eventBus = eventBus;
    }

    public UseCaseResult<BrowseCandidatesResult> browseCandidates(BrowseCandidatesCommand command) {
        if (command == null || command.context() == null || command.currentUser() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and current user are required"));
        }
        User currentUser = command.currentUser();
        if (currentUser.getState() != UserState.ACTIVE) {
            return UseCaseResult.failure(UseCaseError.conflict("User must be ACTIVE to browse candidates"));
        }
        if (candidateFinder == null) {
            return UseCaseResult.failure(UseCaseError.dependency("CandidateFinder is not configured"));
        }

        try {
            Optional<DailyPick> dailyPick = Optional.empty();
            boolean dailyPickViewed = true;
            if (recommendationService != null) {
                dailyPick = recommendationService.getDailyPick(currentUser);
                dailyPickViewed = recommendationService.hasViewedDailyPick(currentUser.getId());
            }

            if (!currentUser.hasLocationSet()) {
                return UseCaseResult.success(new BrowseCandidatesResult(List.of(), dailyPick, dailyPickViewed, true));
            }

            List<User> candidates = candidateFinder.findCandidatesForUser(currentUser);
            return UseCaseResult.success(new BrowseCandidatesResult(candidates, dailyPick, dailyPickViewed, false));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to browse candidates: " + e.getMessage()));
        }
    }

    public UseCaseResult<MatchingService.SwipeResult> processSwipe(ProcessSwipeCommand command) {
        if (command == null
                || command.context() == null
                || command.currentUser() == null
                || command.candidate() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, current user and candidate are required"));
        }
        try {
            if (command.markDailyPickViewed() && recommendationService != null) {
                recommendationService.markDailyPickViewed(command.context().userId());
            }
            MatchingService.SwipeResult result =
                    matchingService.processSwipe(command.currentUser(), command.candidate(), command.liked());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.message()));
            }
            if (eventBus != null) {
                eventBus.publish(new AppEvent.SwipeRecorded(
                        command.context().userId(),
                        command.candidate().getId(),
                        result.like().direction().name(),
                        result.matched(),
                        AppClock.now()));
                if (result.matched()) {
                    Match m = result.match();
                    Objects.requireNonNull(m, "match cannot be null when matched() is true");
                    eventBus.publish(new AppEvent.MatchCreated(
                            m.getId(), result.like().whoLikes(), result.like().whoGotLiked(), AppClock.now()));
                }
            }
            return UseCaseResult.success(result);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to process swipe: " + e.getMessage()));
        }
    }

    public UseCaseResult<UndoService.UndoResult> undoSwipe(UndoSwipeCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (undoService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("UndoService is not configured"));
        }

        UUID userId = command.context().userId();
        if (!undoService.canUndo(userId)) {
            return UseCaseResult.failure(UseCaseError.conflict("No recent swipe to undo"));
        }
        try {
            UndoService.UndoResult result = undoService.undo(userId);
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.message()));
            }
            return UseCaseResult.success(result);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to undo swipe: " + e.getMessage()));
        }
    }

    public UseCaseResult<ActiveMatchesResult> listActiveMatches(ListActiveMatchesQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (interactionStorage == null || userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency("InteractionStorage and UserStorage are required"));
        }

        try {
            UUID userId = query.context().userId();
            List<Match> matches = interactionStorage.getActiveMatchesFor(userId);
            Set<UUID> otherIds = new HashSet<>();
            for (Match match : matches) {
                otherIds.add(match.getOtherUser(userId));
            }
            Map<UUID, User> usersById = userStorage.findByIds(otherIds);
            return UseCaseResult.success(new ActiveMatchesResult(matches, usersById));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to list active matches: " + e.getMessage()));
        }
    }

    public UseCaseResult<List<MatchingService.PendingLiker>> pendingLikers(PendingLikersQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        try {
            return UseCaseResult.success(
                    matchingService.findPendingLikersWithTimes(query.context().userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load pending likers: " + e.getMessage()));
        }
    }

    public UseCaseResult<StandoutsResult> standouts(StandoutsQuery query) {
        if (query == null || query.context() == null || query.currentUser() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and current user are required"));
        }
        if (recommendationService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("RecommendationService is not configured"));
        }
        try {
            RecommendationService.Result result = recommendationService.getStandouts(query.currentUser());
            Map<UUID, User> users = recommendationService.resolveUsers(result.standouts());
            return UseCaseResult.success(new StandoutsResult(result, users));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load standouts: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> markStandoutInteracted(StandoutInteractionCommand command) {
        if (command == null || command.context() == null || command.standoutUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and standout user are required"));
        }
        if (recommendationService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("RecommendationService is not configured"));
        }
        try {
            recommendationService.markInteracted(command.context().userId(), command.standoutUserId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to mark standout interaction: " + e.getMessage()));
        }
    }

    public UseCaseResult<RecordLikeResult> recordLike(RecordLikeCommand command) {
        if (command == null
                || command.context() == null
                || command.targetUserId() == null
                || command.direction() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, target user and direction are required"));
        }
        if (command.enforceDailyLimit()
                && command.direction() == Like.Direction.LIKE
                && recommendationService != null
                && !recommendationService.canLike(command.context().userId())) {
            return UseCaseResult.failure(UseCaseError.conflict("Daily like limit reached"));
        }

        try {
            Like like = Like.create(command.context().userId(), command.targetUserId(), command.direction());
            Optional<Match> match = matchingService.recordLike(like);
            if (eventBus != null) {
                eventBus.publish(new AppEvent.SwipeRecorded(
                        like.whoLikes(),
                        like.whoGotLiked(),
                        like.direction().name(),
                        match.isPresent(),
                        AppClock.now()));
                if (match.isPresent()) {
                    Match m = match.get();
                    eventBus.publish(
                            new AppEvent.MatchCreated(m.getId(), like.whoLikes(), like.whoGotLiked(), AppClock.now()));
                }
            }
            return UseCaseResult.success(new RecordLikeResult(like, match));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to record like interaction: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> removeLike(RemoveLikeCommand command) {
        if (command == null || command.context() == null || command.likeId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and likeId are required"));
        }
        if (interactionStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency("InteractionStorage is not configured"));
        }
        try {
            interactionStorage.delete(command.likeId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to remove like: " + e.getMessage()));
        }
    }

    public UseCaseResult<MatchQualityService.MatchQuality> matchQuality(MatchQualityQuery query) {
        if (query == null || query.context() == null || query.match() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and match are required"));
        }
        if (matchQualityService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("MatchQualityService is not configured"));
        }
        try {
            Optional<MatchQualityService.MatchQuality> quality = matchQualityService.computeQuality(
                    query.match(), query.context().userId());
            if (quality.isEmpty()) {
                return UseCaseResult.failure(UseCaseError.notFound("Match quality unavailable"));
            }
            return UseCaseResult.success(quality.get());
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to compute match quality: " + e.getMessage()));
        }
    }

    public static record BrowseCandidatesCommand(UserContext context, User currentUser) {}

    public static record BrowseCandidatesResult(
            List<User> candidates, Optional<DailyPick> dailyPick, boolean dailyPickViewed, boolean locationMissing) {}

    public static record ProcessSwipeCommand(
            UserContext context, User currentUser, User candidate, boolean liked, boolean markDailyPickViewed) {}

    public static record UndoSwipeCommand(UserContext context) {}

    public static record ListActiveMatchesQuery(UserContext context) {}

    public static record ActiveMatchesResult(List<Match> matches, Map<UUID, User> usersById) {}

    public static record PendingLikersQuery(UserContext context) {}

    public static record StandoutsQuery(UserContext context, User currentUser) {}

    public static record StandoutsResult(RecommendationService.Result result, Map<UUID, User> usersById) {}

    public static record StandoutInteractionCommand(UserContext context, UUID standoutUserId) {}

    public static record RecordLikeCommand(
            UserContext context, UUID targetUserId, Like.Direction direction, boolean enforceDailyLimit) {}

    public static record RecordLikeResult(Like like, Optional<Match> match) {}

    public static record RemoveLikeCommand(UserContext context, UUID likeId) {}

    public static record MatchQualityQuery(UserContext context, Match match) {}
}
