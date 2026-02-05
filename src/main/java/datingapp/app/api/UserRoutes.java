package datingapp.app.api;

import datingapp.core.CandidateFinder;
import datingapp.core.ServiceRegistry;
import datingapp.core.User;
import datingapp.core.storage.UserStorage;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST API routes for user-related operations.
 *
 * <p>Handles listing users, getting user details, and finding candidates.
 */
public class UserRoutes {

    private final UserStorage userStorage;
    private final CandidateFinder candidateFinder;

    public UserRoutes(ServiceRegistry services) {
        Objects.requireNonNull(services, "services cannot be null");
        this.userStorage = services.getUserStorage();
        this.candidateFinder = services.getCandidateFinder();
    }

    /** GET /api/users - List all users. */
    public void listUsers(Context ctx) {
        List<UserSummary> users =
                userStorage.findAll().stream().map(UserSummary::from).toList();
        ctx.json(users);
    }

    /** GET /api/users/{id} - Get user by ID. */
    public void getUser(Context ctx) {
        UUID id = parseUuid(ctx.pathParam("id"));
        User user = userStorage.get(id);
        if (user == null) {
            throw new NotFoundResponse("User not found");
        }
        ctx.json(UserDetail.from(user));
    }

    /** GET /api/users/{id}/candidates - Get matching candidates for user. */
    public void getCandidates(Context ctx) {
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

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + value, e);
        }
    }

    /** Minimal user info for lists. */
    public record UserSummary(UUID id, String name, int age, String state) {
        public static UserSummary from(User user) {
            return new UserSummary(
                    user.getId(), user.getName(), user.getAge(), user.getState().name());
        }
    }

    /** Full user detail for single-user queries. */
    public record UserDetail(
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
}
