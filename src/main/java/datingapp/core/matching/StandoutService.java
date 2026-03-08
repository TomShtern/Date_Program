package datingapp.core.matching;

import datingapp.core.model.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for identifying and managing "Standout" profile recommendations.
 * Standouts are top compatibility matches for a user on a given day.
 */
public interface StandoutService {

    /** Get today's standouts for a user. */
    Result getStandouts(User seeker);

    /** Mark a standout as interacted after like/pass. */
    void markInteracted(UUID seekerId, UUID standoutUserId);

    /** Resolve standout user IDs to User objects. */
    Map<UUID, User> resolveUsers(List<Standout> standouts);

    /** Result record for standouts query. */
    record Result(List<Standout> standouts, int totalCandidates, boolean fromCache, String message) {
        public boolean isEmpty() {
            return standouts == null || standouts.isEmpty();
        }

        public int count() {
            return standouts != null ? standouts.size() : 0;
        }

        public static Result empty(String message) {
            return new Result(List.of(), 0, false, message);
        }

        public static Result of(List<Standout> standouts, int total, boolean cached) {
            return new Result(standouts, total, cached, null);
        }
    }
}
