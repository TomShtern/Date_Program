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
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
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
    // Storage (4 consolidated interfaces)
    // ─────────────────────────────────────────────
    private final UserStorage userStorage;
    private final InteractionStorage interactionStorage;
    private final CommunicationStorage communicationStorage;
    private final AnalyticsStorage analyticsStorage;
    private final TrustSafetyStorage trustSafetyStorage;

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
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            AnalyticsStorage analyticsStorage,
            TrustSafetyStorage trustSafetyStorage,
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
        this.interactionStorage = Objects.requireNonNull(interactionStorage);
        this.communicationStorage = Objects.requireNonNull(communicationStorage);
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage);
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage);
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

    public InteractionStorage getInteractionStorage() {
        return interactionStorage;
    }

    public CommunicationStorage getCommunicationStorage() {
        return communicationStorage;
    }

    public AnalyticsStorage getAnalyticsStorage() {
        return analyticsStorage;
    }

    public TrustSafetyStorage getTrustSafetyStorage() {
        return trustSafetyStorage;
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
