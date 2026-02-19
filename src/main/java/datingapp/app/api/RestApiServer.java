package datingapp.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchingService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API server for the dating application.
 *
 * <p>Provides a lightweight HTTP interface wrapping the core services.
 * All business logic remains in the core layer; this is purely a thin transport adapter.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET /api/health - Health check</li>
 *   <li>GET /api/users - List all users</li>
 *   <li>GET /api/users/{id} - Get user by ID</li>
 *   <li>GET /api/users/{id}/candidates - Get matching candidates for user</li>
 *   <li>GET /api/users/{id}/matches - Get matches for user</li>
 *   <li>POST /api/users/{id}/like/{targetId} - Like a user</li>
 *   <li>POST /api/users/{id}/pass/{targetId} - Pass on a user</li>
 *   <li>GET /api/users/{id}/conversations - Get conversations</li>
 *   <li>GET /api/conversations/{conversationId}/messages - Get messages</li>
 *   <li>POST /api/conversations/{conversationId}/messages - Send message</li>
 * </ul>
 */
public class RestApiServer {

    private static final Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    private static final int DEFAULT_PORT = 7070;
    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final String UNKNOWN_USER = "Unknown";

    private final UserStorage userStorage;
    private final InteractionStorage interactionStorage;
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final CommunicationStorage communicationStorage;
    private final ConnectionService messagingService;
    private final int port;
    private Javalin app;

    /** Creates a server with the given services on the default port. */
    public RestApiServer(ServiceRegistry services) {
        this(services, DEFAULT_PORT);
    }

    /** Creates a server with the given services and port. */
    public RestApiServer(ServiceRegistry services, int port) {
        this.userStorage = services.getUserStorage();
        this.interactionStorage = services.getInteractionStorage();
        this.candidateFinder = services.getCandidateFinder();
        this.matchingService = services.getMatchingService();
        this.communicationStorage = services.getCommunicationStorage();
        this.messagingService = services.getConnectionService();
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

        // Messaging routes
        app.get("/api/users/{id}/conversations", this::getConversations);
        app.get("/api/conversations/{conversationId}/messages", this::getMessages);
        app.post("/api/conversations/{conversationId}/messages", this::sendMessage);
    }

    // ── User Handlers ───────────────────────────────────────────────────

    private void listUsers(Context ctx) {
        List<UserSummary> users =
                userStorage.findAll().stream().map(UserSummary::from).toList();
        ctx.json(users);
    }

    private void getUser(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = userStorage.get(id);
        if (user == null) {
            throw new NotFoundResponse("User not found");
        }
        ctx.json(UserDetail.from(user));
    }

    private void getCandidates(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = userStorage.get(id);
        if (user == null) {
            throw new NotFoundResponse("User not found");
        }
        List<UserSummary> candidates = candidateFinder.findCandidatesForUser(user).stream()
                .map(UserSummary::from)
                .toList();
        ctx.json(candidates);
    }

    // ── Match Handlers ──────────────────────────────────────────────────

    private void getMatches(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        validateUserExists(userId);
        List<MatchSummary> matches = interactionStorage.getAllMatchesFor(userId).stream()
                .map(m -> toMatchSummary(m, userId))
                .toList();
        ctx.json(matches);
    }

    private void likeUser(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        UUID targetId = parseUuid(ctx.pathParam("targetId"));
        validateUserExists(userId);
        validateUserExists(targetId);

        Like like = Like.create(userId, targetId, Like.Direction.LIKE);
        Optional<Match> match = matchingService.recordLike(like);

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
        UUID targetId = parseUuid(ctx.pathParam("targetId"));
        validateUserExists(userId);
        validateUserExists(targetId);

        Like pass = Like.create(userId, targetId, Like.Direction.PASS);
        matchingService.recordLike(pass);
        ctx.status(200);
        ctx.json(new PassResponse("Passed"));
    }

    // ── Messaging Handlers ──────────────────────────────────────────────

    private void getConversations(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        validateUserExists(userId);
        List<ConversationSummary> conversations = communicationStorage.getConversationsFor(userId).stream()
                .map(c -> toConversationSummary(c, userId))
                .toList();
        ctx.json(conversations);
    }

    private void getMessages(Context ctx) {
        String conversationId = ctx.pathParam("conversationId");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

        List<MessageDto> messages = communicationStorage.getMessages(conversationId, limit, offset).stream()
                .map(MessageDto::from)
                .toList();
        ctx.json(messages);
    }

    private void sendMessage(Context ctx) {
        String conversationId = ctx.pathParam("conversationId");
        SendMessageRequest request = ctx.bodyAsClass(SendMessageRequest.class);

        if (request.senderId() == null || request.content() == null) {
            throw new IllegalArgumentException("senderId and content are required");
        }

        UUID recipientId = extractRecipientFromConversation(conversationId, request.senderId());
        var result = messagingService.sendMessage(request.senderId(), recipientId, request.content());

        if (result.success()) {
            ctx.status(201);
            ctx.json(MessageDto.from(result.message()));
        } else {
            ctx.status(400);
            ctx.json(new ErrorResponse(result.errorCode().name(), result.errorMessage()));
        }
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
        if (userStorage.get(userId) == null) {
            throw new NotFoundResponse("User not found: " + userId);
        }
    }

    private MatchSummary toMatchSummary(Match match, UUID currentUserId) {
        UUID otherUserId = match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
        User otherUser = userStorage.get(otherUserId);
        String otherUserName = otherUser != null ? otherUser.getName() : UNKNOWN_USER;
        return new MatchSummary(
                match.getId(), otherUserId, otherUserName, match.getState().name(), match.getCreatedAt());
    }

    private ConversationSummary toConversationSummary(Conversation conversation, UUID currentUserId) {
        UUID otherUserId = extractRecipientFromConversation(conversation.getId(), currentUserId);
        User otherUser = userStorage.get(otherUserId);
        String otherUserName = otherUser != null ? otherUser.getName() : UNKNOWN_USER;
        int messageCount = communicationStorage.countMessages(conversation.getId());
        return new ConversationSummary(
                conversation.getId(), otherUserId, otherUserName, messageCount, conversation.getLastMessageAt());
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
            ctx.json(new ErrorResponse("BAD_REQUEST", e.getMessage()));
        });

        app.exception(IllegalStateException.class, (e, ctx) -> {
            ctx.status(409);
            ctx.json(new ErrorResponse("CONFLICT", e.getMessage()));
        });

        app.exception(java.util.NoSuchElementException.class, (e, ctx) -> {
            ctx.status(404);
            ctx.json(new ErrorResponse("NOT_FOUND", e.getMessage()));
        });

        app.exception(com.fasterxml.jackson.core.JacksonException.class, (e, ctx) -> {
            ctx.status(400);
            ctx.json(new ErrorResponse("BAD_REQUEST", "Invalid request body format"));
        });

        app.exception(Exception.class, (e, ctx) -> {
            if (logger.isErrorEnabled()) {
                logger.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), e);
            }
            ctx.status(500);
            ctx.json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });
    }

    // ── Response Records ────────────────────────────────────────────────

    /** Health check response. */
    public static record HealthResponse(String status, long timestamp) {}

    /** Error response. */
    public static record ErrorResponse(String code, String message) {}

    /** Minimal user info for lists. */
    public static record UserSummary(UUID id, String name, int age, String state) {
        public static UserSummary from(User user) {
            return new UserSummary(
                    user.getId(), user.getName(), user.getAge(), user.getState().name());
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
        public static UserDetail from(User user) {
            return new UserDetail(
                    user.getId(),
                    user.getName(),
                    user.getAge(),
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
            UUID otherUserId = match.getUserA().equals(currentUserId) ? match.getUserB() : match.getUserA();
            return new MatchSummary(
                    match.getId(), otherUserId, UNKNOWN_USER, match.getState().name(), match.getCreatedAt());
        }
    }

    /** Response for like action. */
    public static record LikeResponse(boolean isMatch, String message, MatchSummary match) {}

    /** Response for pass action. */
    public static record PassResponse(String message) {}

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

    /** Main entry point for standalone REST API server. */
    public static void main(String[] args) {
        ServiceRegistry services = ApplicationStartup.initialize();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        RestApiServer server = new RestApiServer(services, port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
