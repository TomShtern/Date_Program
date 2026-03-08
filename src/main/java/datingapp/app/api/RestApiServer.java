package datingapp.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.DeleteProfileNoteCommand;
import datingapp.app.usecase.profile.ProfileUseCases.ProfileNoteQuery;
import datingapp.app.usecase.profile.ProfileUseCases.ProfileNotesQuery;
import datingapp.app.usecase.profile.ProfileUseCases.UpsertProfileNoteCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchingService;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import java.time.Instant;
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
    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int DEFAULT_MATCHES_LIMIT = 20;
    private static final String UNKNOWN_USER = "Unknown";
    private static final String BAD_REQUEST = "BAD_REQUEST";
    private static final String CONFLICT = "CONFLICT";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String PATH_AUTHOR_ID = "authorId";
    private static final String PATH_SUBJECT_ID = "subjectId";
    private static final String PATH_TARGET_ID = "targetId";
    private static final String NOTES_COLLECTION_ROUTE = "/api/users/{authorId}/notes";
    private static final String NOTE_ITEM_ROUTE = "/api/users/{authorId}/notes/{subjectId}";
    /**
     * Pagination query-parameter names shared by match and future list endpoints.
     */
    private static final String PARAM_LIMIT = "limit";

    private static final String PARAM_OFFSET = "offset";

    private final ProfileService profileService;
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;

    private final ConnectionService messagingService;
    private final MatchingUseCases matchingUseCases;
    private final MessagingUseCases messagingUseCases;
    private final ProfileUseCases profileUseCases;
    private final SocialUseCases socialUseCases;
    private final ZoneId userTimeZone;
    private final int port;
    private Javalin app;

    /** Creates a server with the given services on the default port. */
    public RestApiServer(ServiceRegistry services) {
        this(services, DEFAULT_PORT);
    }

    /** Creates a server with the given services and port. */
    public RestApiServer(ServiceRegistry services, int port) {
        this.profileService = services.getProfileService();
        this.candidateFinder = services.getCandidateFinder();
        this.matchingService = services.getMatchingService();
        this.messagingService = services.getConnectionService();
        this.matchingUseCases = services.getMatchingUseCases();
        this.messagingUseCases = services.getMessagingUseCases();
        this.profileUseCases = services.getProfileUseCases();
        this.socialUseCases = services.getSocialUseCases();
        this.userTimeZone = services.getConfig().safety().userTimeZone();
        this.port = port;
    }

    /** Starts the HTTP server. */
    public void start() {
        ObjectMapper mapper = createObjectMapper();

        app = Javalin.create(config -> {
            config.jsonMapper(createJsonMapper(mapper));
            config.http.defaultContentType = "application/json";
        });

        registerRoutes();
        registerExceptionHandlers();

        app.start(port);
        if (logger.isInfoEnabled()) {
            logger.info("REST API server started on port {}", port);
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

        // Health check
        app.get("/api/health", ctx -> ctx.json(new HealthResponse("ok", System.currentTimeMillis())));

        // User routes
        app.get("/api/users", this::listUsers);
        app.get("/api/users/{id}", this::getUser);
        app.get("/api/users/{id}/candidates", this::getCandidates);

        // Match routes
        app.get("/api/users/{id}/matches", this::getMatches);
        app.post("/api/users/{id}/like/{targetId}", this::likeUser);
        app.post("/api/users/{id}/pass/{targetId}", this::passUser);

        // Relationship / moderation routes
        app.post("/api/users/{id}/friend-requests/{targetId}", this::requestFriendZone);
        app.post("/api/users/{id}/friend-requests/{requestId}/accept", this::acceptFriendRequest);
        app.post("/api/users/{id}/friend-requests/{requestId}/decline", this::declineFriendRequest);
        app.post("/api/users/{id}/relationships/{targetId}/graceful-exit", this::gracefulExit);
        app.post("/api/users/{id}/relationships/{targetId}/unmatch", this::unmatch);
        app.post("/api/users/{id}/block/{targetId}", this::blockUser);
        app.post("/api/users/{id}/report/{targetId}", this::reportUser);

        // Messaging routes
        app.get("/api/users/{id}/conversations", this::getConversations);
        app.get("/api/conversations/{conversationId}/messages", this::getMessages);
        app.post("/api/conversations/{conversationId}/messages", this::sendMessage);

        // Private profile-note routes
        app.get(NOTES_COLLECTION_ROUTE, this::listProfileNotes);
        app.get(NOTE_ITEM_ROUTE, this::getProfileNote);
        app.put(NOTE_ITEM_ROUTE, this::upsertProfileNote);
        app.delete(NOTE_ITEM_ROUTE, this::deleteProfileNote);
    }

    // ── User Handlers ───────────────────────────────────────────────────

    private void listUsers(Context ctx) {
        List<UserSummary> users = profileService.listUsers().stream()
                .map(user -> UserSummary.from(user, userTimeZone))
                .toList();
        ctx.json(users);
    }

    private void getUser(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = profileService.getUserById(id).orElseThrow(() -> new NotFoundResponse("User not found"));
        ctx.json(UserDetail.from(user, userTimeZone));
    }

    private void getCandidates(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = profileService.getUserById(id).orElseThrow(() -> new NotFoundResponse("User not found"));
        List<UserSummary> candidates = candidateFinder.findCandidatesForUser(user).stream()
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
        validateUserExists(userId);

        // Use paginated query — prevents OOM for users with thousands of matches.
        var page = matchingService.getPageOfMatchesForUser(userId, offset, limit);
        Set<UUID> otherUserIds = page.items().stream()
                .map(match -> match.getOtherUser(userId))
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> userNamesById = profileService.getUsersByIds(otherUserIds).values().stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getName));
        List<MatchSummary> items = page.items().stream()
                .map(match -> toMatchSummary(match, userId, userNamesById))
                .toList();
        ctx.json(new PagedMatchResponse(items, page.totalCount(), offset, limit, page.hasMore()));
    }

    private void likeUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        validateUserExists(userId);
        validateUserExists(targetId);

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
        validateUserExists(userId);
        validateUserExists(targetId);

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

    private void requestFriendZone(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam(PATH_TARGET_ID));
        validateUserExists(userId);
        validateUserExists(targetId);

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
        validateUserExists(userId);

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
        validateUserExists(userId);
        validateUserExists(targetId);

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
        validateUserExists(userId);
        validateUserExists(targetId);

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
        validateUserExists(userId);
        validateUserExists(targetId);

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
        validateUserExists(userId);
        validateUserExists(targetId);

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
        validateUserExists(userId);
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
        Map<String, Integer> messageCounts = messagingService.countMessagesByConversationIds(conversationIds);

        List<ConversationSummary> conversations = previews.stream()
                .map(preview -> toConversationSummary(preview, messageCounts))
                .toList();
        ctx.json(conversations);
    }

    private void getMessages(Context ctx) {
        String conversationId = ctx.pathParam("conversationId");
        int limit = ctx.queryParamAsClass(PARAM_LIMIT, Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass(PARAM_OFFSET, Integer.class).getOrDefault(0);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        var result = messagingService.getMessages(conversationId, limit, offset);
        if (result.success()) {
            List<MessageDto> messages =
                    result.messages().stream().map(MessageDto::from).toList();
            ctx.json(messages);
        } else {
            ctx.status(400);
            ctx.json(new ErrorResponse(BAD_REQUEST, "Failed to mark messages as read"));
        }
    }

    private void sendMessage(Context ctx) {
        String conversationId = ctx.pathParam("conversationId");
        SendMessageRequest request = ctx.bodyAsClass(SendMessageRequest.class);

        if (request.senderId() == null || request.content() == null) {
            throw new IllegalArgumentException("senderId and content are required");
        }

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

    private void validateUserExists(UUID userId) {
        if (profileService.getUserById(userId).isEmpty()) {
            throw new NotFoundResponse("User not found: " + userId);
        }
    }

    private MatchSummary toMatchSummary(Match match, UUID currentUserId, Map<UUID, String> userNamesById) {
        UUID otherUserId = match.getOtherUser(currentUserId);
        String otherUserName = userNamesById.getOrDefault(otherUserId, UNKNOWN_USER);
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
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid conversation ID format");
        }
        try {
            UUID id1 = UUID.fromString(parts[0]);
            UUID id2 = UUID.fromString(parts[1]);
            return id1.equals(senderId) ? id2 : id1;
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
                ctx.json(new ErrorResponse("FORBIDDEN", error.message()));
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

    // ── Response Records ────────────────────────────────────────────────

    /** Health check response. */
    public static record HealthResponse(String status, long timestamp) {}

    /** Error response. */
    public static record ErrorResponse(String code, String message) {}

    /** Minimal user info for lists. */
    public static record UserSummary(UUID id, String name, int age, String state) {
        /**
         * Creates a UserSummary from a User entity.
         * Uses the provided timezone for age calculation.
         */
        public static UserSummary from(User user, ZoneId userTimeZone) {
            return new UserSummary(
                    user.getId(), user.getName(),
                    user.getAge(userTimeZone).orElse(0), user.getState().name());
        }
    }

    /** Full user detail for single-user queries. */
    public static record UserDetail(
            UUID id,
            String name,
            int age,
            String bio,
            String gender,
            List<String> interestedIn,
            double latitude,
            double longitude,
            int maxDistanceKm,
            List<String> photoUrls,
            String state) {
        /**
         * Creates a UserDetail from a User entity.
         * Uses the provided timezone for age calculation.
         */
        public static UserDetail from(User user, ZoneId userTimeZone) {
            return new UserDetail(
                    user.getId(),
                    user.getName(),
                    user.getAge(userTimeZone).orElse(0),
                    user.getBio(),
                    user.getGender() != null ? user.getGender().name() : null,
                    user.getInterestedIn().stream().map(Enum::name).toList(),
                    user.getLat(),
                    user.getLon(),
                    user.getMaxDistanceKm(),
                    user.getPhotoUrls(),
                    user.getState().name());
        }
    }

    /** Match summary for API responses. */
    public static record MatchSummary(
            String matchId, UUID otherUserId, String otherUserName, String state, Instant createdAt) {
        public static MatchSummary from(Match match, UUID currentUserId) {
            UUID otherUserId = match.getOtherUser(currentUserId);
            return new MatchSummary(
                    match.getId(), otherUserId, UNKNOWN_USER, match.getState().name(), match.getCreatedAt());
        }
    }

    /** Response for like action. */
    public static record LikeResponse(boolean isMatch, String message, MatchSummary match) {}

    /** Response for pass action. */
    public static record PassResponse(String message) {}

    /**
     * Paginated match list response.
     *
     * @param matches    the matches on this page
     * @param totalCount total number of matches across all pages
     * @param offset     zero-based start index of this page
     * @param limit      maximum items per page that was requested
     * @param hasMore    {@code true} if another page exists
     */
    public static record PagedMatchResponse(
            List<MatchSummary> matches, int totalCount, int offset, int limit, boolean hasMore) {}

    /** Conversation summary for API responses. */
    public static record ConversationSummary(
            String id, UUID otherUserId, String otherUserName, int messageCount, Instant lastMessageAt) {}

    /** Message DTO for API responses. */
    public static record MessageDto(UUID id, String conversationId, UUID senderId, String content, Instant sentAt) {
        public static MessageDto from(Message message) {
            return new MessageDto(
                    message.id(), message.conversationId(), message.senderId(), message.content(), message.createdAt());
        }
    }

    /** Request body for sending a message. */
    public static record SendMessageRequest(UUID senderId, String content) {}

    /** Response for relationship transitions. */
    public static record TransitionResponse(boolean success, UUID friendRequestId, String errorMessage) {
        public static TransitionResponse from(ConnectionService.TransitionResult result) {
            FriendRequest request = result.friendRequest();
            return new TransitionResponse(
                    result.success(), request != null ? request.id() : null, result.errorMessage());
        }
    }

    /** Response for moderation operations. */
    public static record ModerationResponse(boolean success, boolean alreadyHandled, String errorMessage) {}

    /** Response for reporting a user. */
    public static record ReportResponse(
            boolean success, boolean autoBanned, String errorMessage, boolean blockedByReporter) {}

    /** Private profile note DTO. */
    public static record ProfileNoteDto(
            UUID authorId, UUID subjectId, String content, Instant createdAt, Instant updatedAt) {
        public static ProfileNoteDto from(ProfileNote note) {
            return new ProfileNoteDto(
                    note.authorId(), note.subjectId(), note.content(), note.createdAt(), note.updatedAt());
        }
    }

    /** Request body for creating or updating a private profile note. */
    public static record ProfileNoteUpsertRequest(String content) {}

    /** Request body for reporting a user. */
    public static record ReportUserRequest(Report.Reason reason, String description, boolean blockUser) {}

    /** Main entry point for standalone REST API server. */
    public static void main(String[] args) {
        ServiceRegistry services = ApplicationStartup.initialize();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        RestApiServer server = new RestApiServer(services, port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
