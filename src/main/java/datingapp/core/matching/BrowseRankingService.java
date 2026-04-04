package datingapp.core.matching;

import datingapp.core.model.User;
import java.util.List;

/** Ranks already-eligible browse candidates for presentation order. */
@FunctionalInterface
public interface BrowseRankingService {

    List<User> rankCandidates(User seeker, List<User> candidates);

    static BrowseRankingService identity() {
        return (seeker, candidates) -> candidates == null ? List.of() : List.copyOf(candidates);
    }
}
