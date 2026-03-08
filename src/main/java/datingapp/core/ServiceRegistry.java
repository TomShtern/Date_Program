package datingapp.core;

import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.core.workflow.RelationshipWorkflowPolicy;
import java.util.Objects;

@SuppressWarnings("java:S6539")
public class ServiceRegistry {

    private final AppConfig config;

    private final UserStorage userStorage;
    private final InteractionStorage interactionStorage;
    private final CommunicationStorage communicationStorage;
    private final AnalyticsStorage analyticsStorage;
    private final TrustSafetyStorage trustSafetyStorage;

    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final ActivityMetricsService activityMetricsService;
    private final MatchQualityService matchQualityService;
    private final RecommendationService recommendationService;
    private final DailyLimitService dailyLimitService;
    private final DailyPickService dailyPickService;
    private final StandoutService standoutService;
    private final UndoService undoService;
    private final CompatibilityCalculator compatibilityCalculator;

    private final TrustSafetyService trustSafetyService;
    private final ProfileService profileService;
    private final ValidationService validationService;
    private final AchievementService achievementService;

    private final ConnectionService connectionService;
    private final AppEventBus eventBus;
    private final ProfileActivationPolicy activationPolicy;
    private final RelationshipWorkflowPolicy workflowPolicy;

    private final MessagingUseCases messagingUseCases;
    private final MatchingUseCases matchingUseCases;
    private final ProfileUseCases profileUseCases;
    private final SocialUseCases socialUseCases;

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
            ActivityMetricsService activityMetricsService,
            MatchQualityService matchQualityService,
            ProfileService profileService,
            RecommendationService recommendationService,
            DailyLimitService dailyLimitService,
            DailyPickService dailyPickService,
            StandoutService standoutService,
            UndoService undoService,
            CompatibilityCalculator compatibilityCalculator,
            AchievementService achievementService,
            ConnectionService connectionService,
            ValidationService validationService,
            AppEventBus eventBus) {
        this(
                config,
                userStorage,
                interactionStorage,
                communicationStorage,
                analyticsStorage,
                trustSafetyStorage,
                candidateFinder,
                matchingService,
                trustSafetyService,
                activityMetricsService,
                matchQualityService,
                profileService,
                recommendationService,
                dailyLimitService,
                dailyPickService,
                standoutService,
                undoService,
                compatibilityCalculator,
                achievementService,
                connectionService,
                validationService,
                eventBus,
                new ProfileActivationPolicy(),
                new RelationshipWorkflowPolicy());
    }

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
            ActivityMetricsService activityMetricsService,
            MatchQualityService matchQualityService,
            ProfileService profileService,
            RecommendationService recommendationService,
            DailyLimitService dailyLimitService,
            DailyPickService dailyPickService,
            StandoutService standoutService,
            UndoService undoService,
            CompatibilityCalculator compatibilityCalculator,
            AchievementService achievementService,
            ConnectionService connectionService,
            ValidationService validationService,
            AppEventBus eventBus,
            ProfileActivationPolicy activationPolicy,
            RelationshipWorkflowPolicy workflowPolicy) {
        this.config = Objects.requireNonNull(config);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.interactionStorage = Objects.requireNonNull(interactionStorage);
        this.communicationStorage = Objects.requireNonNull(communicationStorage);
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage);
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage);
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.matchingService = Objects.requireNonNull(matchingService);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.activityMetricsService = Objects.requireNonNull(activityMetricsService);
        this.matchQualityService = Objects.requireNonNull(matchQualityService);
        this.profileService = Objects.requireNonNull(profileService);
        this.recommendationService = Objects.requireNonNull(recommendationService);
        this.dailyLimitService = Objects.requireNonNull(dailyLimitService);
        this.dailyPickService = Objects.requireNonNull(dailyPickService);
        this.standoutService = Objects.requireNonNull(standoutService);
        this.undoService = Objects.requireNonNull(undoService);
        this.compatibilityCalculator = Objects.requireNonNull(compatibilityCalculator);
        this.connectionService = Objects.requireNonNull(connectionService);
        this.validationService = Objects.requireNonNull(validationService);
        this.achievementService = Objects.requireNonNull(achievementService);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.activationPolicy = Objects.requireNonNull(activationPolicy);
        this.workflowPolicy = Objects.requireNonNull(workflowPolicy);

        this.messagingUseCases = new MessagingUseCases(this.connectionService, this.eventBus);
        this.matchingUseCases = new MatchingUseCases(
                this.candidateFinder,
                this.matchingService,
                this.recommendationService,
                this.undoService,
                this.interactionStorage,
                this.userStorage,
                this.matchQualityService,
                this.eventBus);
        this.profileUseCases = new ProfileUseCases(
                this.userStorage,
                this.profileService,
                this.validationService,
                this.activityMetricsService,
                this.achievementService,
                this.config,
                this.activationPolicy,
                this.eventBus);
        this.socialUseCases = new SocialUseCases(
                this.connectionService, this.trustSafetyService, this.communicationStorage, this.eventBus);
    }

    public AppConfig getConfig() {
        return config;
    }

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

    public CandidateFinder getCandidateFinder() {
        return candidateFinder;
    }

    public MatchingService getMatchingService() {
        return matchingService;
    }

    public ActivityMetricsService getActivityMetricsService() {
        return activityMetricsService;
    }

    public MatchQualityService getMatchQualityService() {
        return matchQualityService;
    }

    public RecommendationService getRecommendationService() {
        return recommendationService;
    }

    public DailyLimitService getDailyLimitService() {
        return dailyLimitService;
    }

    public DailyPickService getDailyPickService() {
        return dailyPickService;
    }

    public StandoutService getStandoutService() {
        return standoutService;
    }

    public UndoService getUndoService() {
        return undoService;
    }

    public CompatibilityCalculator getCompatibilityCalculator() {
        return compatibilityCalculator;
    }

    public TrustSafetyService getTrustSafetyService() {
        return trustSafetyService;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public ConnectionService getConnectionService() {
        return connectionService;
    }

    public ValidationService getValidationService() {
        return validationService;
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    public MessagingUseCases getMessagingUseCases() {
        return messagingUseCases;
    }

    public MatchingUseCases getMatchingUseCases() {
        return matchingUseCases;
    }

    public ProfileUseCases getProfileUseCases() {
        return profileUseCases;
    }

    public SocialUseCases getSocialUseCases() {
        return socialUseCases;
    }

    public AppEventBus getEventBus() {
        return eventBus;
    }

    public ProfileActivationPolicy getActivationPolicy() {
        return activationPolicy;
    }

    public RelationshipWorkflowPolicy getWorkflowPolicy() {
        return workflowPolicy;
    }
}
