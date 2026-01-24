package datingapp.ui.viewmodel;

import datingapp.core.BlockStorage;
import datingapp.core.CandidateFinder;
import datingapp.core.Like;
import datingapp.core.LikeStorage;
import datingapp.core.Match;
import datingapp.core.MatchingService;
import datingapp.core.UndoService;
import datingapp.core.User;
import datingapp.core.UserStorage;
import datingapp.ui.UISession;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
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
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final UndoService undoService;

    private final Queue<User> candidateQueue = new LinkedList<>();
    private final ObjectProperty<User> currentCandidate = new SimpleObjectProperty<>();
    private final BooleanProperty hasMoreCandidates = new SimpleBooleanProperty(false);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    // New: Property to notify when a match occurs
    private final ObjectProperty<Match> lastMatch = new SimpleObjectProperty<>();
    private final ObjectProperty<User> matchedUser = new SimpleObjectProperty<>();

    private User lastSwipedCandidate;
    private User currentUser;

    public MatchingViewModel(
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            UserStorage userStorage,
            LikeStorage likeStorage,
            BlockStorage blockStorage,
            UndoService undoService) {
        this.candidateFinder = candidateFinder;
        this.matchingService = matchingService;
        this.userStorage = userStorage;
        this.likeStorage = likeStorage;
        this.blockStorage = blockStorage;
        this.undoService = undoService;
    }

    /**
     * Gets the current user from UISession if not set.
     */
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = UISession.getInstance().getCurrentUser();
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
            // Load candidates in background to keep UI responsive
            Thread.ofVirtual().start(this::refreshCandidates);
        }
    }

    /**
     * Fetches a new list of candidates for the current user.
     */
    public void refreshCandidates() {
        if (ensureCurrentUser() == null) {
            return;
        }

        javafx.application.Platform.runLater(() -> loading.set(true));
        logger.info("Refreshing candidates for user: {}", currentUser.getName());

        List<User> activeUsers = userStorage.findActive();
        Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(currentUser.getId());
        Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(currentUser.getId());

        Set<UUID> excluded = new HashSet<>(alreadyInteracted);
        excluded.addAll(blockedUsers);

        List<User> candidates = candidateFinder.findCandidates(currentUser, activeUsers, excluded);

        javafx.application.Platform.runLater(() -> {
            candidateQueue.clear();
            candidateQueue.addAll(candidates);

            logger.info("Found {} candidates", candidates.size());
            loading.set(false);
            nextCandidate();
        });
    }

    /**
     * Loads the next candidate from the queue.
     * MUST be called on FX Thread or use Platform.runLater.
     */
    public void nextCandidate() {
        User next = candidateQueue.poll();
        if (javafx.application.Platform.isFxApplicationThread()) {
            currentCandidate.set(next);
            hasMoreCandidates.set(next != null);
        } else {
            javafx.application.Platform.runLater(() -> {
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

        logger.info("User {} {} candidate {}", currentUser.getName(), liked ? "liked" : "passed", candidate.getName());

        Like.Direction direction = liked ? Like.Direction.LIKE : Like.Direction.PASS;
        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);

        Optional<Match> match = matchingService.recordLike(like);
        undoService.recordSwipe(currentUser.getId(), like, match);

        lastSwipedCandidate = candidate;

        // Check for a match and notify UI
        if (match.isPresent()) {
            logger.info("IT'S A MATCH! {} matched with {}", currentUser.getName(), candidate.getName());
            lastMatch.set(match.get());
            matchedUser.set(candidate);
        }

        nextCandidate();
    }

    public void undo() {
        if (ensureCurrentUser() == null) {
            return;
        }

        if (undoService.canUndo(currentUser.getId())) {
            logger.info(
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

        int score = 50; // Base score

        // Shared interests bonus (+5 per shared interest, max 25)
        if (currentUser.getInterests() != null && candidate.getInterests() != null) {
            long sharedCount = currentUser.getInterests().stream()
                    .filter(i -> candidate.getInterests().contains(i))
                    .count();
            score += Math.min((int) sharedCount * 5, 25);
        }

        // Age range match bonus (+15)
        int theirAge = candidate.getAge();
        if (theirAge >= currentUser.getMinAge() && theirAge <= currentUser.getMaxAge()) {
            score += 15;
        }

        // Distance (assume nearby for now) (+10)
        score += 10;

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
            double distKm = haversineDistance(lat1, lon1, lat2, lon2);
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

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371; // km
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(deltaLon / 2)
                        * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
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
}
