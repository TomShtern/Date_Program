package datingapp.app.api;

import static datingapp.app.api.RestApiDtos.AchievementSnapshotDto;
import static datingapp.app.api.RestApiDtos.ArchiveConversationRequest;
import static datingapp.app.api.RestApiDtos.ConversationSummary;
import static datingapp.app.api.RestApiDtos.ErrorResponse;
import static datingapp.app.api.RestApiDtos.HealthResponse;
import static datingapp.app.api.RestApiDtos.LikeResponse;
import static datingapp.app.api.RestApiDtos.MarkAllNotificationsReadResponse;
import static datingapp.app.api.RestApiDtos.MatchQualityDto;
import static datingapp.app.api.RestApiDtos.MatchSummary;
import static datingapp.app.api.RestApiDtos.MessageDto;
import static datingapp.app.api.RestApiDtos.ModerationResponse;
import static datingapp.app.api.RestApiDtos.NotificationDto;
import static datingapp.app.api.RestApiDtos.PagedMatchResponse;
import static datingapp.app.api.RestApiDtos.PassResponse;
import static datingapp.app.api.RestApiDtos.PendingLikerDto;
import static datingapp.app.api.RestApiDtos.PendingLikersResponse;
import static datingapp.app.api.RestApiDtos.ProfileNoteDto;
import static datingapp.app.api.RestApiDtos.ProfileNoteUpsertRequest;
import static datingapp.app.api.RestApiDtos.ProfileUpdateRequest;
import static datingapp.app.api.RestApiDtos.ProfileUpdateResponse;
import static datingapp.app.api.RestApiDtos.ReportResponse;
import static datingapp.app.api.RestApiDtos.ReportUserRequest;
import static datingapp.app.api.RestApiDtos.SendMessageRequest;
import static datingapp.app.api.RestApiDtos.StandoutDto;
import static datingapp.app.api.RestApiDtos.StandoutsResponse;
import static datingapp.app.api.RestApiDtos.TransitionResponse;
import static datingapp.app.api.RestApiDtos.UndoResponse;
import static datingapp.app.api.RestApiDtos.UserDetail;
import static datingapp.app.api.RestApiDtos.UserStatsDto;
import static datingapp.app.api.RestApiDtos.UserSummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.ArchiveMatchCommand;
import datingapp.app.usecase.matching.MatchingUseCases.ListPagedMatchesQuery;
import datingapp.app.usecase.matching.MatchingUseCases.MatchQualityByIdQuery;
import datingapp.app.usecase.matching.MatchingUseCases.PendingLikersQuery;
import datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.StandoutsQuery;
import datingapp.app.usecase.matching.MatchingUseCases.UndoSwipeCommand;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.ArchiveConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.CountMessagesByConversationIdsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.DeleteConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.DeleteMessageCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.LoadConversationQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.DeleteProfileNoteCommand;
import datingapp.app.usecase.profile.ProfileUseCases.ProfileNoteQuery;
import datingapp.app.usecase.profile.ProfileUseCases.ProfileNotesQuery;
import datingapp.app.usecase.profile.ProfileUseCases.StatsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.UpdateProfileCommand;
import datingapp.app.usecase.profile.ProfileUseCases.UpsertProfileNoteCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.MarkAllNotificationsReadCommand;
import datingapp.app.usecase.social.SocialUseCases.MarkNotificationReadCommand;
import datingapp.app.usecase.social.SocialUseCases.NotificationsQuery;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API server for the dating application.
 *
 * <p>
 * Provides a lightweight HTTP interface wrapping the core services.
 * All business logic remains in the core layer; this is purely a thin transport
 * adapter.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>GET /api/health - Health check</li>
 * <li>GET /api/users - List all users</li>
 * <li>GET /api/users/{id} - Get user by ID</li>
 * <li>GET /api/users/{id}/candidates - Get matching candidates for user</li>
 * <li>GET /api/users/{id}/matches - Get matches for user</li>
 * <li>POST /api/users/{id}/like/{targetId} - Like a user</li>
 * <li>POST /api/users/{id}/pass/{targetId} - Pass on a user</li>
 * <li>GET /api/users/{id}/conversations - Get conversations</li>
 * <li>GET /api/conversations/{conversationId}/messages - Get messages</li>
 * <li>POST /api/conversations/{conversationId}/messages - Send message</li>
 * </ul>
 */
public class RestApiServer {

    private static final Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    private static final int DEFAULT_PORT = 7070;
    private static final String LOCALHOST_HOST =
            InetAddress.getLoopbackAddress().getHostAddress();
    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int DEFAULT_MATCHES_LIMIT = 20;
    private static final String UNKNOWN_USER = "Unknown";
    private static final String BAD_REQUEST = "BAD_REQUEST";
    private static final String CONFLICT = "CONFLICT";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String FORBIDDEN = "FORBIDDEN";
    private static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    private static final String PATH_AUTHOR_ID = "authorId";
    private static final String PATH_CONVERSATION_ID = "conversationId";
    private static final String PATH_MATCH_ID = "matchId";
    private static final String PATH_MESSAGE_ID = "messageId";
    private static final String PATH_NOTIFICATION_ID = "notificationId";
    private static final String PATH_SUBJECT_ID = "subjectId";
    private static final String PATH_TARGET_ID = "targetId";
    private static final String NOTES_COLLECTION_ROUTE = "/api/users/{authorId}/notes";
    private static final String NOTE_ITEM_ROUTE = "/api/users/{authorId}/notes/{subjectId}";
    private static final String HEADER_ACTING_USER_ID = "X-User-Id";
    private static final String QUERY_ACTING_USER_ID = "userId";
    private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_RATE_LIMIT_REQUESTS = 240;
    /**
     * Pagination query-parameter names shared by match and future list endpoints.
     */
    private static final String PARAM_LIMIT = "limit";

    private static final String PARAM_OFFSET = "offset";

    private final CandidateFinder candidateFinder;
    private final MatchingUseCases matchingUseCases;
    private final MessagingUseCases messagingUseCases;
    private final ProfileUseCases profileUseCases;
    private final SocialUseCases socialUseCases;
    private final ZoneId userTimeZone;
    private final LocalRateLimiter rateLimiter;
    private final int port;
    private Javalin app;

    /** Creates a server with the given services on the default port. */
    public RestApiServer(ServiceRegistry services) {
        this(services, DEFAULT_PORT);
    }

    /** Creates a server with the given services and port. */
    public RestApiServer(ServiceRegistry services, int port) {
        this.candidateFinder = services.getCandidateFinder();
        this.matchingUseCases = services.getMatchingUseCases();
        this.messagingUseCases = services.getMessagingUseCases();
        this.profileUseCases = services.getProfileUseCases();
        this.socialUseCases = services.getSocialUseCases();
        this.userTimeZone = services.getConfig().safety().userTimeZone();
        this.rateLimiter = new LocalRateLimiter(DEFAULT_RATE_LIMIT_WINDOW, DEFAULT_RATE_LIMIT_REQUESTS);
        this.port = port;
    }

    /** Starts the HTTP server. */
    public void start() {
        ObjectMapper mapper = createObjectMapper();

        app = Javalin.create(config -> {
            config.jsonMapper(createJsonMapper(mapper));
            config.http.defaultContentType = "application/json";
        });

        registerRequestGuards();
        registerRoutes();
        registerExceptionHandlers();

        app.start(LOCALHOST_HOST, port);
        if (logger.isWarnEnabled()) {
            logger.warn(
                    "REST API server started on localhost-only (http://{}:{}) for local unauthenticated use only",
                    LOCALHOST_HOST,
                    app.port());
        }
    }

    /** Stops the HTTP server. */
    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("REST API server stopped");
        }
    }

    /** Returns the running Javalin instance for testing. */
    public Javalin getApp() {
        return app;
    }

    // ── Route Registration ──────────────────────────────────────────────

    private void registerRoutes() {
        // ────────────────────────────────────────────────────────────────────
        // AUTHENTICATION NOTE: This REST API is intentionally unauthenticated.
        // It is designed for local IPC use only (CLI tools, local admin scripts).
        // Do NOT expose these endpoints over a public network without adding
        // authentication middleware (e.g., app.before() with a shared secret).
        // All routes below operate without any identity verification.
        // ────────────────────────────────────────────────────────────────────

        new HealthRoutes(app).register();
        new UserRoutes(app, this).register();
        new MatchingRoutes(app, this).register();
        new SocialRoutes(app, this).register();
        new MessagingRoutes(app, this).register();
        new ProfileNoteRoutes(app, this).register();
    }

    private void registerRequestGuards() {
        app.beforeMatched(ctx -> {
            if (!ctx.path().startsWith("/api/")) {
                return;
            }
            enforceLocalhostOnly(ctx);
            enforceRateLimit(ctx);
            enforceScopedIdentity(ctx);
        });
    }

    // ── User Handlers ───────────────────────────────────────────────────

    private void listUsers(Context ctx) {
        var result = profileUseCases.listUsers();
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        List<UserSummary> users = result.data().stream()
                .map(user -> UserSummary.from(user, userTimeZone))
                .toList();
        ctx.json(users);
    }

    private void getUser(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = loadUser(ctx, id);
        if (user == null) {
            return;
        }
        ctx.json(UserDetail.from(user, userTimeZone));
    }

    private void updateProfile(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }
        ProfileUpdateRequest request = ctx.bodyAsClass(ProfileUpdateRequest.class);

        var result = profileUseCases.updateProfile(new UpdateProfileCommand(
                UserContext.api(userId),
                request.bio(),
                request.birthDate(),
                request.gender(),
                request.interestedIn(),
                request.latitude(),
                request.longitude(),
                request.maxDistanceKm(),
                request.minAge(),
                request.maxAge(),
                request.heightCm(),
                request.smoking(),
                request.drinking(),
                request.wantsKids(),
                request.lookingFor(),
                request.education(),
                request.interests(),
                request.dealbreakers()));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(ProfileUpdateResponse.from(result.data().user(), result.data().activated(), userTimeZone));
    }

    private void getCandidates(Context ctx) {
        // Deliberate exception: read-only candidate projection route.
        // browseCandidates() adds daily-pick semantics; this route intentionally returns
        // only the direct candidate list for local tooling/API clients.
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = loadUser(ctx, id);
        if (user == null) {
            return;
        }
        List<UserSummary> candidates = readCandidateSummaries(user).stream()
                .map(candidate -> UserSummary.from(candidate, userTimeZone))
                .toList();
        ctx.json(candidates);
    }

    // ── Match Handlers ──────────────────────────────────────────────────

    private void getMatches(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MATCHES_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result =
                matchingUseCases.listPagedMatches(new ListPagedMatchesQuery(UserContext.api(userId), limit, offset));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }

        var page = result.data().page();
        Map<UUID, User> usersById = result.data().usersById();
        List<MatchSummary> items = page.items().stream()
                .map(match -> toMatchSummary(match, userId, usersById))
                .toList();
        ctx.json(new PagedMatchResponse(items, page.totalCount(), offset, limit, page.hasMore()));
    }

    private void likeUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        var result = matchingUseCases.recordLike(new RecordLikeCommand(
                UserContext.api(userId),
                targetId,
                datingapp.core.connection.ConnectionModels.Like.Direction.LIKE,
                true));
        if (!result.success()) {
            ctx.status(409);
            ctx.json(new ErrorResponse(CONFLICT, result.error().message()));
            return;
        }

        Optional<Match> match = result.data().match();

        if (match.isPresent()) {
            ctx.status(201);
            ctx.json(new LikeResponse(true, "It's a match!", MatchSummary.from(match.get(), userId)));
        } else {
            ctx.status(200);
            ctx.json(new LikeResponse(false, "Like recorded", null));
        }
    }

    private void passUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        var result = matchingUseCases.recordLike(new RecordLikeCommand(
                UserContext.api(userId),
                targetId,
                datingapp.core.connection.ConnectionModels.Like.Direction.PASS,
                false));
        if (!result.success()) {
            ctx.status(409);
            ctx.json(new ErrorResponse(CONFLICT, result.error().message()));
            return;
        }
        ctx.status(200);
        ctx.json(new PassResponse("Passed"));
    }

    private void undoSwipe(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = matchingUseCases.undoSwipe(new UndoSwipeCommand(UserContext.api(userId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(UndoResponse.from(result.data()));
    }

    private void getPendingLikers(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = matchingUseCases.pendingLikers(new PendingLikersQuery(UserContext.api(userId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(new PendingLikersResponse(result.data().stream()
                .map(pendingLiker -> PendingLikerDto.from(pendingLiker, userTimeZone))
                .toList()));
    }

    private void getStandouts(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        User currentUser = loadUser(ctx, userId);
        if (currentUser == null) {
            return;
        }

        var result = matchingUseCases.standouts(new StandoutsQuery(UserContext.api(userId), currentUser));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }

        var standoutResult = result.data();
        ctx.json(new StandoutsResponse(
                standoutResult.result().standouts().stream()
                        .map(standout -> StandoutDto.from(standout, standoutResult.usersById(), userTimeZone))
                        .toList(),
                standoutResult.result().totalCandidates(),
                standoutResult.result().fromCache(),
                standoutResult.result().message()));
    }

    private void archiveMatch(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String matchId = ctx.pathParam(PATH_MATCH_ID);
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = matchingUseCases.archiveMatch(new ArchiveMatchCommand(UserContext.api(userId), matchId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    private void getMatchQuality(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String matchId = ctx.pathParam(PATH_MATCH_ID);
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = matchingUseCases.getMatchQuality(new MatchQualityByIdQuery(UserContext.api(userId), matchId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(MatchQualityDto.from(result.data()));
    }

    private void getStats(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = profileUseCases.getOrComputeStats(new StatsQuery(UserContext.api(userId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(UserStatsDto.from(result.data()));
    }

    private void getAchievements(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }
        boolean checkForNew =
                ctx.queryParamAsClass("checkForNew", Boolean.class).getOrDefault(false);

        var result = profileUseCases.getAchievements(new AchievementsQuery(UserContext.api(userId), checkForNew));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(AchievementSnapshotDto.from(result.data()));
    }

    private void getNotifications(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }
        boolean unreadOnly = ctx.queryParamAsClass("unreadOnly", Boolean.class).getOrDefault(false);

        var result = socialUseCases.notifications(new NotificationsQuery(UserContext.api(userId), unreadOnly));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(result.data().stream().map(NotificationDto::from).toList());
    }

    private void markNotificationRead(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID notificationId = parseUuid(ctx.pathParam(PATH_NOTIFICATION_ID));
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = socialUseCases.markNotificationRead(
                new MarkNotificationReadCommand(UserContext.api(userId), notificationId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    private void markAllNotificationsRead(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result =
                socialUseCases.markAllNotificationsRead(new MarkAllNotificationsReadCommand(UserContext.api(userId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(new MarkAllNotificationsReadResponse(result.data()));
    }

    private void requestFriendZone(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        var result = socialUseCases.requestFriendZone(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(201);
        ctx.json(TransitionResponse.from(result.data()));
    }

    private void acceptFriendRequest(Context ctx) {
        respondToFriendRequest(ctx, FriendRequestAction.ACCEPT);
    }

    private void declineFriendRequest(Context ctx) {
        respondToFriendRequest(ctx, FriendRequestAction.DECLINE);
    }

    private void respondToFriendRequest(Context ctx, FriendRequestAction action) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID requestId = parseUuid(ctx.pathParam("requestId"));
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = socialUseCases.respondToFriendRequest(
                new RespondFriendRequestCommand(UserContext.api(userId), requestId, action));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(TransitionResponse.from(result.data()));
    }

    private void gracefulExit(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        var result = socialUseCases.gracefulExit(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(TransitionResponse.from(result.data()));
    }

    private void unmatch(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        var result = socialUseCases.unmatch(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(TransitionResponse.from(result.data()));
    }

    private void blockUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        var result = socialUseCases.blockUser(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(new ModerationResponse(
                result.data().success(), false, result.data().errorMessage()));
    }

    private void reportUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (loadUser(ctx, userId) == null || loadUser(ctx, targetId) == null) {
            return;
        }

        ReportUserRequest request = ctx.bodyAsClass(ReportUserRequest.class);
        if (request.reason() == null) {
            throw new IllegalArgumentException("reason is required");
        }

        var result = socialUseCases.reportUser(new ReportCommand(
                UserContext.api(userId), targetId, request.reason(), request.description(), request.blockUser()));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(new ReportResponse(
                result.data().success(),
                result.data().userWasBanned(),
                result.data().errorMessage(),
                request.blockUser()));
    }

    // ── Messaging Handlers ──────────────────────────────────────────────

    private void getConversations(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (loadUser(ctx, userId) == null) {
            return;
        }
        var listResult =
                messagingUseCases.listConversations(new ListConversationsQuery(UserContext.api(userId), limit, offset));
        if (!listResult.success()) {
            ctx.status(409);
            ctx.json(new ErrorResponse(CONFLICT, listResult.error().message()));
            return;
        }
        List<ConnectionService.ConversationPreview> previews = listResult.data().conversations();
        Set<String> conversationIds = previews.stream()
                .map(preview -> preview.conversation().getId())
                .collect(java.util.stream.Collectors.toSet());
        var countResult = messagingUseCases.countMessagesByConversationIds(
                new CountMessagesByConversationIdsQuery(UserContext.api(userId), conversationIds));
        if (!countResult.success()) {
            handleUseCaseFailure(ctx, countResult.error());
            return;
        }
        Map<String, Integer> messageCounts = countResult.data();

        List<ConversationSummary> conversations = previews.stream()
                .map(preview -> toConversationSummary(preview, messageCounts))
                .toList();
        ctx.json(conversations);
    }

    private void deleteConversation(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        if (loadUser(ctx, userId) == null) {
            return;
        }

        var result = messagingUseCases.deleteConversation(
                new DeleteConversationCommand(UserContext.api(userId), conversationId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    private void archiveConversation(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        if (loadUser(ctx, userId) == null) {
            return;
        }

        ArchiveConversationRequest request = Optional.ofNullable(ctx.body())
                .filter(body -> !body.isBlank())
                .map(ignored -> ctx.bodyAsClass(ArchiveConversationRequest.class))
                .orElse(new ArchiveConversationRequest(Match.MatchArchiveReason.UNMATCH));
        Match.MatchArchiveReason reason =
                request.reason() != null ? request.reason() : Match.MatchArchiveReason.UNMATCH;

        var result = messagingUseCases.archiveConversation(
                new ArchiveConversationCommand(UserContext.api(userId), conversationId, reason));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    private void getMessages(Context ctx) {
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        UUID actingUserId = requireActingUserId(ctx);
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        ConversationParticipants participants = parseConversationParticipants(conversationId);
        participants.requireParticipant(actingUserId);

        var result = messagingUseCases.loadConversation(new LoadConversationQuery(
                UserContext.api(actingUserId), participants.otherParticipant(actingUserId), limit, offset, true));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        List<MessageDto> messages =
                result.data().messages().stream().map(MessageDto::from).toList();
        ctx.json(messages);
    }

    private void deleteMessage(Context ctx) {
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        UUID messageId = parseUuid(ctx.pathParam(PATH_MESSAGE_ID));
        UUID actingUserId = requireActingUserId(ctx);

        var result = messagingUseCases.deleteMessage(
                new DeleteMessageCommand(UserContext.api(actingUserId), conversationId, messageId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    private void sendMessage(Context ctx) {
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        SendMessageRequest request = ctx.bodyAsClass(SendMessageRequest.class);

        if (request.senderId() == null || request.content() == null) {
            throw new IllegalArgumentException("senderId and content are required");
        }

        resolveActingUserId(ctx).ifPresent(actingUserId -> {
            if (!actingUserId.equals(request.senderId())) {
                throw new ApiForbiddenException("Acting user does not match message sender");
            }
        });
        UUID recipientId = extractRecipientFromConversation(conversationId, request.senderId());
        var result = messagingUseCases.sendMessage(
                new SendMessageCommand(UserContext.api(request.senderId()), recipientId, request.content()));

        if (result.success()) {
            ctx.status(201);
            ctx.json(MessageDto.from(result.data().message()));
        } else {
            ctx.status(409);
            ctx.json(new ErrorResponse(CONFLICT, result.error().message()));
        }
    }

    private void listProfileNotes(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        if (loadUser(ctx, authorId) == null) {
            return;
        }
        var result = profileUseCases.listProfileNotes(new ProfileNotesQuery(UserContext.api(authorId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(result.data().stream().map(ProfileNoteDto::from).toList());
    }

    private void getProfileNote(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        UUID subjectId = parseUuid(ctx.pathParam(PATH_SUBJECT_ID));
        if (loadUser(ctx, authorId) == null) {
            return;
        }
        var result = profileUseCases.getProfileNote(new ProfileNoteQuery(UserContext.api(authorId), subjectId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(ProfileNoteDto.from(result.data()));
    }

    private void upsertProfileNote(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        UUID subjectId = parseUuid(ctx.pathParam(PATH_SUBJECT_ID));
        if (loadUser(ctx, authorId) == null || loadUser(ctx, subjectId) == null) {
            return;
        }
        ProfileNoteUpsertRequest request = ctx.bodyAsClass(ProfileNoteUpsertRequest.class);
        if (request.content() == null) {
            throw new IllegalArgumentException("content is required");
        }

        var result = profileUseCases.upsertProfileNote(
                new UpsertProfileNoteCommand(UserContext.api(authorId), subjectId, request.content()));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(ProfileNoteDto.from(result.data()));
    }

    private void deleteProfileNote(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        UUID subjectId = parseUuid(ctx.pathParam(PATH_SUBJECT_ID));
        if (loadUser(ctx, authorId) == null || loadUser(ctx, subjectId) == null) {
            return;
        }
        var result =
                profileUseCases.deleteProfileNote(new DeleteProfileNoteCommand(UserContext.api(authorId), subjectId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    // ── Shared Helpers ──────────────────────────────────────────────────

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + value, e);
        }
    }

    private User loadUser(Context ctx, UUID userId) {
        var result = profileUseCases.getUserById(userId);
        if (result.success()) {
            return result.data();
        }
        handleUseCaseFailure(ctx, result.error());
        return null;
    }

    private List<User> readCandidateSummaries(User user) {
        // Deliberate exception: this is the remaining direct-read adapter.
        // The route intentionally exposes the raw candidate projection rather than
        // the browseCandidates() daily-pick flow.
        return candidateFinder.findCandidatesForUser(user);
    }

    private MatchSummary toMatchSummary(Match match, UUID currentUserId, Map<UUID, User> usersById) {
        UUID otherUserId = match.getOtherUser(currentUserId);
        User otherUser = usersById.get(otherUserId);
        String otherUserName = otherUser != null ? otherUser.getName() : UNKNOWN_USER;
        return new MatchSummary(
                match.getId(), otherUserId, otherUserName, match.getState().name(), match.getCreatedAt());
    }

    private ConversationSummary toConversationSummary(
            ConnectionService.ConversationPreview preview, Map<String, Integer> messageCounts) {
        Conversation conversation = preview.conversation();
        User otherUser = preview.otherUser();
        int messageCount = messageCounts.getOrDefault(conversation.getId(), 0);
        return new ConversationSummary(
                conversation.getId(),
                otherUser.getId(),
                otherUser.getName(),
                messageCount,
                conversation.getLastMessageAt());
    }

    private UUID extractRecipientFromConversation(String conversationId, UUID senderId) {
        return parseConversationParticipants(conversationId).otherParticipant(senderId);
    }

    private void enforceLocalhostOnly(Context ctx) {
        if (isLoopbackAddress(ctx.ip())) {
            return;
        }
        throw new ApiForbiddenException("REST API is restricted to localhost requests");
    }

    private void enforceRateLimit(Context ctx) {
        if ("/api/health".equals(ctx.path())) {
            return;
        }
        String key = ctx.ip() + '|' + ctx.method();
        if (rateLimiter.tryAcquire(key)) {
            return;
        }
        throw new ApiTooManyRequestsException("Local API rate limit exceeded");
    }

    private void enforceScopedIdentity(Context ctx) {
        validateActingUserMatchesPathParam(ctx, "id");
        validateActingUserMatchesPathParam(ctx, PATH_AUTHOR_ID);

        if (ctx.path().startsWith("/api/conversations/")) {
            UUID actingUserId = requireActingUserId(ctx);
            parseConversationParticipants(ctx.pathParam(PATH_CONVERSATION_ID)).requireParticipant(actingUserId);
        }
    }

    private void validateActingUserMatchesPathParam(Context ctx, String paramName) {
        if (!ctx.pathParamMap().containsKey(paramName)) {
            return;
        }
        Optional<UUID> actingUserId = resolveActingUserId(ctx);
        if (actingUserId.isEmpty()) {
            return;
        }
        UUID routeUserId = parseUuid(ctx.pathParam(paramName));
        if (!routeUserId.equals(actingUserId.get())) {
            throw new ApiForbiddenException("Acting user does not match requested user route");
        }
    }

    private Optional<UUID> resolveActingUserId(Context ctx) {
        String rawUserId = Optional.ofNullable(ctx.header(HEADER_ACTING_USER_ID))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> Optional.ofNullable(ctx.queryParam(QUERY_ACTING_USER_ID))
                        .filter(value -> !value.isBlank())
                        .orElse(null));
        if (rawUserId == null) {
            return Optional.empty();
        }
        return Optional.of(parseUuid(rawUserId));
    }

    private UUID requireActingUserId(Context ctx) {
        return resolveActingUserId(ctx)
                .orElseThrow(() -> new IllegalArgumentException(HEADER_ACTING_USER_ID + " header or "
                        + QUERY_ACTING_USER_ID + " query parameter is required for conversation routes"));
    }

    private boolean isLoopbackAddress(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
    }

    private ConversationParticipants parseConversationParticipants(String conversationId) {
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid conversation ID format");
        }
        try {
            return new ConversationParticipants(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid conversation ID format", ex);
        }
    }

    private void handleUseCaseFailure(Context ctx, datingapp.app.usecase.common.UseCaseError error) {
        switch (error.code()) {
            case VALIDATION -> {
                ctx.status(400);
                ctx.json(new ErrorResponse(BAD_REQUEST, error.message()));
            }
            case NOT_FOUND -> throw new NotFoundResponse(error.message());
            case FORBIDDEN -> {
                ctx.status(403);
                ctx.json(new ErrorResponse(FORBIDDEN, error.message()));
            }
            case DEPENDENCY, INTERNAL -> {
                ctx.status(500);
                ctx.json(new ErrorResponse(INTERNAL_ERROR, error.message()));
            }
            case CONFLICT -> {
                ctx.status(409);
                ctx.json(new ErrorResponse(CONFLICT, error.message()));
            }
            default -> {
                ctx.status(500);
                ctx.json(new ErrorResponse(INTERNAL_ERROR, error.message()));
            }
        }
    }

    // ── Infrastructure ──────────────────────────────────────────────────

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }

    private static io.javalin.json.JsonMapper createJsonMapper(ObjectMapper mapper) {
        return new JavalinJackson(mapper, true);
    }

    private void registerExceptionHandlers() {
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(400);
            ctx.json(new ErrorResponse(BAD_REQUEST, e.getMessage()));
        });

        app.exception(ApiForbiddenException.class, (e, ctx) -> {
            ctx.status(403);
            ctx.json(new ErrorResponse(FORBIDDEN, e.getMessage()));
        });

        app.exception(ApiTooManyRequestsException.class, (e, ctx) -> {
            ctx.status(429);
            ctx.json(new ErrorResponse(TOO_MANY_REQUESTS, e.getMessage()));
        });

        app.exception(IllegalStateException.class, (e, ctx) -> {
            ctx.status(409);
            ctx.json(new ErrorResponse(CONFLICT, e.getMessage()));
        });

        app.exception(java.util.NoSuchElementException.class, (e, ctx) -> {
            ctx.status(404);
            ctx.json(new ErrorResponse("NOT_FOUND", e.getMessage()));
        });

        app.exception(com.fasterxml.jackson.core.JacksonException.class, (e, ctx) -> {
            ctx.status(400);
            ctx.json(new ErrorResponse(BAD_REQUEST, "Invalid request body format"));
        });

        app.exception(Exception.class, (e, ctx) -> {
            if (logger.isErrorEnabled()) {
                logger.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), e);
            }
            ctx.status(500);
            ctx.json(new ErrorResponse(INTERNAL_ERROR, "An unexpected error occurred"));
        });
    }

    private record ConversationParticipants(UUID firstUserId, UUID secondUserId) {
        private boolean involves(UUID userId) {
            return firstUserId.equals(userId) || secondUserId.equals(userId);
        }

        private UUID otherParticipant(UUID userId) {
            if (firstUserId.equals(userId)) {
                return secondUserId;
            }
            if (secondUserId.equals(userId)) {
                return firstUserId;
            }
            throw new ApiForbiddenException("User is not part of this conversation");
        }

        private void requireParticipant(UUID userId) {
            if (!involves(userId)) {
                throw new ApiForbiddenException("User is not part of this conversation");
            }
        }
    }

    private static final class LocalRateLimiter {
        private final long windowMillis;
        private final int maxRequests;
        private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

        private LocalRateLimiter(Duration window, int maxRequests) {
            this.windowMillis = window.toMillis();
            this.maxRequests = maxRequests;
        }

        private boolean tryAcquire(String key) {
            long now = System.currentTimeMillis();
            Window window = windows.compute(key, (ignored, current) -> {
                if (current == null || now - current.windowStartedAtMillis >= windowMillis) {
                    return new Window(now, 1);
                }
                return new Window(current.windowStartedAtMillis, current.requestCount + 1);
            });
            return window.requestCount <= maxRequests;
        }

        private record Window(long windowStartedAtMillis, int requestCount) {}
    }

    private static final class ApiForbiddenException extends RuntimeException {
        private ApiForbiddenException(String message) {
            super(message);
        }
    }

    private static final class ApiTooManyRequestsException extends RuntimeException {
        private ApiTooManyRequestsException(String message) {
            super(message);
        }
    }

    private interface RouteModule {
        void register();
    }

    private static final class HealthRoutes implements RouteModule {
        private final Javalin app;

        private HealthRoutes(Javalin app) {
            this.app = app;
        }

        @Override
        public void register() {
            app.get("/api/health", ctx -> ctx.json(new HealthResponse("ok", System.currentTimeMillis())));
        }
    }

    private static final class UserRoutes implements RouteModule {
        private final Javalin app;
        private final RestApiServer server;

        private UserRoutes(Javalin app, RestApiServer server) {
            this.app = app;
            this.server = server;
        }

        @Override
        public void register() {
            app.get("/api/users", server::listUsers);
            app.get("/api/users/{id}", server::getUser);
            app.put("/api/users/{id}/profile", server::updateProfile);
            app.get("/api/users/{id}/candidates", server::getCandidates);
        }
    }

    private static final class MatchingRoutes implements RouteModule {
        private final Javalin app;
        private final RestApiServer server;

        private MatchingRoutes(Javalin app, RestApiServer server) {
            this.app = app;
            this.server = server;
        }

        @Override
        public void register() {
            app.get("/api/users/{id}/matches", server::getMatches);
            app.get("/api/users/{id}/pending-likers", server::getPendingLikers);
            app.get("/api/users/{id}/standouts", server::getStandouts);
            app.get("/api/users/{id}/match-quality/{matchId}", server::getMatchQuality);
            app.post("/api/users/{id}/like/{targetId}", server::likeUser);
            app.post("/api/users/{id}/pass/{targetId}", server::passUser);
            app.post("/api/users/{id}/matches/{matchId}/archive", server::archiveMatch);
            app.post("/api/users/{id}/undo", server::undoSwipe);
            app.get("/api/users/{id}/stats", server::getStats);
            app.get("/api/users/{id}/achievements", server::getAchievements);
        }
    }

    private static final class SocialRoutes implements RouteModule {
        private final Javalin app;
        private final RestApiServer server;

        private SocialRoutes(Javalin app, RestApiServer server) {
            this.app = app;
            this.server = server;
        }

        @Override
        public void register() {
            app.get("/api/users/{id}/notifications", server::getNotifications);
            app.post("/api/users/{id}/notifications/read-all", server::markAllNotificationsRead);
            app.post("/api/users/{id}/notifications/{notificationId}/read", server::markNotificationRead);
            app.post("/api/users/{id}/friend-requests/{targetId}", server::requestFriendZone);
            app.post("/api/users/{id}/friend-requests/{requestId}/accept", server::acceptFriendRequest);
            app.post("/api/users/{id}/friend-requests/{requestId}/decline", server::declineFriendRequest);
            app.post("/api/users/{id}/relationships/{targetId}/graceful-exit", server::gracefulExit);
            app.post("/api/users/{id}/relationships/{targetId}/unmatch", server::unmatch);
            app.post("/api/users/{id}/block/{targetId}", server::blockUser);
            app.post("/api/users/{id}/report/{targetId}", server::reportUser);
        }
    }

    private static final class MessagingRoutes implements RouteModule {
        private final Javalin app;
        private final RestApiServer server;

        private MessagingRoutes(Javalin app, RestApiServer server) {
            this.app = app;
            this.server = server;
        }

        @Override
        public void register() {
            app.get("/api/users/{id}/conversations", server::getConversations);
            app.delete("/api/users/{id}/conversations/{conversationId}", server::deleteConversation);
            app.post("/api/users/{id}/conversations/{conversationId}/archive", server::archiveConversation);
            app.get("/api/conversations/{conversationId}/messages", server::getMessages);
            app.delete("/api/conversations/{conversationId}/messages/{messageId}", server::deleteMessage);
            app.post("/api/conversations/{conversationId}/messages", server::sendMessage);
        }
    }

    private static final class ProfileNoteRoutes implements RouteModule {
        private final Javalin app;
        private final RestApiServer server;

        private ProfileNoteRoutes(Javalin app, RestApiServer server) {
            this.app = app;
            this.server = server;
        }

        @Override
        public void register() {
            app.get(NOTES_COLLECTION_ROUTE, server::listProfileNotes);
            app.get(NOTE_ITEM_ROUTE, server::getProfileNote);
            app.put(NOTE_ITEM_ROUTE, server::upsertProfileNote);
            app.delete(NOTE_ITEM_ROUTE, server::deleteProfileNote);
        }
    }

    /** Main entry point for standalone REST API server. */
    public static void main(String[] args) {
        ServiceRegistry services = ApplicationStartup.initialize();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        RestApiServer server = new RestApiServer(services, port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
