package datingapp.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import datingapp.core.AppBootstrap;
import datingapp.core.ServiceRegistry;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
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

    private final ServiceRegistry services;
    private final int port;
    private Javalin app;

    /** Creates a server with the given services on the default port. */
    public RestApiServer(ServiceRegistry services) {
        this(services, DEFAULT_PORT);
    }

    /** Creates a server with the given services and port. */
    public RestApiServer(ServiceRegistry services, int port) {
        this.services = services;
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

    private void registerRoutes() {
        var userRoutes = new UserRoutes(services);
        var matchRoutes = new MatchRoutes(services);
        var messagingRoutes = new MessagingRoutes(services);

        // Health check
        app.get("/api/health", ctx -> ctx.json(new HealthResponse("ok", System.currentTimeMillis())));

        // User routes
        app.get("/api/users", userRoutes::listUsers);
        app.get("/api/users/{id}", userRoutes::getUser);
        app.get("/api/users/{id}/candidates", userRoutes::getCandidates);

        // Match routes
        app.get("/api/users/{id}/matches", matchRoutes::getMatches);
        app.post("/api/users/{id}/like/{targetId}", matchRoutes::likeUser);
        app.post("/api/users/{id}/pass/{targetId}", matchRoutes::passUser);

        // Messaging routes
        app.get("/api/users/{id}/conversations", messagingRoutes::getConversations);
        app.get("/api/conversations/{conversationId}/messages", messagingRoutes::getMessages);
        app.post("/api/conversations/{conversationId}/messages", messagingRoutes::sendMessage);
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

        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception", e);
            ctx.status(500);
            ctx.json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });
    }

    /** Health check response. */
    public record HealthResponse(String status, long timestamp) {}

    /** Error response. */
    public record ErrorResponse(String code, String message) {}

    /** Main entry point for standalone REST API server. */
    public static void main(String[] args) {
        ServiceRegistry services = AppBootstrap.initialize();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        RestApiServer server = new RestApiServer(services, port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
