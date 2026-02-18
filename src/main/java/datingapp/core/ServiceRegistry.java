package datingapp.core;

import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
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
    private final UndoService undoService;

    private final TrustSafetyService trustSafetyService;
    private final ProfileService profileService;
    private final ValidationService validationService;

    private final ConnectionService connectionService;

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
            UndoService undoService,
            ConnectionService connectionService,
            ValidationService validationService) {
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
        this.undoService = Objects.requireNonNull(undoService);
        this.connectionService = Objects.requireNonNull(connectionService);
        this.validationService = Objects.requireNonNull(validationService);
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

    public UndoService getUndoService() {
        return undoService;
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
}
