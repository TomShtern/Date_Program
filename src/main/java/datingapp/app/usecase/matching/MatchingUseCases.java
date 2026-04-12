package datingapp.app.usecase.matching;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.DailyPickService.DailyPick;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.PageData;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Candidate browsing, swiping, undo, and standout/match orchestration use-cases. */
@SuppressWarnings("java:S6539")
public class MatchingUseCases {

    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final DailyLimitService NO_OP_DAILY_LIMIT_SERVICE = new DailyLimitService() {
        @Override
        public boolean canLike(UUID userId) {
            return true;
        }

        @Override
        public boolean canSuperLike(UUID userId) {
            return true;
        }

        @Override
        public boolean canPass(UUID userId) {
            return true;
        }

        @Override
        public DailyStatus getStatus(UUID userId) {
            return new DailyStatus(0, -1, 0, -1, 0, -1, AppClock.today(), AppClock.now());
        }

        @Override
        public Duration getTimeUntilReset() {
            return Duration.ZERO;
        }
    };
    private static final DailyPickService NO_OP_DAILY_PICK_SERVICE = new DailyPickService() {
        @Override
        public Optional<DailyPick> getDailyPick(User seeker) {
            return Optional.empty();
        }

        @Override
        public boolean hasViewedDailyPick(UUID userId) {
            return false;
        }

        @Override
        public void markDailyPickViewed(UUID userId) {
            // Compatibility shim intentionally does nothing.
        }

        @Override
        public int cleanupOldDailyPickViews(java.time.LocalDate before) {
            return 0;
        }
    };
    private static final StandoutService NO_OP_STANDOUT_SERVICE = new StandoutService() {
        @Override
        public Result getStandouts(User seeker) {
            return Result.empty("No standout service configured");
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId) {
            // Compatibility shim intentionally does nothing.
        }

        @Override
        public Map<UUID, User> resolveUsers(List<Standout> standouts) {
            return Map.of();
        }
    };

    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final DailyLimitService dailyLimitService;
    private final DailyPickService dailyPickService;
    private final StandoutService standoutService;
    private final UndoService undoService;
    private final InteractionStorage interactionStorage;
    private final UserStorage userStorage;
    private final MatchQualityService matchQualityService;
    private final AppEventBus eventBus;
    private final RecommendationService recommendationService;

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("java:S107")
    public MatchingUseCases(
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            DailyLimitService dailyLimitService,
            DailyPickService dailyPickService,
            StandoutService standoutService,
            UndoService undoService,
            InteractionStorage interactionStorage,
            UserStorage userStorage,
            MatchQualityService matchQualityService,
            AppEventBus eventBus,
            RecommendationService recommendationService) {
        this.candidateFinder = Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
        this.dailyLimitService = Objects.requireNonNull(dailyLimitService, "dailyLimitService cannot be null");
        this.dailyPickService = Objects.requireNonNull(dailyPickService, "dailyPickService cannot be null");
        this.standoutService = Objects.requireNonNull(standoutService, "standoutService cannot be null");
        this.undoService = Objects.requireNonNull(undoService, "undoService cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.matchQualityService = Objects.requireNonNull(matchQualityService, "matchQualityService cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.recommendationService =
                Objects.requireNonNull(recommendationService, "recommendationService cannot be null");
    }

    public static final class Builder {
        private CandidateFinder candidateFinder;
        private MatchingService matchingService;
        private DailyLimitService dailyLimitService;
        private DailyPickService dailyPickService;
        private StandoutService standoutService;
        private UndoService undoService;
        private InteractionStorage interactionStorage;
        private UserStorage userStorage;
        private MatchQualityService matchQualityService;
        private AppEventBus eventBus;
        private RecommendationService recommendationService;

        private Builder() {}

        public Builder candidateFinder(CandidateFinder candidateFinder) {
            this.candidateFinder = candidateFinder;
            return this;
        }

        public Builder matchingService(MatchingService matchingService) {
            this.matchingService = matchingService;
            return this;
        }

        /**
         * Compatibility hook for callers that still provide a single recommendation service.
         * Explicit seam setters remain authoritative; this only seeds any missing seams.
         */
        public Builder recommendationService(RecommendationService recommendationService) {
            this.recommendationService = recommendationService;
            if (this.dailyLimitService == null) {
                this.dailyLimitService = wrapDailyLimitService(recommendationService);
            }
            if (this.dailyPickService == null) {
                this.dailyPickService = wrapDailyPickService(recommendationService);
            }
            if (this.standoutService == null) {
                this.standoutService = wrapStandoutService(recommendationService);
            }
            return this;
        }

        public Builder dailyLimitService(DailyLimitService dailyLimitService) {
            this.dailyLimitService = dailyLimitService;
            return this;
        }

        public Builder dailyPickService(DailyPickService dailyPickService) {
            this.dailyPickService = dailyPickService;
            return this;
        }

        public Builder standoutService(StandoutService standoutService) {
            this.standoutService = standoutService;
            return this;
        }

        public Builder undoService(UndoService undoService) {
            this.undoService = undoService;
            return this;
        }

        public Builder interactionStorage(InteractionStorage interactionStorage) {
            this.interactionStorage = interactionStorage;
            return this;
        }

        public Builder userStorage(UserStorage userStorage) {
            this.userStorage = userStorage;
            return this;
        }

        public Builder matchQualityService(MatchQualityService matchQualityService) {
            this.matchQualityService = matchQualityService;
            return this;
        }

        public Builder eventBus(AppEventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public MatchingUseCases build() {
            CandidateFinder resolvedCandidateFinder =
                    Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
            MatchingService resolvedMatchingService =
                    Objects.requireNonNull(matchingService, "matchingService cannot be null");
            DailyLimitService resolvedDailyLimitService =
                    Objects.requireNonNull(dailyLimitService, "dailyLimitService cannot be null");
            DailyPickService resolvedDailyPickService =
                    Objects.requireNonNull(dailyPickService, "dailyPickService cannot be null");
            StandoutService resolvedStandoutService =
                    Objects.requireNonNull(standoutService, "standoutService cannot be null");
            UndoService resolvedUndoService = Objects.requireNonNull(undoService, "undoService cannot be null");
            InteractionStorage resolvedInteractionStorage =
                    Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
            UserStorage resolvedUserStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
            MatchQualityService resolvedMatchQualityService =
                    Objects.requireNonNull(matchQualityService, "matchQualityService cannot be null");
            AppEventBus resolvedEventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
            RecommendationService resolvedRecommendationService =
                    Objects.requireNonNull(recommendationService, "recommendationService cannot be null");
            return new MatchingUseCases(
                    resolvedCandidateFinder,
                    resolvedMatchingService,
                    resolvedDailyLimitService,
                    resolvedDailyPickService,
                    resolvedStandoutService,
                    resolvedUndoService,
                    resolvedInteractionStorage,
                    resolvedUserStorage,
                    resolvedMatchQualityService,
                    resolvedEventBus,
                    resolvedRecommendationService);
        }
    }

    public UseCaseResult<BrowseCandidatesResult> browseCandidates(BrowseCandidatesCommand command) {
        if (command == null || command.context() == null || command.currentUser() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and current user are required"));
        }
        User currentUser = command.currentUser();
        if (currentUser.getState() != UserState.ACTIVE) {
            return UseCaseResult.failure(UseCaseError.conflict("User must be ACTIVE to browse candidates"));
        }

        try {
            Optional<DailyPick> dailyPick = dailyPickService.getDailyPick(currentUser);
            boolean dailyPickViewed = dailyPickService.hasViewedDailyPick(currentUser.getId());

            if (!currentUser.hasLocationSet()) {
                return UseCaseResult.success(new BrowseCandidatesResult(List.of(), dailyPick, dailyPickViewed, true));
            }

            List<User> candidates = candidateFinder.findCandidatesForUser(currentUser);
            List<User> rankedCandidates = recommendationService.rankBrowseCandidates(currentUser, candidates);
            return UseCaseResult.success(
                    new BrowseCandidatesResult(rankedCandidates, dailyPick, dailyPickViewed, false));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to browse candidates: " + e.getMessage()));
        }
    }

    public UseCaseResult<SwipeOutcome> processSwipe(ProcessSwipeCommand command) {
        if (command == null
                || command.context() == null
                || command.currentUser() == null
                || command.candidate() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, current user and candidate are required"));
        }
        if (!Objects.equals(command.context().userId(), command.currentUser().getId())) {
            return UseCaseResult.failure(UseCaseError.forbidden("Context user does not match current user"));
        }
        if (command.currentUser().getState() != UserState.ACTIVE) {
            return UseCaseResult.failure(UseCaseError.conflict("Current user must be ACTIVE to swipe"));
        }
        if (command.candidate().getState() != UserState.ACTIVE) {
            return UseCaseResult.failure(UseCaseError.conflict("Candidate must be ACTIVE to swipe"));
        }
        if (Objects.equals(command.currentUser().getId(), command.candidate().getId())) {
            return UseCaseResult.failure(UseCaseError.validation("Cannot swipe on yourself"));
        }
        try {
            MatchingService.SwipeResult result = matchingService.processSwipe(
                    command.currentUser(), command.candidate(), command.liked(), command.superLike());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.message()));
            }
            if (command.markDailyPickViewed() && result.like() != null) {
                dailyPickService.markDailyPickViewed(command.context().userId());
            }
            if (result.like() != null) {
                eventBus.publish(new AppEvent.SwipeRecorded(
                        command.context().userId(),
                        command.candidate().getId(),
                        result.like().direction(),
                        result.matched(),
                        AppClock.now()));
                if (result.matched()) {
                    Match m = result.match();
                    Objects.requireNonNull(m, "match cannot be null when matched() is true");
                    eventBus.publish(new AppEvent.MatchCreated(
                            m.getId(), result.like().whoLikes(), result.like().whoGotLiked(), AppClock.now()));
                }
            }
            return UseCaseResult.success(SwipeOutcome.from(result));
        } catch (Exception e) {
            String errorMessage =
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return UseCaseResult.failure(UseCaseError.internal("Failed to process swipe: " + errorMessage));
        }
    }

    public UseCaseResult<UndoOutcome> undoSwipe(UndoSwipeCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
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
            return UseCaseResult.success(UndoOutcome.from(result));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to undo swipe: " + e.getMessage()));
        }
    }

    public UseCaseResult<ActiveMatchesResult> listActiveMatches(ListActiveMatchesQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
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

    public UseCaseResult<PagedMatchesResult> listPagedMatches(ListPagedMatchesQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (query.limit() <= 0) {
            return UseCaseResult.failure(UseCaseError.validation("limit must be greater than 0"));
        }
        if (query.offset() < 0) {
            return UseCaseResult.failure(UseCaseError.validation("offset must be non-negative"));
        }
        try {
            UUID userId = query.context().userId();
            PageData<Match> page = interactionStorage.getPageOfActiveMatchesFor(userId, query.offset(), query.limit());
            Set<UUID> otherIds = new HashSet<>();
            for (Match match : page.items()) {
                otherIds.add(match.getOtherUser(userId));
            }
            Map<UUID, User> usersById = userStorage.findByIds(otherIds);
            return UseCaseResult.success(new PagedMatchesResult(page, usersById));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to list matches: " + e.getMessage()));
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

    public UseCaseResult<List<SentLikeSnapshot>> sentLikes(SentLikesQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        try {
            UUID userId = query.context().userId();
            Set<UUID> matchedUserIds = interactionStorage.getMatchedCounterpartIds(userId);
            List<SentLikeSnapshot> sentLikes = interactionStorage.getLikedOrPassedUserIds(userId).stream()
                    .filter(otherUserId -> !matchedUserIds.contains(otherUserId))
                    .map(otherUserId -> interactionStorage
                            .getLike(userId, otherUserId)
                            .filter(like -> like.direction() == Like.Direction.LIKE)
                            .map(like -> new SentLikeSnapshot(otherUserId, like.id(), like.createdAt())))
                    .flatMap(Optional::stream)
                    .sorted((left, right) -> right.likedAt().compareTo(left.likedAt()))
                    .toList();
            return UseCaseResult.success(sentLikes);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load sent likes: " + e.getMessage()));
        }
    }

    public UseCaseResult<StandoutsResult> standouts(StandoutsQuery query) {
        if (query == null || query.context() == null || query.currentUser() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and current user are required"));
        }
        try {
            StandoutService.Result result = standoutService.getStandouts(query.currentUser());
            Map<UUID, User> users = standoutService.resolveUsers(result.standouts());
            return UseCaseResult.success(new StandoutsResult(result, users));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load standouts: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> markStandoutInteracted(StandoutInteractionCommand command) {
        if (command == null || command.context() == null || command.standoutUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and standout user are required"));
        }
        try {
            standoutService.markInteracted(command.context().userId(), command.standoutUserId());
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
                && !dailyLimitService.canLike(command.context().userId())) {
            return UseCaseResult.failure(UseCaseError.conflict("Daily like limit reached"));
        }
        if (command.enforceDailyLimit()
                && command.direction() == Like.Direction.SUPER_LIKE
                && !dailyLimitService.canSuperLike(command.context().userId())) {
            return UseCaseResult.failure(UseCaseError.conflict("Daily super-like limit reached"));
        }
        if (command.enforceDailyLimit()
                && command.direction() == Like.Direction.PASS
                && !dailyLimitService.canPass(command.context().userId())) {
            return UseCaseResult.failure(UseCaseError.conflict("Daily pass limit reached"));
        }

        try {
            Like like = Like.create(command.context().userId(), command.targetUserId(), command.direction());
            MatchingService.RecordLikeOutcome outcome = matchingService.recordLike(like);
            if (outcome.rejected()) {
                return UseCaseResult.failure(
                        UseCaseError.conflict(outcome.rejectionMessage().orElseThrow()));
            }
            if (outcome.persisted()) {
                eventBus.publish(new AppEvent.SwipeRecorded(
                        like.whoLikes(),
                        like.whoGotLiked(),
                        like.direction(),
                        outcome.match().isPresent(),
                        AppClock.now()));
                if (outcome.match().isPresent()) {
                    Match m = outcome.match().orElseThrow();
                    eventBus.publish(
                            new AppEvent.MatchCreated(m.getId(), like.whoLikes(), like.whoGotLiked(), AppClock.now()));
                }
            }
            Like recordedLike = outcome.like().orElseThrow();
            Optional<Match> match = outcome.match();
            return UseCaseResult.success(new RecordLikeResult(recordedLike, match));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to record like interaction: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> removeLike(RemoveLikeCommand command) {
        if (command == null || command.context() == null || command.likeId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and likeId are required"));
        }
        try {
            Like like = interactionStorage.getLikeById(command.likeId()).orElse(null);
            if (like == null) {
                return UseCaseResult.failure(UseCaseError.notFound("Like not found"));
            }
            if (!Objects.equals(like.whoLikes(), command.context().userId())) {
                return UseCaseResult.failure(UseCaseError.forbidden("Like does not belong to current user"));
            }
            if (!interactionStorage.deleteLikeOwnedBy(command.context().userId(), command.likeId())) {
                return UseCaseResult.failure(UseCaseError.notFound("Like not found"));
            }
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to remove like: " + e.getMessage()));
        }
    }

    public UseCaseResult<MatchQualitySnapshot> matchQuality(MatchQualityQuery query) {
        if (query == null || query.context() == null || query.match() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and match are required"));
        }
        try {
            Optional<MatchQualityService.MatchQuality> quality = matchQualityService.computeQuality(
                    query.match(), query.context().userId());
            if (quality.isEmpty()) {
                return UseCaseResult.failure(UseCaseError.notFound("Match quality unavailable"));
            }
            return UseCaseResult.success(MatchQualitySnapshot.from(quality.get()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to compute match quality: " + e.getMessage()));
        }
    }

    public UseCaseResult<MatchQualitySnapshot> getMatchQuality(MatchQualityByIdQuery query) {
        if (query == null || query.context() == null || query.matchId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and matchId are required"));
        }
        Match match = interactionStorage.get(query.matchId()).orElse(null);
        if (match == null) {
            return UseCaseResult.failure(UseCaseError.notFound("Match not found"));
        }
        if (!match.involves(query.context().userId())) {
            return UseCaseResult.failure(UseCaseError.forbidden("Match does not belong to current user"));
        }
        return matchQuality(new MatchQualityQuery(query.context(), match));
    }

    public UseCaseResult<Void> archiveMatch(ArchiveMatchCommand command) {
        if (command == null || command.context() == null || command.matchId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and matchId are required"));
        }
        Match match = interactionStorage.get(command.matchId()).orElse(null);
        if (match == null) {
            return UseCaseResult.failure(UseCaseError.notFound("Match not found"));
        }
        if (!match.involves(command.context().userId())) {
            return UseCaseResult.failure(UseCaseError.forbidden("Match does not belong to current user"));
        }
        try {
            Match archivedMatch = copyMatch(match);
            archivedMatch.unmatch(command.context().userId());
            if (!interactionStorage.unmatchTransition(archivedMatch, Optional.empty())) {
                return UseCaseResult.failure(UseCaseError.conflict("Failed to archive match"));
            }
            return UseCaseResult.success(null);
        } catch (IllegalStateException e) {
            return UseCaseResult.failure(UseCaseError.conflict(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to archive match: " + e.getMessage()));
        }
    }

    public UseCaseResult<DailyStatusResult> getDailyStatus(DailyStatusQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        try {
            UUID userId = query.context().userId();
            DailyLimitService.DailyStatus status = dailyLimitService.getStatus(userId);
            String timeUntilReset = RecommendationService.formatDuration(dailyLimitService.getTimeUntilReset());
            return UseCaseResult.success(new DailyStatusResult(
                    status.likesUsed(), status.likesRemaining(), status.hasUnlimitedLikes(), timeUntilReset));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to get daily status: " + e.getMessage()));
        }
    }

    public UseCaseResult<UndoAvailabilityResult> getUndoAvailability(UndoAvailabilityQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        try {
            UUID userId = query.context().userId();
            boolean canUndo = undoService.canUndo(userId);
            int secondsRemaining = canUndo ? undoService.getSecondsRemaining(userId) : 0;
            return UseCaseResult.success(new UndoAvailabilityResult(canUndo, secondsRemaining));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to get undo availability: " + e.getMessage()));
        }
    }

    public static record BrowseCandidatesCommand(UserContext context, User currentUser) {}

    public static record BrowseCandidatesResult(
            List<User> candidates, Optional<DailyPick> dailyPick, boolean dailyPickViewed, boolean locationMissing) {}

    public static record ProcessSwipeCommand(
            UserContext context,
            User currentUser,
            User candidate,
            boolean liked,
            boolean superLike,
            boolean markDailyPickViewed) {}

    public static record SwipeOutcome(boolean matched, Match match, Like like, String message) {
        static SwipeOutcome from(MatchingService.SwipeResult result) {
            return new SwipeOutcome(result.matched(), result.match(), result.like(), result.message());
        }
    }

    public static record UndoSwipeCommand(UserContext context) {}

    public static record UndoOutcome(Like undoneSwipe, boolean matchDeleted, String message) {
        static UndoOutcome from(UndoService.UndoResult result) {
            return new UndoOutcome(result.undoneSwipe(), result.matchDeleted(), result.message());
        }
    }

    public static record ListActiveMatchesQuery(UserContext context) {}

    public static record ActiveMatchesResult(List<Match> matches, Map<UUID, User> usersById) {}

    public static record PagedMatchesResult(PageData<Match> page, Map<UUID, User> usersById) {}

    public static record PendingLikersQuery(UserContext context) {}

    public static record SentLikesQuery(UserContext context) {}

    public static record SentLikeSnapshot(UUID userId, UUID likeId, Instant likedAt) {}

    public static record StandoutsQuery(UserContext context, User currentUser) {}

    public static record StandoutsResult(StandoutService.Result result, Map<UUID, User> usersById) {}

    public static record StandoutInteractionCommand(UserContext context, UUID standoutUserId) {}

    public static record RecordLikeCommand(
            UserContext context, UUID targetUserId, Like.Direction direction, boolean enforceDailyLimit) {}

    public static record RecordLikeResult(Like like, Optional<Match> match) {}

    public static record RemoveLikeCommand(UserContext context, UUID likeId) {}

    public static record MatchQualityQuery(UserContext context, Match match) {}

    public static record MatchQualitySnapshot(
            String matchId,
            UUID perspectiveUserId,
            UUID otherUserId,
            Instant computedAt,
            double distanceScore,
            double ageScore,
            double interestScore,
            double lifestyleScore,
            double paceScore,
            double responseScore,
            double distanceKm,
            int ageDifference,
            List<String> sharedInterests,
            List<String> lifestyleMatches,
            Duration timeBetweenLikes,
            String paceSyncLevel,
            int compatibilityScore,
            String compatibilityLabel,
            String starDisplay,
            String shortSummary,
            List<String> highlights) {
        public MatchQualitySnapshot {
            sharedInterests = sharedInterests == null ? List.of() : List.copyOf(sharedInterests);
            lifestyleMatches = lifestyleMatches == null ? List.of() : List.copyOf(lifestyleMatches);
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
        }

        static MatchQualitySnapshot from(MatchQualityService.MatchQuality quality) {
            return new MatchQualitySnapshot(
                    quality.matchId(),
                    quality.perspectiveUserId(),
                    quality.otherUserId(),
                    quality.computedAt(),
                    quality.distanceScore(),
                    quality.ageScore(),
                    quality.interestScore(),
                    quality.lifestyleScore(),
                    quality.paceScore(),
                    quality.responseScore(),
                    quality.distanceKm(),
                    quality.ageDifference(),
                    quality.sharedInterests(),
                    quality.lifestyleMatches(),
                    quality.timeBetweenLikes(),
                    quality.paceSyncLevel(),
                    quality.compatibilityScore(),
                    quality.getCompatibilityLabel(),
                    quality.getStarDisplay(),
                    quality.getShortSummary(),
                    quality.highlights());
        }

        public String getCompatibilityLabel() {
            return compatibilityLabel;
        }

        public String getStarDisplay() {
            return starDisplay;
        }

        public String getShortSummary() {
            return shortSummary;
        }
    }

    public static record MatchQualityByIdQuery(UserContext context, String matchId) {}

    public static record ArchiveMatchCommand(UserContext context, String matchId) {}

    public static record ListPagedMatchesQuery(UserContext context, int limit, int offset) {}

    public static record DailyStatusQuery(UserContext context) {}

    public static record DailyStatusResult(
            int likesUsed, int likesRemaining, boolean hasUnlimitedLikes, String timeUntilReset) {}

    public static record UndoAvailabilityQuery(UserContext context) {}

    public static record UndoAvailabilityResult(boolean canUndo, int secondsRemaining) {}

    private static Match copyMatch(Match match) {
        return new Match(
                match.getId(),
                match.getUserA(),
                match.getUserB(),
                match.getCreatedAt(),
                match.getUpdatedAt(),
                match.getState(),
                match.getEndedAt(),
                match.getEndedBy(),
                match.getEndReason(),
                match.getDeletedAt());
    }

    public static DailyLimitService wrapDailyLimitService(RecommendationService recommendationService) {
        if (recommendationService == null) {
            return NO_OP_DAILY_LIMIT_SERVICE;
        }
        return new DailyLimitService() {
            @Override
            public boolean canLike(UUID userId) {
                return recommendationService.canLike(userId);
            }

            @Override
            public boolean canPass(UUID userId) {
                return recommendationService.canPass(userId);
            }

            @Override
            public boolean canSuperLike(UUID userId) {
                return recommendationService.canSuperLike(userId);
            }

            @Override
            public DailyStatus getStatus(UUID userId) {
                RecommendationService.DailyStatus status = recommendationService.getStatus(userId);
                return new DailyStatus(
                        status.likesUsed(),
                        status.likesRemaining(),
                        status.superLikesUsed(),
                        status.superLikesRemaining(),
                        status.passesUsed(),
                        status.passesRemaining(),
                        status.date(),
                        status.resetsAt());
            }

            @Override
            public Duration getTimeUntilReset() {
                return recommendationService.getTimeUntilReset();
            }
        };
    }

    public static DailyPickService wrapDailyPickService(RecommendationService recommendationService) {
        if (recommendationService == null) {
            return NO_OP_DAILY_PICK_SERVICE;
        }
        return new DailyPickService() {
            @Override
            public Optional<DailyPick> getDailyPick(User seeker) {
                return recommendationService
                        .getDailyPick(seeker)
                        .map(pick -> new DailyPick(pick.user(), pick.date(), pick.reason(), pick.alreadySeen()));
            }

            @Override
            public boolean hasViewedDailyPick(UUID userId) {
                return recommendationService.hasViewedDailyPick(userId);
            }

            @Override
            public void markDailyPickViewed(UUID userId) {
                recommendationService.markDailyPickViewed(userId);
            }

            @Override
            public int cleanupOldDailyPickViews(java.time.LocalDate before) {
                return recommendationService.cleanupOldDailyPickViews(before);
            }
        };
    }

    public static StandoutService wrapStandoutService(RecommendationService recommendationService) {
        if (recommendationService == null) {
            return NO_OP_STANDOUT_SERVICE;
        }
        return new StandoutService() {
            @Override
            public Result getStandouts(User seeker) {
                RecommendationService.Result result = recommendationService.getStandouts(seeker);
                return new Result(result.standouts(), result.totalCandidates(), result.fromCache(), result.message());
            }

            @Override
            public void markInteracted(UUID seekerId, UUID standoutUserId) {
                recommendationService.markInteracted(seekerId, standoutUserId);
            }

            @Override
            public Map<UUID, User> resolveUsers(List<Standout> standouts) {
                return recommendationService.resolveUsers(standouts);
            }
        };
    }
}
