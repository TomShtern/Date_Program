package datingapp.ui.viewmodel;

import static datingapp.core.CandidateFinder.GeoUtils.distanceKm;

import datingapp.core.AppSession;
import datingapp.core.CandidateFinder;
import datingapp.core.Match;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchingService;
import datingapp.core.UndoService;
import datingapp.core.User;
import datingapp.core.UserState;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Matching screen.
 * Handles the logic for browsing candidates, liking/passing, undoing swipes,
 * and detecting matches.
 */
public class MatchingViewModel {
    private static final Logger logger = LoggerFactory.getLogger(MatchingViewModel.class);

    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final UndoService undoService;

    private final Queue<User> candidateQueue = new LinkedList<>();
    private final ObjectProperty<User> currentCandidate = new SimpleObjectProperty<>();
    private final BooleanProperty hasMoreCandidates = new SimpleBooleanProperty(false);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    // New: Property to notify when a match occurs
    private final ObjectProperty<Match> lastMatch = new SimpleObjectProperty<>();
    private final ObjectProperty<User> matchedUser = new SimpleObjectProperty<>();

    /** Track background thread for cleanup on dispose. */
    private final AtomicReference<Thread> backgroundThread = new AtomicReference<>();

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicInteger activeLoads = new AtomicInteger(0);

    private User lastSwipedCandidate;
    private User currentUser;

    public MatchingViewModel(
            CandidateFinder candidateFinder, MatchingService matchingService, UndoService undoService) {
        this.candidateFinder = candidateFinder;
        this.matchingService = matchingService;
        this.undoService = undoService;
    }

    /**
     * Gets the current user from UISession if not set.
     */
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
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
        User user = ensureCurrentUser();
        if (user != null) {
            refreshCandidates();
        }
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        Thread thread = backgroundThread.getAndSet(null);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        activeLoads.set(0);
        setLoadingState(false);
    }

    /**
     * Fetches a new list of candidates for the current user.
     */
    public void refreshCandidates() {
        if (disposed.get()) {
            return;
        }
        User user = ensureCurrentUser();
        if (user == null) {
            logWarn("Cannot refresh candidates: no current user set");
            activeLoads.set(0);
            setLoadingState(false);
            return;
        }

        beginLoading();

        Thread thread = Thread.ofVirtual().name("candidate-refresh").start(() -> {
            List<User> candidates = null;
            try {
                logDebug(
                        "Refreshing candidates for user: {} (state={}, isComplete={}, gender={}, interestedIn={})",
                        user.getName(),
                        user.getState(),
                        user.isComplete(),
                        user.getGender(),
                        user.getInterestedIn());

                // Check if current user is ACTIVE - otherwise they cannot browse
                if (user.getState() != UserState.ACTIVE) {
                    logWarn(
                            "Current user {} is NOT ACTIVE (state={}). Cannot browse candidates. Profile complete: {}",
                            user.getName(),
                            user.getState(),
                            user.isComplete());
                    candidates = List.of();
                } else {
                    candidates = candidateFinder.findCandidatesForUser(user);
                }
                logDebug("Found {} candidates after filtering", candidates.size());

            } catch (Exception e) {
                logWarn("Failed to refresh candidates", e);
            }
            List<User> finalCandidates = candidates;
            runOnFx(() -> {
                if (!disposed.get() && finalCandidates != null) {
                    candidateQueue.clear();
                    candidateQueue.addAll(finalCandidates);
                    nextCandidate();
                }
                endLoading();
            });
        });
        backgroundThread.set(thread);
    }

    /**
     * Loads the next candidate from the queue.
     * MUST be called on FX Thread or use Platform.runLater.
     */
    public void nextCandidate() {
        User next = candidateQueue.poll();
        if (Platform.isFxApplicationThread()) {
            currentCandidate.set(next);
            hasMoreCandidates.set(next != null);
        } else {
            Platform.runLater(() -> {
                currentCandidate.set(next);
                hasMoreCandidates.set(next != null);
            });
        }
    }

    public void like() {
        processSwipe(true);
    }

    public void pass() {
        processSwipe(false);
    }

    private void processSwipe(boolean liked) {
        User candidate = currentCandidate.get();
        if (candidate == null || ensureCurrentUser() == null) {
            return;
        }

        logInfo("User {} {} candidate {}", currentUser.getName(), liked ? "liked" : "passed", candidate.getName());

        MatchingService.SwipeResult result = matchingService.processSwipe(currentUser, candidate, liked);

        if (!result.success()) {
            logWarn("Swipe failed: {}", result.message());
            // UI handles daily limit messaging elsewhere; no further action here.
            return;
        }

        lastSwipedCandidate = candidate;

        // Check for a match and notify UI
        if (result.matched()) {
            logInfo("IT'S A MATCH! {} matched with {}", currentUser.getName(), candidate.getName());
            lastMatch.set(result.match());
            matchedUser.set(candidate);
        }

        nextCandidate();
    }

    public void undo() {
        if (ensureCurrentUser() == null) {
            return;
        }

        if (undoService.canUndo(currentUser.getId())) {
            logInfo(
                    "Undoing swipe on {}",
                    lastSwipedCandidate != null ? lastSwipedCandidate.getName() : "previous candidate");
            UndoService.UndoResult result = undoService.undo(currentUser.getId());

            if (result.success()) {
                // Return last candidate to view
                if (lastSwipedCandidate != null) {
                    currentCandidate.set(lastSwipedCandidate);
                    lastSwipedCandidate = null;
                    hasMoreCandidates.set(true);
                } else {
                    refreshCandidates();
                }
            }
        }
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

        // Use MatchQualityService's InterestMatcher for more accurate calculation
        MatchQualityService.InterestMatcher.MatchResult interestMatch =
                MatchQualityService.InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());

        // Simplified pre-match compatibility formula
        // (Full MatchQuality calculation requires a Match object)
        int score = 50; // Base score

        // Interest compatibility (0-30 points based on Jaccard similarity)
        score += (int) (interestMatch.jaccardIndex() * 30);

        // Age compatibility (0-15 points)
        int theirAge = candidate.getAge();
        if (theirAge >= currentUser.getMinAge() && theirAge <= currentUser.getMaxAge()) {
            int ageDiff = Math.abs(theirAge - currentUser.getAge());
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

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public ObjectProperty<Match> lastMatchProperty() {
        return lastMatch;
    }

    public ObjectProperty<User> matchedUserProperty() {
        return matchedUser;
    }

    private void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void beginLoading() {
        if (activeLoads.incrementAndGet() == 1) {
            setLoadingState(true);
        }
    }

    private void endLoading() {
        int remaining = activeLoads.decrementAndGet();
        if (remaining <= 0) {
            activeLoads.set(0);
            setLoadingState(false);
        }
    }

    private void setLoadingState(boolean isLoading) {
        runOnFx(() -> {
            if (loading.get() != isLoading) {
                loading.set(isLoading);
            }
        });
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
