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
public final class ServiceRegistry {

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

    public static Builder builder() {
        return new Builder();
    }

    private ServiceRegistry(Builder builder) {
        this.config = Objects.requireNonNull(builder.config, "config cannot be null");
        this.userStorage = Objects.requireNonNull(builder.userStorage, "userStorage cannot be null");
        this.interactionStorage =
                Objects.requireNonNull(builder.interactionStorage, "interactionStorage cannot be null");
        this.communicationStorage =
                Objects.requireNonNull(builder.communicationStorage, "communicationStorage cannot be null");
        this.analyticsStorage = Objects.requireNonNull(builder.analyticsStorage, "analyticsStorage cannot be null");
        this.trustSafetyStorage =
                Objects.requireNonNull(builder.trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.candidateFinder = Objects.requireNonNull(builder.candidateFinder, "candidateFinder cannot be null");
        this.matchingService = Objects.requireNonNull(builder.matchingService, "matchingService cannot be null");
        this.trustSafetyService =
                Objects.requireNonNull(builder.trustSafetyService, "trustSafetyService cannot be null");
        this.activityMetricsService =
                Objects.requireNonNull(builder.activityMetricsService, "activityMetricsService cannot be null");
        this.matchQualityService =
                Objects.requireNonNull(builder.matchQualityService, "matchQualityService cannot be null");
        this.profileService = Objects.requireNonNull(builder.profileService, "profileService cannot be null");
        this.recommendationService =
                Objects.requireNonNull(builder.recommendationService, "recommendationService cannot be null");
        this.dailyLimitService = Objects.requireNonNull(builder.dailyLimitService, "dailyLimitService cannot be null");
        this.dailyPickService = Objects.requireNonNull(builder.dailyPickService, "dailyPickService cannot be null");
        this.standoutService = Objects.requireNonNull(builder.standoutService, "standoutService cannot be null");
        this.undoService = Objects.requireNonNull(builder.undoService, "undoService cannot be null");
        this.compatibilityCalculator =
                Objects.requireNonNull(builder.compatibilityCalculator, "compatibilityCalculator cannot be null");
        this.connectionService = Objects.requireNonNull(builder.connectionService, "connectionService cannot be null");
        this.validationService = Objects.requireNonNull(builder.validationService, "validationService cannot be null");
        this.achievementService =
                Objects.requireNonNull(builder.achievementService, "achievementService cannot be null");
        this.eventBus = Objects.requireNonNull(builder.eventBus, "eventBus cannot be null");
        this.activationPolicy = Objects.requireNonNull(builder.activationPolicy, "activationPolicy cannot be null");
        this.workflowPolicy = Objects.requireNonNull(builder.workflowPolicy, "workflowPolicy cannot be null");

        this.messagingUseCases = new MessagingUseCases(this.connectionService, this.eventBus);
        this.matchingUseCases = MatchingUseCases.builder()
                .candidateFinder(this.candidateFinder)
                .matchingService(this.matchingService)
                .recommendationService(this.recommendationService)
                .undoService(this.undoService)
                .interactionStorage(this.interactionStorage)
                .userStorage(this.userStorage)
                .matchQualityService(this.matchQualityService)
                .eventBus(this.eventBus)
                .build();
        this.profileUseCases = ProfileUseCases.builder()
                .userStorage(this.userStorage)
                .profileService(this.profileService)
                .validationService(this.validationService)
                .activityMetricsService(this.activityMetricsService)
                .achievementService(this.achievementService)
                .config(this.config)
                .activationPolicy(this.activationPolicy)
                .eventBus(this.eventBus)
                .build();
        this.socialUseCases = new SocialUseCases(
                this.connectionService, this.trustSafetyService, this.communicationStorage, this.eventBus);
    }

    public static final class Builder {
        private AppConfig config;
        private UserStorage userStorage;
        private InteractionStorage interactionStorage;
        private CommunicationStorage communicationStorage;
        private AnalyticsStorage analyticsStorage;
        private TrustSafetyStorage trustSafetyStorage;
        private CandidateFinder candidateFinder;
        private MatchingService matchingService;
        private ActivityMetricsService activityMetricsService;
        private MatchQualityService matchQualityService;
        private RecommendationService recommendationService;
        private DailyLimitService dailyLimitService;
        private DailyPickService dailyPickService;
        private StandoutService standoutService;
        private UndoService undoService;
        private CompatibilityCalculator compatibilityCalculator;
        private TrustSafetyService trustSafetyService;
        private ProfileService profileService;
        private ValidationService validationService;
        private AchievementService achievementService;
        private ConnectionService connectionService;
        private AppEventBus eventBus;
        private ProfileActivationPolicy activationPolicy = new ProfileActivationPolicy();
        private RelationshipWorkflowPolicy workflowPolicy = new RelationshipWorkflowPolicy();

        private Builder() {}

        public Builder config(AppConfig config) {
            this.config = config;
            return this;
        }

        public Builder userStorage(UserStorage userStorage) {
            this.userStorage = userStorage;
            return this;
        }

        public Builder interactionStorage(InteractionStorage interactionStorage) {
            this.interactionStorage = interactionStorage;
            return this;
        }

        public Builder communicationStorage(CommunicationStorage communicationStorage) {
            this.communicationStorage = communicationStorage;
            return this;
        }

        public Builder analyticsStorage(AnalyticsStorage analyticsStorage) {
            this.analyticsStorage = analyticsStorage;
            return this;
        }

        public Builder trustSafetyStorage(TrustSafetyStorage trustSafetyStorage) {
            this.trustSafetyStorage = trustSafetyStorage;
            return this;
        }

        public Builder candidateFinder(CandidateFinder candidateFinder) {
            this.candidateFinder = candidateFinder;
            return this;
        }

        public Builder matchingService(MatchingService matchingService) {
            this.matchingService = matchingService;
            return this;
        }

        public Builder activityMetricsService(ActivityMetricsService activityMetricsService) {
            this.activityMetricsService = activityMetricsService;
            return this;
        }

        public Builder matchQualityService(MatchQualityService matchQualityService) {
            this.matchQualityService = matchQualityService;
            return this;
        }

        public Builder recommendationService(RecommendationService recommendationService) {
            this.recommendationService = recommendationService;
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

        public Builder compatibilityCalculator(CompatibilityCalculator compatibilityCalculator) {
            this.compatibilityCalculator = compatibilityCalculator;
            return this;
        }

        public Builder trustSafetyService(TrustSafetyService trustSafetyService) {
            this.trustSafetyService = trustSafetyService;
            return this;
        }

        public Builder profileService(ProfileService profileService) {
            this.profileService = profileService;
            return this;
        }

        public Builder validationService(ValidationService validationService) {
            this.validationService = validationService;
            return this;
        }

        public Builder achievementService(AchievementService achievementService) {
            this.achievementService = achievementService;
            return this;
        }

        public Builder connectionService(ConnectionService connectionService) {
            this.connectionService = connectionService;
            return this;
        }

        public Builder eventBus(AppEventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder activationPolicy(ProfileActivationPolicy activationPolicy) {
            this.activationPolicy = activationPolicy;
            return this;
        }

        public Builder workflowPolicy(RelationshipWorkflowPolicy workflowPolicy) {
            this.workflowPolicy = workflowPolicy;
            return this;
        }

        public ServiceRegistry build() {
            return new ServiceRegistry(this);
        }
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
