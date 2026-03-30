package datingapp.ui.viewmodel;

import static datingapp.core.matching.CandidateFinder.GeoUtils.distanceKm;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.UndoSwipeCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.InterestMatcher;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.ui.async.TaskHandle;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel for the Matching screen.
 * Handles the logic for browsing candidates, liking/passing, undoing swipes,
 * and detecting matches.
 */
public class MatchingViewModel extends BaseViewModel {

    private final CandidateFinder candidateFinder;
    private final UndoService undoService;
    private final MatchingUseCases matchingUseCases;
    private final SocialUseCases socialUseCases;
    private final UiProfileNoteDataAccess noteDataAccess;
    private final AppSession session;

    private final Queue<User> candidateQueue = new ConcurrentLinkedQueue<>();
    private final ObjectProperty<User> currentCandidate = new SimpleObjectProperty<>();
    private final BooleanProperty hasMoreCandidates = new SimpleBooleanProperty(false);
    private final BooleanProperty locationMissing = new SimpleBooleanProperty(false);
    private final StringProperty infoMessage = new SimpleStringProperty();

    private final ObjectProperty<List<String>> currentCandidatePhotoUrls = new SimpleObjectProperty<>(List.of());
    private final IntegerProperty currentCandidatePhotoIndex = new SimpleIntegerProperty(0);
    private final StringProperty currentCandidatePhotoUrl = new SimpleStringProperty();
    private final StringProperty noteContent = new SimpleStringProperty("");
    private final StringProperty noteStatusMessage = new SimpleStringProperty();
    private final BooleanProperty noteBusy = new SimpleBooleanProperty(false);
    private final IntegerProperty undoCountdownSeconds = new SimpleIntegerProperty(0);
    private final BooleanProperty undoAvailable = new SimpleBooleanProperty(false);

    // New: Property to notify when a match occurs
    private final ObjectProperty<Match> lastMatch = new SimpleObjectProperty<>();
    private final ObjectProperty<User> matchedUser = new SimpleObjectProperty<>();

    private User lastSwipedCandidate;
    private User currentUser;
    private UUID prioritizedCandidateId;
    private final AtomicBoolean swipeInProgress = new AtomicBoolean(false);
    private final AtomicInteger noteLoadToken = new AtomicInteger();
    private TaskHandle undoCountdownHandle;

    public record Dependencies(
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            UndoService undoService,
            TrustSafetyService trustSafetyService,
            MatchingUseCases matchingUseCases,
            SocialUseCases socialUseCases,
            UiProfileNoteDataAccess noteDataAccess) {

        public Dependencies {
            Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
            Objects.requireNonNull(matchingService, "matchingService cannot be null");
            Objects.requireNonNull(undoService, "undoService cannot be null");
            Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
            Objects.requireNonNull(matchingUseCases, "matchingUseCases cannot be null");
            Objects.requireNonNull(socialUseCases, "socialUseCases cannot be null");
            Objects.requireNonNull(noteDataAccess, "noteDataAccess cannot be null");
        }
    }

    public MatchingViewModel(Dependencies dependencies, AppSession session, UiThreadDispatcher uiDispatcher) {
        super("matching", uiDispatcher);
        Dependencies resolvedDependencies = Objects.requireNonNull(dependencies, "dependencies cannot be null");
        this.candidateFinder = resolvedDependencies.candidateFinder();
        this.undoService = resolvedDependencies.undoService();
        this.matchingUseCases = resolvedDependencies.matchingUseCases();
        this.socialUseCases = resolvedDependencies.socialUseCases();
        this.noteDataAccess = resolvedDependencies.noteDataAccess();
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    /**
     * Gets the current user from UISession if not set.
     */
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = session.getCurrentUser();
        }
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshCandidates();
    }

    /**
     * Initializes the ViewModel by loading the current user from UISession.
     */
    public void initialize() {
        initialize(null);
    }

    public void initialize(UUID selectedCandidateId) {
        prioritizedCandidateId = selectedCandidateId;
        User user = ensureCurrentUser();
        if (user != null) {
            refreshCandidates();
        }
    }

    /**
     * Fetches a new list of candidates for the current user.
     */
    public void refreshCandidates() {
        if (isDisposed()) {
            return;
        }
        User user = ensureCurrentUser();
        if (user == null) {
            logWarn("Cannot refresh candidates: no current user set");
            setLoadingState(false);
            return;
        }

        UUID requestedCandidateId = prioritizedCandidateId;
        asyncScope.runLatest(
                "matching-refresh", "refresh candidates", () -> fetchCandidates(user, requestedCandidateId), result -> {
                    locationMissing.set(result.locationMissing());
                    candidateQueue.clear();
                    candidateQueue.addAll(result.candidates());
                    prioritizedCandidateId = null;
                    if (result.requestedCandidateUnavailable()) {
                        infoMessage.set("That profile is no longer available. Showing other nearby people instead.");
                    }
                    nextCandidate();
                });
    }

    private RefreshResult fetchCandidates(User user, UUID requestedCandidateId) {
        List<User> candidates = List.of();
        boolean locationMissingLocal = false;
        boolean requestedCandidateUnavailable = false;
        try {
            logDebug(
                    "Refreshing candidates for user: {} (state={}, isComplete={}, gender={}, interestedIn={})",
                    user.getName(),
                    user.getState(),
                    user.isComplete(),
                    user.getGender(),
                    user.getInterestedIn());

            var browseResult =
                    matchingUseCases.browseCandidates(new BrowseCandidatesCommand(UserContext.ui(user.getId()), user));
            if (browseResult.success()) {
                candidates = browseResult.data().candidates();
                locationMissingLocal = browseResult.data().locationMissing();
                CandidatePrioritization prioritization = prioritizeCandidate(candidates, requestedCandidateId);
                candidates = prioritization.candidates();
                requestedCandidateUnavailable = prioritization.requestedCandidateUnavailable();
            } else if (user.getState() != UserState.ACTIVE) {
                logWarn(
                        "Current user {} is NOT ACTIVE (state={}). Cannot browse candidates. Profile complete: {}",
                        user.getName(),
                        user.getState(),
                        user.isComplete());
            } else {
                logWarn("Failed to refresh candidates: {}", browseResult.error().message());
            }
            logDebug("Found {} candidates after filtering", candidates.size());
        } catch (Exception e) {
            logWarn("Failed to refresh candidates", e);
        }
        return new RefreshResult(candidates, locationMissingLocal, requestedCandidateUnavailable);
    }

    private CandidatePrioritization prioritizeCandidate(List<User> candidates, UUID requestedCandidateId) {
        if (requestedCandidateId == null || candidates.isEmpty()) {
            return new CandidatePrioritization(candidates, false);
        }

        List<User> reordered = new ArrayList<>(candidates.size());
        User prioritizedCandidate = null;
        for (User candidate : candidates) {
            if (requestedCandidateId.equals(candidate.getId())) {
                prioritizedCandidate = candidate;
            } else {
                reordered.add(candidate);
            }
        }
        if (prioritizedCandidate == null) {
            return new CandidatePrioritization(candidates, true);
        }
        reordered.addFirst(prioritizedCandidate);
        return new CandidatePrioritization(List.copyOf(reordered), false);
    }

    /**
     * Loads the next candidate from the queue.
     * All queue access is dispatched to the FX thread to avoid races
     * with {@link #refreshCandidates()} which clears/refills the queue on FX.
     */
    public void nextCandidate() {
        asyncScope.dispatchToUi(() -> {
            User next = candidateQueue.poll();
            currentCandidate.set(next);
            if (next != null) {
                List<String> urls = next.getPhotoUrls();
                currentCandidatePhotoUrls.set(urls);
                currentCandidatePhotoIndex.set(0);
                currentCandidatePhotoUrl.set(urls.isEmpty() ? null : urls.get(0));
            } else {
                currentCandidatePhotoUrls.set(List.of());
                currentCandidatePhotoIndex.set(0);
                currentCandidatePhotoUrl.set(null);
            }
            hasMoreCandidates.set(next != null);
            if (next != null) {
                loadNoteForCandidate(next);
            } else {
                clearNoteState();
            }
            swipeInProgress.set(false);
        });
    }

    private void loadNoteForCandidate(User candidate) {
        User user = ensureCurrentUser();
        if (candidate == null || user == null) {
            clearNoteState();
            return;
        }

        int token = noteLoadToken.incrementAndGet();
        noteStatusMessage.set(null);
        noteBusy.set(true);
        asyncScope.runFireAndForget("load candidate note", () -> {
            try {
                ProfileNote note = noteDataAccess
                        .getProfileNote(user.getId(), candidate.getId())
                        .orElse(null);
                asyncScope.dispatchToUi(() -> applyLoadedNote(candidate.getId(), note, token));
            } catch (Exception e) {
                asyncScope.dispatchToUi(() -> applyNoteFailure(candidate.getId(), token, "Failed to load note", e));
            }
        });
    }

    public void saveCurrentCandidateNote() {
        User user = ensureCurrentUser();
        User candidate = currentCandidate.get();
        if (user == null || candidate == null) {
            return;
        }

        String content = noteContent.get();
        noteBusy.set(true);
        noteStatusMessage.set(null);
        asyncScope.runFireAndForget("save candidate note", () -> {
            try {
                ProfileNote savedNote = noteDataAccess.upsertProfileNote(user.getId(), candidate.getId(), content);
                int token = noteLoadToken.incrementAndGet();
                asyncScope.dispatchToUi(() -> {
                    if (!isCurrentCandidate(candidate.getId(), token)) {
                        return;
                    }
                    noteContent.set(savedNote.content());
                    noteStatusMessage.set("Private note saved.");
                    noteBusy.set(false);
                });
            } catch (Exception e) {
                asyncScope.dispatchToUi(
                        () -> applyNoteFailure(candidate.getId(), noteLoadToken.get(), "Failed to save note", e));
            }
        });
    }

    public void deleteCurrentCandidateNote() {
        User user = ensureCurrentUser();
        User candidate = currentCandidate.get();
        if (user == null || candidate == null) {
            return;
        }

        noteBusy.set(true);
        noteStatusMessage.set(null);
        asyncScope.runFireAndForget("delete candidate note", () -> {
            try {
                boolean deleted = noteDataAccess.deleteProfileNote(user.getId(), candidate.getId());
                int token = noteLoadToken.incrementAndGet();
                asyncScope.dispatchToUi(() -> {
                    if (!isCurrentCandidate(candidate.getId(), token)) {
                        return;
                    }
                    if (deleted) {
                        noteContent.set("");
                        noteStatusMessage.set("Private note deleted.");
                    } else {
                        applyNoteFailure(candidate.getId(), token, "Failed to delete note", null);
                        return;
                    }
                    noteBusy.set(false);
                });
            } catch (Exception e) {
                asyncScope.dispatchToUi(
                        () -> applyNoteFailure(candidate.getId(), noteLoadToken.get(), "Failed to delete note", e));
            }
        });
    }

    private void applyLoadedNote(UUID candidateId, ProfileNote note, int token) {
        if (!isCurrentCandidate(candidateId, token)) {
            return;
        }
        noteContent.set(note != null ? note.content() : "");
        noteBusy.set(false);
    }

    private void applyNoteFailure(UUID candidateId, int token, String message, Exception error) {
        if (!isCurrentCandidate(candidateId, token)) {
            return;
        }
        noteStatusMessage.set(
                error != null
                                && error.getMessage() != null
                                && !error.getMessage().isBlank()
                        ? message + ": " + error.getMessage()
                        : message);
        noteBusy.set(false);
    }

    private boolean isCurrentCandidate(UUID candidateId, int token) {
        return token == noteLoadToken.get()
                && currentCandidate.get() != null
                && currentCandidate.get().getId().equals(candidateId);
    }

    private void clearNoteState() {
        noteLoadToken.incrementAndGet();
        noteContent.set("");
        noteStatusMessage.set(null);
        noteBusy.set(false);
    }

    public void like() {
        processSwipe(true, false);
    }

    public void superLike() {
        processSwipe(true, true);
    }

    public void pass() {
        processSwipe(false, false);
    }

    private void processSwipe(boolean liked, boolean superLike) {
        if (!swipeInProgress.compareAndSet(false, true)) {
            return;
        }
        User candidate = currentCandidate.get();
        if (candidate == null || ensureCurrentUser() == null) {
            swipeInProgress.set(false);
            return;
        }

        String action = "passed";
        if (liked) {
            action = superLike ? "super-liked" : "liked";
        }
        logInfo("User {} {} candidate {}", currentUser.getName(), action, candidate.getName());

        var result = matchingUseCases.processSwipe(new ProcessSwipeCommand(
                UserContext.ui(currentUser.getId()), currentUser, candidate, liked, superLike, false));

        if (!result.success()) {
            logWarn("Swipe failed: {}", result.error().message());
            infoMessage.set(result.error().message());
            swipeInProgress.set(false);
            return;
        }
        MatchingUseCases.SwipeOutcome swipeResult = result.data();

        lastSwipedCandidate = candidate;
        startUndoCountdown();

        // Check for a match and notify UI
        if (swipeResult.matched()) {
            logInfo("IT'S A MATCH! {} matched with {}", currentUser.getName(), candidate.getName());
            lastMatch.set(swipeResult.match());
            matchedUser.set(candidate);
        }

        nextCandidate();
    }

    public void undo() {
        if (ensureCurrentUser() == null) {
            return;
        }

        logInfo(
                "Undoing swipe on {}",
                lastSwipedCandidate != null ? lastSwipedCandidate.getName() : "previous candidate");
        var result = matchingUseCases.undoSwipe(new UndoSwipeCommand(UserContext.ui(currentUser.getId())));

        if (result.success()) {
            stopUndoCountdown();
            // Return last candidate to view
            if (lastSwipedCandidate != null) {
                currentCandidate.set(lastSwipedCandidate);
                List<String> urls = lastSwipedCandidate.getPhotoUrls();
                currentCandidatePhotoUrls.set(urls);
                currentCandidatePhotoIndex.set(0);
                currentCandidatePhotoUrl.set(urls.isEmpty() ? null : urls.get(0));
                loadNoteForCandidate(lastSwipedCandidate);
                lastSwipedCandidate = null;
                hasMoreCandidates.set(true);
                swipeInProgress.set(false);
                refreshCandidates();
            }
            return;
        }

        infoMessage.set(result.error().message());
    }

    private void startUndoCountdown() {
        stopUndoCountdown();
        User user = ensureCurrentUser();
        if (user == null) {
            return;
        }

        applyUndoCountdownState(undoService.getSecondsRemaining(user.getId()));
        if (!undoAvailable.get()) {
            return;
        }

        undoCountdownHandle =
                asyncScope.runPolling("matching-undo-countdown", "poll undo countdown", Duration.ofSeconds(1), () -> {
                    User current = ensureCurrentUser();
                    if (current == null) {
                        asyncScope.dispatchToUi(this::stopUndoCountdown);
                        return;
                    }
                    int remaining = undoService.getSecondsRemaining(current.getId());
                    asyncScope.dispatchToUi(() -> applyUndoCountdownState(remaining));
                });
    }

    private void applyUndoCountdownState(int remainingSeconds) {
        int normalizedSeconds = Math.max(0, remainingSeconds);
        undoCountdownSeconds.set(normalizedSeconds);
        undoAvailable.set(normalizedSeconds > 0);
        if (normalizedSeconds == 0 && undoCountdownHandle != null) {
            undoCountdownHandle.cancel();
            undoCountdownHandle = null;
        }
    }

    private void stopUndoCountdown() {
        if (undoCountdownHandle != null) {
            undoCountdownHandle.cancel();
            undoCountdownHandle = null;
        }
        undoCountdownSeconds.set(0);
        undoAvailable.set(false);
    }

    /**
     * Clears the match notification after the popup has been shown.
     */
    public void clearMatchNotification() {
        lastMatch.set(null);
        matchedUser.set(null);
    }

    /**
     * Calculates a simple compatibility score between current user and candidate.
     * Returns percentage as string (e.g., "85%").
     */
    public String getCompatibilityDisplay(User candidate) {
        if (candidate == null || currentUser == null) {
            return "--";
        }

        // Use InterestMatcher for more accurate calculation
        InterestMatcher.MatchResult interestMatch =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());

        // Simplified pre-match compatibility formula
        // (Full MatchQuality calculation requires a Match object)
        int score = 50; // Base score

        // Interest compatibility (0-30 points based on Jaccard similarity)
        score += (int) (interestMatch.jaccardIndex() * 30);

        // Age compatibility (0-15 points)
        Integer theirAge = candidate.getAge(candidateFinder.getTimezone()).orElse(null);
        Integer currentUserAge =
                currentUser.getAge(candidateFinder.getTimezone()).orElse(null);
        if (theirAge != null
                && currentUserAge != null
                && theirAge >= currentUser.getMinAge()
                && theirAge <= currentUser.getMaxAge()) {
            int ageDiff = Math.abs(theirAge - currentUserAge);
            score += Math.max(0, 15 - ageDiff); // Closer ages score higher
        }

        // Proximity bonus (+5 points for having location data)
        if (candidate.getLat() != 0.0 && candidate.getLon() != 0.0) {
            score += 5;
        }

        return Math.min(score, 99) + "%";
    }

    /**
     * Estimates distance display for a candidate.
     */
    public String getDistanceDisplay(User candidate) {
        if (candidate == null || currentUser == null) {
            return "Unknown";
        }

        // Calculate real distance if both have locations
        double lat1 = currentUser.getLat();
        double lon1 = currentUser.getLon();
        double lat2 = candidate.getLat();
        double lon2 = candidate.getLon();

        if (lat1 != 0 && lon1 != 0 && lat2 != 0 && lon2 != 0) {
            double distKm = distanceKm(lat1, lon1, lat2, lon2);
            if (distKm < 1) {
                return "< 1 km away";
            } else if (distKm < 10) {
                return String.format("%.1f km away", distKm);
            } else {
                return String.format("%.0f km away", distKm);
            }
        }

        return "Nearby";
    }

    // --- Properties ---
    public ObjectProperty<User> currentCandidateProperty() {
        return currentCandidate;
    }

    public BooleanProperty hasMoreCandidatesProperty() {
        return hasMoreCandidates;
    }

    public BooleanProperty locationMissingProperty() {
        return locationMissing;
    }

    public ObjectProperty<Match> lastMatchProperty() {
        return lastMatch;
    }

    public ObjectProperty<User> matchedUserProperty() {
        return matchedUser;
    }

    public void showNextPhoto() {
        List<String> urls = currentCandidatePhotoUrls.get();
        if (urls == null || urls.isEmpty()) {
            return;
        }
        int nextIdx = (currentCandidatePhotoIndex.get() + 1) % urls.size();
        currentCandidatePhotoIndex.set(nextIdx);
        currentCandidatePhotoUrl.set(urls.get(nextIdx));
    }

    public void showPreviousPhoto() {
        List<String> urls = currentCandidatePhotoUrls.get();
        if (urls == null || urls.isEmpty()) {
            return;
        }
        int prevIdx = currentCandidatePhotoIndex.get() - 1;
        if (prevIdx < 0) {
            prevIdx = urls.size() - 1;
        }
        currentCandidatePhotoIndex.set(prevIdx);
        currentCandidatePhotoUrl.set(urls.get(prevIdx));
    }

    public StringProperty currentCandidatePhotoUrlProperty() {
        return currentCandidatePhotoUrl;
    }

    public void blockCandidate(UUID targetId) {
        User user = ensureCurrentUser();
        if (user == null || targetId == null) {
            return;
        }
        asyncScope.runFireAndForget("block candidate", () -> {
            try {
                socialUseCases.blockUser(new RelationshipCommand(UserContext.ui(user.getId()), targetId));
                // After blocking, advance to next candidate
                asyncScope.dispatchToUi(this::nextCandidate);
            } catch (Exception e) {
                logWarn("Failed to block user", e);
            }
        });
    }

    public void reportCandidate(UUID targetId, Report.Reason reason, String description, boolean blockUser) {
        User user = ensureCurrentUser();
        if (user == null || targetId == null || reason == null) {
            return;
        }
        asyncScope.runFireAndForget("report candidate", () -> {
            try {
                socialUseCases.reportUser(
                        new ReportCommand(UserContext.ui(user.getId()), targetId, reason, description, blockUser));
                if (blockUser) {
                    asyncScope.dispatchToUi(this::nextCandidate);
                }
            } catch (Exception e) {
                logWarn("Failed to report user", e);
            }
        });
    }

    public ObjectProperty<List<String>> currentCandidatePhotoUrlsProperty() {
        return currentCandidatePhotoUrls;
    }

    public IntegerProperty currentCandidatePhotoIndexProperty() {
        return currentCandidatePhotoIndex;
    }

    public StringProperty infoMessageProperty() {
        return infoMessage;
    }

    public StringProperty noteContentProperty() {
        return noteContent;
    }

    public StringProperty noteStatusMessageProperty() {
        return noteStatusMessage;
    }

    public BooleanProperty noteBusyProperty() {
        return noteBusy;
    }

    public IntegerProperty undoCountdownSecondsProperty() {
        return undoCountdownSeconds;
    }

    public BooleanProperty undoAvailableProperty() {
        return undoAvailable;
    }

    public void clearInfoMessage() {
        infoMessage.set(null);
    }

    @Override
    protected void onDispose() {
        stopUndoCountdown();
        candidateQueue.clear();
        matchedUser.set(null);
        lastMatch.set(null);
    }

    private record RefreshResult(
            List<User> candidates, boolean locationMissing, boolean requestedCandidateUnavailable) {}

    private record CandidatePrioritization(List<User> candidates, boolean requestedCandidateUnavailable) {}
}
