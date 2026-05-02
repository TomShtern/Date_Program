package datingapp.app.api;

import io.javalin.Javalin;

/** Package-private REST route registration helper. */
final class RestRouteSupport {

    private final Javalin app;
    private final RestApiServer server;

    RestRouteSupport(Javalin app, RestApiServer server) {
        this.app = app;
        this.server = server;
    }

    void registerRoutes() {
        // ────────────────────────────────────────────────────────────────────
        // TRANSPORT NOTE: Loopback mode remains intentionally unauthenticated for
        // local IPC use. Non-loopback/LAN mode now requires the configured shared
        // secret header, and browser clients additionally rely on explicit CORS
        // allowlisting. Mutating routes still require X-User-Id; selected read
        // routes may remain anonymous after the transport guard passes.
        // ────────────────────────────────────────────────────────────────────
        registerHealthRoutes();
        registerAuthRoutes();
        registerLocationRoutes();
        registerUserRoutes();
        registerPhotoRoutes();
        registerMatchingRoutes();
        registerSocialRoutes();
        registerMessagingRoutes();
        registerProfileNoteRoutes();
    }

    private void registerHealthRoutes() {
        app.get("/api/health", ctx -> ctx.json(new RestApiDtos.HealthResponse("ok", System.currentTimeMillis())));
    }

    private void registerAuthRoutes() {
        app.post("/api/auth/signup", server::signup);
        app.post("/api/auth/login", server::login);
        app.post("/api/auth/refresh", server::refresh);
        app.post("/api/auth/logout", server::logout);
        app.get("/api/auth/me", server::me);
    }

    private void registerUserRoutes() {
        app.get("/api/users", server::listUsers);
        app.get("/api/users/{id}", server::getUser);
        app.get("/api/users/{id}/profile-edit-snapshot", server::getProfileEditSnapshot);
        app.get("/api/users/{viewerId}/presentation-context/{targetId}", server::getPresentationContext);
        app.get("/api/users/{id}/browse", server::browseCandidates);
        app.put("/api/users/{id}/profile", server::updateProfile);
        app.get("/api/users/{id}/candidates", server::getCandidates);
        app.delete("/api/users/{id}", server::deleteUser);
    }

    private void registerPhotoRoutes() {
        app.get("/api/users/{id}/photos", server::listPhotos);
        app.post("/api/users/{id}/photos", server::uploadPhoto);
        app.delete("/api/users/{id}/photos/{photoId}", server::deletePhoto);
        app.put("/api/users/{id}/photos/order", server::reorderPhotos);
    }

    private void registerLocationRoutes() {
        app.get("/api/location/countries", server::listLocationCountries);
        app.get("/api/location/cities", server::listLocationCities);
        app.post("/api/location/resolve", server::resolveLocationSelection);
    }

    private void registerMatchingRoutes() {
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

    private void registerSocialRoutes() {
        app.get("/api/users/{id}/notifications", server::getNotifications);
        app.post("/api/users/{id}/notifications/read-all", server::markAllNotificationsRead);
        app.post("/api/users/{id}/notifications/{notificationId}/read", server::markNotificationRead);
        app.get("/api/users/{id}/friend-requests", server::getFriendRequests);
        app.post("/api/users/{id}/friend-requests/{targetId}", server::requestFriendZone);
        app.post("/api/users/{id}/friend-requests/{requestId}/accept", server::acceptFriendRequest);
        app.post("/api/users/{id}/friend-requests/{requestId}/decline", server::declineFriendRequest);
        app.post("/api/users/{id}/relationships/{targetId}/graceful-exit", server::gracefulExit);
        app.post("/api/users/{id}/relationships/{targetId}/unmatch", server::unmatch);
        app.get("/api/users/{id}/blocked-users", server::getBlockedUsers);
        app.post("/api/users/{id}/block/{targetId}", server::blockUser);
        app.delete("/api/users/{id}/block/{targetId}", server::unblockUser);
        app.post("/api/users/{id}/report/{targetId}", server::reportUser);
        app.post("/api/users/{id}/verification/start", server::startVerification);
        app.post("/api/users/{id}/verification/confirm", server::confirmVerification);
    }

    private void registerMessagingRoutes() {
        app.get("/api/users/{id}/conversations", server::getConversations);
        app.delete("/api/users/{id}/conversations/{conversationId}", server::deleteConversation);
        app.post("/api/users/{id}/conversations/{conversationId}/archive", server::archiveConversation);
        app.get("/api/conversations/{conversationId}/messages", server::getMessages);
        app.delete("/api/conversations/{conversationId}/messages/{messageId}", server::deleteMessage);
        app.post("/api/conversations/{conversationId}/messages", server::sendMessage);
    }

    private void registerProfileNoteRoutes() {
        app.get("/api/users/{authorId}/notes", server::listProfileNotes);
        app.get("/api/users/{authorId}/notes/{subjectId}", server::getProfileNote);
        app.put("/api/users/{authorId}/notes/{subjectId}", server::upsertProfileNote);
        app.delete("/api/users/{authorId}/notes/{subjectId}", server::deleteProfileNote);
    }
}
