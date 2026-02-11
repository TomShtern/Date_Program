package datingapp.app.cli;

import datingapp.app.cli.CliSupport.InputReader;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.service.ValidationService;
import java.util.Objects;

/**
 * Factory for creating CLI handlers with consistent dependency injection.
 * Provides lazy initialization of all handlers to reduce startup overhead.
 */
public final class HandlerFactory {
    private final ServiceRegistry services;
    private final AppSession session;
    private final InputReader inputReader;

    // Lazily-created handlers
    private MatchingHandler matchingHandler;
    private ProfileHandler profileHandler;
    private SafetyHandler safetyHandler;
    private StatsHandler statsHandler;
    private MessagingHandler messagingHandler;

    /**
     * Creates a new HandlerFactory with dependencies from ServiceRegistry.
     *
     * @param services the centralized service registry
     * @param session the app session for user context
     * @param inputReader the CLI input reader
     */
    public HandlerFactory(ServiceRegistry services, AppSession session, InputReader inputReader) {
        this.services = Objects.requireNonNull(services, "services cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.inputReader = Objects.requireNonNull(inputReader, "inputReader cannot be null");
    }

    /**
     * Returns the MatchingHandler (lazy-initialized).
     */
    public MatchingHandler matching() {
        if (matchingHandler == null) {
            MatchingHandler.Dependencies deps = new MatchingHandler.Dependencies(
                    services.getCandidateFinder(),
                    services.getMatchingService(),
                    services.getMatchStorage(),
                    services.getTrustSafetyStorage(),
                    services.getDailyService(),
                    services.getUndoService(),
                    services.getMatchQualityService(),
                    services.getUserStorage(),
                    services.getAchievementService(),
                    services.getStatsStorage(),
                    services.getRelationshipTransitionService(),
                    services.getStandoutsService(),
                    services.getSocialStorage(),
                    session,
                    inputReader);
            matchingHandler = new MatchingHandler(deps);
        }
        return matchingHandler;
    }

    /**
     * Returns the ProfileHandler (lazy-initialized).
     */
    public ProfileHandler profile() {
        if (profileHandler == null) {
            profileHandler = new ProfileHandler(
                    services.getUserStorage(),
                    services.getProfileCompletionService(),
                    services.getAchievementService(),
                    new ValidationService(services.getConfig()),
                    session,
                    inputReader);
        }
        return profileHandler;
    }

    /**
     * Returns the SafetyHandler (lazy-initialized).
     */
    public SafetyHandler safety() {
        if (safetyHandler == null) {
            safetyHandler = new SafetyHandler(
                    services.getUserStorage(),
                    services.getTrustSafetyStorage(),
                    services.getMatchStorage(),
                    services.getTrustSafetyService(),
                    session,
                    inputReader);
        }
        return safetyHandler;
    }

    /**
     * Returns the StatsHandler (lazy-initialized).
     */
    public StatsHandler stats() {
        if (statsHandler == null) {
            statsHandler = new StatsHandler(
                    services.getStatsService(), services.getAchievementService(), session, inputReader);
        }
        return statsHandler;
    }

    /**
     * Returns the MessagingHandler (lazy-initialized).
     */
    public MessagingHandler messaging() {
        if (messagingHandler == null) {
            messagingHandler = new MessagingHandler(
                    services.getMessagingService(),
                    services.getMatchStorage(),
                    services.getTrustSafetyStorage(),
                    inputReader,
                    session);
        }
        return messagingHandler;
    }
}
