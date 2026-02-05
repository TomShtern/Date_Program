package datingapp.app.cli;

import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.ValidationService;

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
    private ProfileNotesHandler profileNotesHandler;
    private LikerBrowserHandler likerBrowserHandler;
    private MessagingHandler messagingHandler;
    private RelationshipHandler relationshipHandler;

    /**
     * Creates a new HandlerFactory with dependencies from ServiceRegistry.
     *
     * @param services the centralized service registry
     * @param session the app session for user context
     * @param inputReader the CLI input reader
     */
    public HandlerFactory(ServiceRegistry services, AppSession session, InputReader inputReader) {
        this.services = services;
        this.session = session;
        this.inputReader = inputReader;
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
                    services.getBlockStorage(),
                    services.getDailyService(),
                    services.getUndoService(),
                    services.getMatchQualityService(),
                    services.getUserStorage(),
                    services.getAchievementService(),
                    services.getStatsStorage(),
                    services.getRelationshipTransitionService(),
                    services.getStandoutsService(),
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
                    services.getProfilePreviewService(),
                    services.getAchievementService(),
                    new ValidationService(),
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
                    services.getBlockStorage(),
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
     * Returns the ProfileNotesHandler (lazy-initialized).
     */
    public ProfileNotesHandler profileNotes() {
        if (profileNotesHandler == null) {
            profileNotesHandler = new ProfileNotesHandler(services.getUserStorage(), session, inputReader);
        }
        return profileNotesHandler;
    }

    /**
     * Returns the LikerBrowserHandler (lazy-initialized).
     */
    public LikerBrowserHandler likerBrowser() {
        if (likerBrowserHandler == null) {
            likerBrowserHandler = new LikerBrowserHandler(services.getMatchingService(), session, inputReader);
        }
        return likerBrowserHandler;
    }

    /**
     * Returns the MessagingHandler (lazy-initialized).
     */
    public MessagingHandler messaging() {
        if (messagingHandler == null) {
            messagingHandler = new MessagingHandler(services, inputReader, session);
        }
        return messagingHandler;
    }

    /**
     * Returns the RelationshipHandler (lazy-initialized).
     */
    public RelationshipHandler relationship() {
        if (relationshipHandler == null) {
            relationshipHandler = new RelationshipHandler(
                    services.getRelationshipTransitionService(),
                    services.getSocialStorage(),
                    services.getUserStorage(),
                    session,
                    inputReader);
        }
        return relationshipHandler;
    }
}
