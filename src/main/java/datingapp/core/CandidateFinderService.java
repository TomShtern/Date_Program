package datingapp.core;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Interface for finding candidate users for matching. Allows drop-in replacement with different
 * matching strategies.
 */
public interface CandidateFinderService {

    /**
     * Finds candidates for the given seeker.
     *
     * @param seeker The user looking for matches
     * @param allActive All active users in the system
     * @param excluded Set of user IDs to exclude (already interacted, blocked, etc.)
     * @return Sorted list of candidate users
     */
    List<User> findCandidates(User seeker, List<User> allActive, Set<UUID> excluded);
}
