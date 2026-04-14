package datingapp.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import datingapp.app.api.RestApiDtos.AchievementSnapshotDto;
import datingapp.app.api.RestApiDtos.ArchiveConversationRequest;
import datingapp.app.api.RestApiDtos.BlockedUsersResponse;
import datingapp.app.api.RestApiDtos.BrowseCandidatesResponse;
import datingapp.app.api.RestApiDtos.ConfirmVerificationRequest;
import datingapp.app.api.RestApiDtos.ConfirmVerificationResponse;
import datingapp.app.api.RestApiDtos.ConversationSummary;
import datingapp.app.api.RestApiDtos.ErrorResponse;
import datingapp.app.api.RestApiDtos.FriendRequestsResponse;
import datingapp.app.api.RestApiDtos.LikeResponse;
import datingapp.app.api.RestApiDtos.LocationCityDto;
import datingapp.app.api.RestApiDtos.LocationCountryDto;
import datingapp.app.api.RestApiDtos.LocationResolveRequest;
import datingapp.app.api.RestApiDtos.LocationResolveResponse;
import datingapp.app.api.RestApiDtos.MarkAllNotificationsReadResponse;
import datingapp.app.api.RestApiDtos.MatchQualityDto;
import datingapp.app.api.RestApiDtos.MatchSummary;
import datingapp.app.api.RestApiDtos.MessageDto;
import datingapp.app.api.RestApiDtos.ModerationResponse;
import datingapp.app.api.RestApiDtos.NotificationDto;
import datingapp.app.api.RestApiDtos.PagedMatchResponse;
import datingapp.app.api.RestApiDtos.PassResponse;
import datingapp.app.api.RestApiDtos.PendingLikersResponse;
import datingapp.app.api.RestApiDtos.ProfileUpdateRequest;
import datingapp.app.api.RestApiDtos.ReportResponse;
import datingapp.app.api.RestApiDtos.SendMessageRequest;
import datingapp.app.api.RestApiDtos.StandoutsResponse;
import datingapp.app.api.RestApiDtos.StartVerificationRequest;
import datingapp.app.api.RestApiDtos.StartVerificationResponse;
import datingapp.app.api.RestApiDtos.TransitionResponse;
import datingapp.app.api.RestApiDtos.UndoResponse;
import datingapp.app.api.RestApiDtos.UserStatsDto;
import datingapp.app.api.RestApiUserDtos.PendingLikerDto;
import datingapp.app.api.RestApiUserDtos.ProfileUpdateResponse;
import datingapp.app.api.RestApiUserDtos.StandoutDto;
import datingapp.app.api.RestApiUserDtos.UserDetail;
import datingapp.app.api.RestApiUserDtos.UserSummary;
import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.app.event.AppEvent;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.ArchiveMatchCommand;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
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
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.StatsQuery;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases.DeleteAccountCommand;
import datingapp.app.usecase.profile.ProfileMutationUseCases.UpdateProfileCommand;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.VerificationUseCases;
import datingapp.app.usecase.profile.VerificationUseCases.ConfirmVerificationCommand;
import datingapp.app.usecase.profile.VerificationUseCases.StartVerificationCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestsQuery;
import datingapp.app.usecase.social.SocialUseCases.ListBlockedUsersQuery;
import datingapp.app.usecase.social.SocialUseCases.MarkAllNotificationsReadCommand;
import datingapp.app.usecase.social.SocialUseCases.MarkNotificationReadCommand;
import datingapp.app.usecase.social.SocialUseCases.NotificationsQuery;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.LocationService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import java.net.InetAddress;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * <li>GET /api/users/{id}/browse - Browse matching candidates for user</li>
 * <li>GET /api/users/{id}/candidates - Get matching candidates for user</li>
 * <li>DELETE /api/users/{id} - Delete user account</li>
 * <li>GET /api/users/{id}/matches - Get matches for user</li>
 * <li>POST /api/users/{id}/like/{targetId} - Like a user</li>
 * <li>POST /api/users/{id}/pass/{targetId} - Pass on a user</li>
 * <li>GET /api/users/{id}/friend-requests - Get pending friend requests</li>
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
    private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_RATE_LIMIT_REQUESTS = 240;
    /**
     * Pagination query-parameter names shared by match and future list endpoints.
     */
    private static final String PARAM_LIMIT = "limit";

    private static final String PARAM_OFFSET = "offset";

    private final MatchingUseCases matchingUseCases;
    private final MessagingUseCases messagingUseCases;
    private final ProfileUseCases profileUseCases;
    private final ProfileMutationUseCases profileMutationUseCases;
    private final ProfileInsightsUseCases profileInsightsUseCases;
    private final ProfileNotesUseCases profileNotesUseCases;
    private final VerificationUseCases verificationUseCases;
    private final SocialUseCases socialUseCases;
    private final LocationService locationService;
    private final ZoneId userTimeZone;
    private final RestApiIdentityPolicy identityPolicy;
    private final RestApiRequestGuards requestGuards;
    private final String host;
    private final boolean restrictToLoopbackClients;
    private final int port;
    private Javalin app;

    /** Creates a server with the given services on the default port. */
    public RestApiServer(ServiceRegistry services) {
        this(services, DEFAULT_PORT);
    }

    /** Creates a server with the given services and port. */
    public RestApiServer(ServiceRegistry services, int port) {
        this(services, LOCALHOST_HOST, port);
    }

    /** Creates a server with the given services, bind host, and port. */
    public RestApiServer(ServiceRegistry services, String host, int port) {
        this.matchingUseCases = services.getMatchingUseCases();
        this.messagingUseCases = services.getMessagingUseCases();
        this.profileUseCases = services.getProfileUseCases();
        this.profileMutationUseCases = services.getProfileMutationUseCases();
        this.profileInsightsUseCases = services.getProfileInsightsUseCases();
        this.profileNotesUseCases = services.getProfileNotesUseCases();
        this.verificationUseCases = services.getVerificationUseCases();
        this.socialUseCases = services.getSocialUseCases();
        this.locationService = services.getLocationService();
        this.userTimeZone = services.getConfig().safety().userTimeZone();
        this.identityPolicy = new RestApiIdentityPolicy();
        this.requestGuards =
                new RestApiRequestGuards(identityPolicy, DEFAULT_RATE_LIMIT_WINDOW, DEFAULT_RATE_LIMIT_REQUESTS);
        this.host = normalizeHost(host);
        this.restrictToLoopbackClients = isLoopbackHost(this.host);
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

        app.start(host, port);
        if (restrictToLoopbackClients && logger.isWarnEnabled()) {
            logger.warn(
                    "REST API server started on localhost-only (http://{}:{}) for local unauthenticated use only",
                    host,
                    app.port());
        } else if (logger.isInfoEnabled()) {
            logger.info("REST API server started on {}:{}", host, app.port());
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
        new RestRouteSupport(app, this).registerRoutes();
    }

    private void registerRequestGuards() {
        requestGuards.registerRequestGuards(app, this::enforceLocalhostOnly);
    }

    // ── User Handlers ───────────────────────────────────────────────────

    void listUsers(Context ctx) {
        Optional<List<User>> usersResult =
                requiredDataOrHandleFailure(ctx, profileUseCases.listUsers(), "User list returned no data");
        if (usersResult.isEmpty()) {
            return;
        }
        List<UserSummary> users = usersResult.get().stream()
                .map(user -> UserSummary.from(user, userTimeZone))
                .toList();
        ctx.json(users);
    }

    void getUser(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        Optional<User> user = loadExistingUser(ctx, id);
        if (user.isEmpty()) {
            return;
        }
        ctx.json(UserDetail.from(user.get(), userTimeZone, locationLabel(user.get())));
    }

    void listLocationCountries(Context ctx) {
        ctx.json(locationService.getAvailableCountries().stream()
                .map(LocationCountryDto::from)
                .toList());
    }

    void listLocationCities(Context ctx) {
        String countryCode = Optional.ofNullable(ctx.queryParam("countryCode"))
                .filter(value -> !value.isBlank())
                .orElse(locationService.getDefaultCountry().code());
        String query = Optional.ofNullable(ctx.queryParam("query")).orElse("");
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(10);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        ctx.json(locationService.searchCities(countryCode, query, limit).stream()
                .map(LocationCityDto::from)
                .toList());
    }

    void resolveLocationSelection(Context ctx) {
        LocationResolveRequest request = ctx.bodyAsClass(LocationResolveRequest.class);
        var result = locationService.resolveSelection(
                request.countryCode(),
                request.cityName(),
                request.zipCode(),
                Boolean.TRUE.equals(request.allowApproximate()));
        if (!result.valid() || result.resolvedLocation().isEmpty()) {
            ctx.status(400).json(new ErrorResponse(BAD_REQUEST, result.message()));
            return;
        }
        ctx.json(LocationResolveResponse.from(
                result.resolvedLocation().orElseThrow(), result.approximate(), result.message()));
    }

    void browseCandidates(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        Optional<User> user = loadExistingUser(ctx, id);
        if (user.isEmpty()) {
            return;
        }
        ensureActiveCandidateBrowser(user.get());

        Optional<BrowseCandidatesResponse> response = loadBrowseResponse(ctx, id, user.get());
        if (response.isEmpty()) {
            return;
        }
        ctx.json(response.get());
    }

    void updateProfile(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }
        ProfileUpdateRequest request = ctx.bodyAsClass(ProfileUpdateRequest.class);

        Optional<ResolvedProfileLocation> resolvedLocation;
        try {
            resolvedLocation = resolveProfileLocation(request);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(new ErrorResponse(BAD_REQUEST, e.getMessage()));
            return;
        }

        Double latitude =
                resolvedLocation.map(ResolvedProfileLocation::latitude).orElse(request.latitude());
        Double longitude =
                resolvedLocation.map(ResolvedProfileLocation::longitude).orElse(request.longitude());

        Optional<ProfileMutationUseCases.ProfileSaveResult> result = requiredDataOrHandleFailure(
                ctx,
                profileMutationUseCases.updateProfile(new UpdateProfileCommand(
                        UserContext.api(userId),
                        request.bio(),
                        request.birthDate(),
                        request.gender(),
                        request.interestedIn(),
                        latitude,
                        longitude,
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
                        request.dealbreakers())),
                "Profile update returned no data");
        if (result.isEmpty()) {
            return;
        }
        ctx.json(ProfileUpdateResponse.from(
                result.get().user(),
                result.get().activated(),
                userTimeZone,
                locationLabel(result.get().user())));
    }

    void deleteUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        if (handleFailureIfNeeded(
                ctx,
                profileMutationUseCases.deleteAccount(
                        new DeleteAccountCommand(UserContext.api(userId), AppEvent.DeletionReason.USER_REQUEST)))) {
            return;
        }
        ctx.status(204);
    }

    void getCandidates(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        Optional<User> user = loadExistingUser(ctx, id);
        if (user.isEmpty()) {
            return;
        }
        ensureActiveCandidateBrowser(user.get());
        Optional<BrowseCandidatesResponse> response = loadBrowseResponse(ctx, id, user.get());
        if (response.isEmpty()) {
            return;
        }
        markCandidatesCompatibilityAlias(ctx, id);
        ctx.json(response.get().candidates());
    }

    private Optional<ResolvedProfileLocation> resolveProfileLocation(ProfileUpdateRequest request) {
        if (request.location() == null) {
            return Optional.empty();
        }
        var location = request.location();
        var result = locationService.resolveSelection(
                location.countryCode(),
                location.cityName(),
                location.zipCode(),
                Boolean.TRUE.equals(location.allowApproximate()));
        if (!result.valid() || result.resolvedLocation().isEmpty()) {
            throw new IllegalArgumentException(result.message());
        }
        var resolvedLocation = result.resolvedLocation().orElseThrow();
        return Optional.of(new ResolvedProfileLocation(resolvedLocation.latitude(), resolvedLocation.longitude()));
    }

    private String locationLabel(User user) {
        if (user == null || !user.hasLocationSet()) {
            return null;
        }
        return locationService.formatForDisplay(user.getLat(), user.getLon());
    }

    // ── Match Handlers ──────────────────────────────────────────────────

    void getMatches(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MATCHES_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void getFriendRequests(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (!ensureUsersExist(ctx, userId)) {
            return;
        }

        Optional<List<datingapp.core.connection.ConnectionModels.FriendRequest>> result = requiredDataOrHandleFailure(
                ctx,
                socialUseCases.pendingFriendRequests(new FriendRequestsQuery(UserContext.api(userId))),
                "Friend requests returned no data");
        if (result.isEmpty()) {
            return;
        }
        ctx.json(FriendRequestsResponse.from(result.get()));
    }

    void likeUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        Optional<User> currentUser = loadExistingUser(ctx, userId);
        Optional<User> targetUser = loadExistingUser(ctx, targetId);
        if (currentUser.isEmpty() || targetUser.isEmpty()) {
            return;
        }

        Optional<MatchingUseCases.RecordLikeResult> result = requiredDataOrHandleFailure(
                ctx,
                matchingUseCases.recordLike(new RecordLikeCommand(
                        UserContext.api(userId),
                        targetId,
                        datingapp.core.connection.ConnectionModels.Like.Direction.LIKE,
                        true)),
                "Like result returned no data");
        if (result.isEmpty()) {
            return;
        }

        Optional<Match> match = result.get().match();

        if (match.isPresent()) {
            ctx.status(201);
            ctx.json(new LikeResponse(
                    true,
                    "It's a match!",
                    MatchSummary.from(
                            match.get(), userId, Map.of(targetUser.get().getId(), targetUser.get()))));
        } else {
            ctx.status(200);
            ctx.json(new LikeResponse(false, "Like recorded", null));
        }
    }

    void passUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
            return;
        }

        if (handleFailureIfNeeded(
                ctx,
                matchingUseCases.recordLike(new RecordLikeCommand(
                        UserContext.api(userId),
                        targetId,
                        datingapp.core.connection.ConnectionModels.Like.Direction.PASS,
                        true)))) {
            return;
        }
        ctx.status(200);
        ctx.json(new PassResponse("Passed"));
    }

    void undoSwipe(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (!ensureUsersExist(ctx, userId)) {
            return;
        }

        Optional<MatchingUseCases.UndoOutcome> result = requiredDataOrHandleFailure(
                ctx,
                matchingUseCases.undoSwipe(new UndoSwipeCommand(UserContext.api(userId))),
                "Undo swipe returned no data");
        if (result.isEmpty()) {
            return;
        }
        ctx.json(UndoResponse.from(result.get()));
    }

    void getPendingLikers(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (!ensureUsersExist(ctx, userId)) {
            return;
        }

        Optional<List<datingapp.core.matching.MatchingService.PendingLiker>> result = requiredDataOrHandleFailure(
                ctx,
                matchingUseCases.pendingLikers(new PendingLikersQuery(UserContext.api(userId))),
                "Pending likers returned no data");
        if (result.isEmpty()) {
            return;
        }
        ctx.json(new PendingLikersResponse(result.get().stream()
                .map(pendingLiker -> PendingLikerDto.from(pendingLiker, userTimeZone))
                .toList()));
    }

    void getStandouts(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        Optional<User> currentUser = loadExistingUser(ctx, userId);
        if (currentUser.isEmpty()) {
            return;
        }

        var result = matchingUseCases.standouts(new StandoutsQuery(UserContext.api(userId), currentUser.get()));
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

    void archiveMatch(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String matchId = ctx.pathParam(PATH_MATCH_ID);
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        var result = matchingUseCases.archiveMatch(new ArchiveMatchCommand(UserContext.api(userId), matchId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    void getMatchQuality(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String matchId = ctx.pathParam(PATH_MATCH_ID);
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        var result = matchingUseCases.getMatchQuality(new MatchQualityByIdQuery(UserContext.api(userId), matchId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(MatchQualityDto.from(result.data()));
    }

    void getStats(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        var result = profileInsightsUseCases.getOrComputeStats(new StatsQuery(UserContext.api(userId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(UserStatsDto.from(result.data()));
    }

    void getAchievements(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }
        boolean checkForNew =
                ctx.queryParamAsClass("checkForNew", Boolean.class).getOrDefault(false);

        var result =
                profileInsightsUseCases.getAchievements(new AchievementsQuery(UserContext.api(userId), checkForNew));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(AchievementSnapshotDto.from(result.data()));
    }

    void getNotifications(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void markNotificationRead(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID notificationId = parseUuid(ctx.pathParam(PATH_NOTIFICATION_ID));
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void markAllNotificationsRead(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void requestFriendZone(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
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

    void acceptFriendRequest(Context ctx) {
        respondToFriendRequest(ctx, FriendRequestAction.ACCEPT);
    }

    void declineFriendRequest(Context ctx) {
        respondToFriendRequest(ctx, FriendRequestAction.DECLINE);
    }

    private void respondToFriendRequest(Context ctx, FriendRequestAction action) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID requestId = parseUuid(ctx.pathParam("requestId"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void gracefulExit(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
            return;
        }

        var result = socialUseCases.gracefulExit(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(TransitionResponse.from(result.data()));
    }

    void unmatch(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
            return;
        }

        var result = socialUseCases.unmatch(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(TransitionResponse.from(result.data()));
    }

    void blockUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
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

    void getBlockedUsers(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        var result = socialUseCases.listBlockedUsers(new ListBlockedUsersQuery(UserContext.api(userId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(BlockedUsersResponse.from(result.data()));
    }

    void unblockUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
            return;
        }

        var result = socialUseCases.unblockUser(new RelationshipCommand(UserContext.api(userId), targetId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.status(204);
    }

    void reportUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        if (!ensureUsersExist(ctx, userId, targetId)) {
            return;
        }

        RestApiDtos.ReportUserRequest request = ctx.bodyAsClass(RestApiDtos.ReportUserRequest.class);
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
                true, result.data().autoBanned(), null, result.data().blockedByReporter()));
    }

    void startVerification(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        StartVerificationRequest request = ctx.bodyAsClass(StartVerificationRequest.class);
        var result = verificationUseCases.startVerification(
                new StartVerificationCommand(UserContext.api(userId), request.method(), request.contact()));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(StartVerificationResponse.from(result.data()));
    }

    void confirmVerification(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }

        ConfirmVerificationRequest request = ctx.bodyAsClass(ConfirmVerificationRequest.class);
        var result = verificationUseCases.confirmVerification(
                new ConfirmVerificationCommand(UserContext.api(userId), request.verificationCode()));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(ConfirmVerificationResponse.from(result.data()));
    }

    // ── Messaging Handlers ──────────────────────────────────────────────

    void getConversations(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (loadExistingUser(ctx, userId).isEmpty()) {
            return;
        }
        var listResult =
                messagingUseCases.listConversations(new ListConversationsQuery(UserContext.api(userId), limit, offset));
        if (!listResult.success()) {
            handleUseCaseFailure(ctx, listResult.error());
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

    void deleteConversation(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void archiveConversation(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        if (loadExistingUser(ctx, userId).isEmpty()) {
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

    void getMessages(Context ctx) {
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        RestApiIdentityPolicy.ConversationParticipants participants = parseConversationParticipants(conversationId);
        UUID requestUserId = requireActingUserId(ctx);
        participants.requireParticipant(requestUserId);
        UUID otherUserId = participants.otherParticipant(requestUserId);

        var result = messagingUseCases.loadConversation(
                new LoadConversationQuery(UserContext.api(requestUserId), otherUserId, limit, offset, true));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        List<MessageDto> messages =
                result.data().messages().stream().map(MessageDto::from).toList();
        ctx.json(messages);
    }

    void deleteMessage(Context ctx) {
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

    void sendMessage(Context ctx) {
        String conversationId = ctx.pathParam(PATH_CONVERSATION_ID);
        SendMessageRequest request = ctx.bodyAsClass(SendMessageRequest.class);

        if (request.senderId() == null || request.content() == null) {
            throw new IllegalArgumentException("senderId and content are required");
        }

        resolveActingUserId(ctx).ifPresent(actingUserId -> {
            if (!actingUserId.equals(request.senderId())) {
                throw new RestApiRequestGuards.ApiForbiddenException("Acting user does not match message sender");
            }
        });
        UUID recipientId = extractRecipientFromConversation(conversationId, request.senderId());
        var result = messagingUseCases.sendMessage(
                new SendMessageCommand(UserContext.api(request.senderId()), recipientId, request.content()));

        if (result.success()) {
            ctx.status(201);
            ctx.json(MessageDto.from(result.data().message()));
        } else {
            handleUseCaseFailure(ctx, result.error());
        }
    }

    void listProfileNotes(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        if (loadExistingUser(ctx, authorId).isEmpty()) {
            return;
        }
        var result = profileNotesUseCases.listProfileNotes(
                new ProfileNotesUseCases.ProfileNotesQuery(UserContext.api(authorId)));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(result.data().stream().map(RestApiDtos.ProfileNoteDto::from).toList());
    }

    void getProfileNote(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        UUID subjectId = parseUuid(ctx.pathParam(PATH_SUBJECT_ID));
        if (loadExistingUser(ctx, authorId).isEmpty()) {
            return;
        }
        var result = profileNotesUseCases.getProfileNote(
                new ProfileNotesUseCases.ProfileNoteQuery(UserContext.api(authorId), subjectId));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(RestApiDtos.ProfileNoteDto.from(result.data()));
    }

    void upsertProfileNote(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        UUID subjectId = parseUuid(ctx.pathParam(PATH_SUBJECT_ID));
        if (!ensureUsersExist(ctx, authorId, subjectId)) {
            return;
        }
        RestApiDtos.ProfileNoteUpsertRequest request = ctx.bodyAsClass(RestApiDtos.ProfileNoteUpsertRequest.class);
        if (request.content() == null) {
            throw new IllegalArgumentException("content is required");
        }

        var result = profileNotesUseCases.upsertProfileNote(new ProfileNotesUseCases.UpsertProfileNoteCommand(
                UserContext.api(authorId), subjectId, request.content()));
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return;
        }
        ctx.json(RestApiDtos.ProfileNoteDto.from(result.data()));
    }

    void deleteProfileNote(Context ctx) {
        UUID authorId = parseUuid(ctx.pathParam(PATH_AUTHOR_ID));
        UUID subjectId = parseUuid(ctx.pathParam(PATH_SUBJECT_ID));
        if (!ensureUsersExist(ctx, authorId, subjectId)) {
            return;
        }
        var result = profileNotesUseCases.deleteProfileNote(
                new ProfileNotesUseCases.DeleteProfileNoteCommand(UserContext.api(authorId), subjectId));
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

    private Optional<User> loadExistingUser(Context ctx, UUID userId) {
        var result = profileUseCases.getUserById(userId);
        if (!result.success()) {
            handleUseCaseFailure(ctx, result.error());
            return Optional.empty();
        }
        if (result.data() == null) {
            ctx.status(500).json(new ErrorResponse(INTERNAL_ERROR, "User lookup returned no data"));
            return Optional.empty();
        }
        return Optional.of(result.data());
    }

    private boolean ensureUsersExist(Context ctx, UUID... userIds) {
        for (UUID userId : userIds) {
            if (loadExistingUser(ctx, userId).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean handleFailureIfNeeded(Context ctx, UseCaseResult<?> result) {
        if (result.success()) {
            return false;
        }
        handleUseCaseFailure(ctx, result.error());
        return true;
    }

    private <T> Optional<T> requiredDataOrHandleFailure(
            Context ctx, UseCaseResult<T> result, String unexpectedNullMessage) {
        if (handleFailureIfNeeded(ctx, result)) {
            return Optional.empty();
        }
        if (result.data() == null) {
            ctx.status(500).json(new ErrorResponse(INTERNAL_ERROR, unexpectedNullMessage));
            return Optional.empty();
        }
        return Optional.of(result.data());
    }

    private Optional<MatchingUseCases.BrowseCandidatesResult> loadBrowseCandidates(
            Context ctx, UUID userId, User user) {
        return requiredDataOrHandleFailure(
                ctx,
                matchingUseCases.browseCandidates(new BrowseCandidatesCommand(UserContext.api(userId), user)),
                "Browse candidates returned no data");
    }

    private Optional<BrowseCandidatesResponse> loadBrowseResponse(Context ctx, UUID userId, User user) {
        Optional<MatchingUseCases.BrowseCandidatesResult> result = loadBrowseCandidates(ctx, userId, user);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BrowseCandidatesResponse.from(result.get(), userTimeZone));
    }

    private MatchSummary toMatchSummary(Match match, UUID currentUserId, Map<UUID, User> usersById) {
        return MatchSummary.from(match, currentUserId, usersById);
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
        return identityPolicy.extractRecipientFromConversation(conversationId, senderId);
    }

    private void enforceLocalhostOnly(Context ctx) {
        if (!restrictToLoopbackClients) {
            return;
        }
        requestGuards.enforceLocalhostOnly(ctx);
    }

    private static String normalizeHost(String configuredHost) {
        return configuredHost == null || configuredHost.isBlank() ? LOCALHOST_HOST : configuredHost.trim();
    }

    private static boolean isLoopbackHost(String configuredHost) {
        try {
            return InetAddress.getByName(configuredHost).isLoopbackAddress();
        } catch (Exception _) {
            return false;
        }
    }

    private void ensureActiveCandidateBrowser(User user) {
        if (user.getState() != UserState.ACTIVE) {
            throw new IllegalStateException("User must be ACTIVE to browse candidates");
        }
    }

    private void markCandidatesCompatibilityAlias(Context ctx, UUID userId) {
        ctx.header("Deprecation", "true");
        ctx.header("Link", "</api/users/" + userId + "/browse>; rel=\"successor-version\"");
    }

    private Optional<UUID> resolveActingUserId(Context ctx) {
        return identityPolicy.resolveActingUserId(ctx);
    }

    private UUID requireActingUserId(Context ctx) {
        return identityPolicy.requireActingUserId(ctx);
    }

    private RestApiIdentityPolicy.ConversationParticipants parseConversationParticipants(String conversationId) {
        return identityPolicy.parseConversationParticipants(conversationId);
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

        app.exception(RestApiRequestGuards.ApiForbiddenException.class, (e, ctx) -> {
            ctx.status(403);
            ctx.json(new ErrorResponse(FORBIDDEN, e.getMessage()));
        });

        app.exception(RestApiRequestGuards.ApiTooManyRequestsException.class, (e, ctx) -> {
            RestApiRequestGuards.RateLimitStatus status = e.status();
            ctx.header("Retry-After", String.valueOf(status.retryAfterSeconds()));
            ctx.header("X-RateLimit-Limit", String.valueOf(status.limit()));
            ctx.header("X-RateLimit-Used", String.valueOf(status.used()));
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

    /** Main entry point for standalone REST API server. */
    public static void main(String[] args) {
        ServiceRegistry services = ApplicationStartup.initialize();
        StartupOptions options = parseStartupOptions(args);
        RestApiServer server = new RestApiServer(services, options.host(), options.port());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            ApplicationStartup.shutdown();
        }));
    }

    private static StartupOptions parseStartupOptions(String[] args) {
        String host = LOCALHOST_HOST;
        int port = DEFAULT_PORT;
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (arg.startsWith("--host=")) {
                host = arg.substring("--host=".length()).trim();
                continue;
            }
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()).trim());
                continue;
            }
            if (arg.chars().allMatch(Character::isDigit)) {
                port = Integer.parseInt(arg);
                continue;
            }
            throw new IllegalArgumentException("Unknown REST API server argument: " + arg);
        }
        return new StartupOptions(normalizeHost(host), port);
    }

    private record ResolvedProfileLocation(Double latitude, Double longitude) {}

    private record StartupOptions(String host, int port) {}
}
