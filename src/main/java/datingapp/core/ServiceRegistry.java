package datingapp.core;

import datingapp.core.service.AchievementService;
import datingapp.core.service.CandidateFinder;
import datingapp.core.service.DailyService;
import datingapp.core.service.MatchQualityService;
import datingapp.core.service.MatchingService;
import datingapp.core.service.MessagingService;
import datingapp.core.service.ProfileCompletionService;
import datingapp.core.service.RelationshipTransitionService;
import datingapp.core.service.SessionService;
import datingapp.core.service.StandoutsService;
import datingapp.core.service.StatsService;
import datingapp.core.service.TrustSafetyService;
import datingapp.core.service.UndoService;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.SocialStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.Objects;

@SuppressWarnings("java:S6539")
public class ServiceRegistry {

    // ─────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────
    private final AppConfig config;

    // ─────────────────────────────────────────────
    // Core Storage (Users, Likes, Matches)
    // ─────────────────────────────────────────────
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final SwipeSessionStorage sessionStorage;

    // ─────────────────────────────────────────────
    // Trust & Safety Storage
    // ─────────────────────────────────────────────
    private final TrustSafetyStorage trustSafetyStorage;

    // ─────────────────────────────────────────────
    // Profile & Stats Storage
    // ─────────────────────────────────────────────
    private final StatsStorage statsStorage; // Consolidated: user + platform stats

    // ─────────────────────────────────────────────
    // Messaging & Social Storage
    // ─────────────────────────────────────────────
    private final MessagingStorage messagingStorage; // Consolidated: conversation + message
    private final SocialStorage socialStorage; // Consolidated: friend request + notification

    // ─────────────────────────────────────────────
    // Core Services (Matching)
    // ─────────────────────────────────────────────
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final SessionService sessionService;
    private final MatchQualityService matchQualityService;
    private final DailyService dailyService;
    private final UndoService undoService;

    // ─────────────────────────────────────────────
    // Trust & Safety Services
    // ─────────────────────────────────────────────
    private final TrustSafetyService trustSafetyService;

    // ─────────────────────────────────────────────
    // Stats & Achievement Services
    // ─────────────────────────────────────────────
    private final StatsService statsService;
    private final ProfileCompletionService profileCompletionService;
    private final AchievementService achievementService;

    // ─────────────────────────────────────────────
    // Messaging & Relationship Services
    // ─────────────────────────────────────────────
    private final MessagingService messagingService; // Messaging
    private final RelationshipTransitionService relationshipTransitionService;

    // ─────────────────────────────────────────────
    // Maintenance Services
    // ─────────────────────────────────────────────
    private final StandoutsService standoutsService;

    /** Public constructor — use {@link datingapp.storage.StorageFactory} for convenient wiring. */
    @SuppressWarnings("java:S107")
    public ServiceRegistry(
            AppConfig config,
            UserStorage userStorage,
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            TrustSafetyStorage trustSafetyStorage,
            SwipeSessionStorage sessionStorage,
            StatsStorage statsStorage,
            MessagingStorage messagingStorage,
            SocialStorage socialStorage,
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            TrustSafetyService trustSafetyService,
            SessionService sessionService,
            StatsService statsService,
            MatchQualityService matchQualityService,
            ProfileCompletionService profileCompletionService,
            DailyService dailyService,
            UndoService undoService,
            AchievementService achievementService,
            MessagingService messagingService,
            RelationshipTransitionService relationshipTransitionService,
            StandoutsService standoutsService) {
        this.config = Objects.requireNonNull(config);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage);
        this.sessionStorage = Objects.requireNonNull(sessionStorage);
        this.statsStorage = Objects.requireNonNull(statsStorage);
        this.messagingStorage = Objects.requireNonNull(messagingStorage);
        this.socialStorage = Objects.requireNonNull(socialStorage);
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.matchingService = Objects.requireNonNull(matchingService);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.statsService = Objects.requireNonNull(statsService);
        this.matchQualityService = Objects.requireNonNull(matchQualityService);
        this.profileCompletionService = Objects.requireNonNull(profileCompletionService);
        this.dailyService = Objects.requireNonNull(dailyService);
        this.undoService = Objects.requireNonNull(undoService);
        this.achievementService = Objects.requireNonNull(achievementService);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.relationshipTransitionService = Objects.requireNonNull(relationshipTransitionService);
        this.standoutsService = Objects.requireNonNull(standoutsService);
    }

    // === Configuration ===

    public AppConfig getConfig() {
        return config;
    }

    // === Core Storage ===

    public UserStorage getUserStorage() {
        return userStorage;
    }

    public LikeStorage getLikeStorage() {
        return likeStorage;
    }

    public MatchStorage getMatchStorage() {
        return matchStorage;
    }

    public SwipeSessionStorage getSessionStorage() {
        return sessionStorage;
    }

    // === Trust & Safety Storage ===

    public TrustSafetyStorage getTrustSafetyStorage() {
        return trustSafetyStorage;
    }

    // === Profile & Stats Storage ===

    public StatsStorage getStatsStorage() {
        return statsStorage;
    }

    // === Messaging & Social Storage ===

    public MessagingStorage getMessagingStorage() {
        return messagingStorage;
    }

    public SocialStorage getSocialStorage() {
        return socialStorage;
    }

    // === Matching Services ===

    public CandidateFinder getCandidateFinder() {
        return candidateFinder;
    }

    public MatchingService getMatchingService() {
        return matchingService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public MatchQualityService getMatchQualityService() {
        return matchQualityService;
    }

    public DailyService getDailyService() {
        return dailyService;
    }

    public UndoService getUndoService() {
        return undoService;
    }

    // === Trust & Safety Services ===

    public TrustSafetyService getTrustSafetyService() {
        return trustSafetyService;
    }

    // === Stats & Achievement Services ===

    public StatsService getStatsService() {
        return statsService;
    }

    public ProfileCompletionService getProfileCompletionService() {
        return profileCompletionService;
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    // === Messaging & Relationship Services ===

    public MessagingService getMessagingService() {
        return messagingService;
    }

    public RelationshipTransitionService getRelationshipTransitionService() {
        return relationshipTransitionService;
    }

    // === Maintenance Services ===

    public StandoutsService getStandoutsService() {
        return standoutsService;
    }
}
