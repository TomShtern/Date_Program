package datingapp.core.testutil;

import datingapp.core.Match;
import datingapp.core.MatchStorage;
import java.util.*;

/**
 * In-memory MatchStorage for testing. Thread-safe and provides test helper methods.
 */
public class InMemoryMatchStorage implements MatchStorage {
    private final Map<String, Match> matches = new HashMap<>();

    @Override
    public void save(Match match) {
        matches.put(match.getId(), match);
    }

    @Override
    public void update(Match match) {
        matches.put(match.getId(), match);
    }

    @Override
    public Optional<Match> get(String matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    @Override
    public boolean exists(String matchId) {
        return matches.containsKey(matchId);
    }

    @Override
    public List<Match> getActiveMatchesFor(UUID userId) {
        return matches.values().stream()
                .filter(m -> m.involves(userId) && m.isActive())
                .toList();
    }

    @Override
    public List<Match> getAllMatchesFor(UUID userId) {
        return matches.values().stream().filter(m -> m.involves(userId)).toList();
    }

    @Override
    public void delete(String matchId) {
        matches.remove(matchId);
    }

    // === Test Helpers ===

    /** Clears all matches */
    public void clear() {
        matches.clear();
    }

    /** Returns number of matches stored */
    public int size() {
        return matches.size();
    }

    /** Returns all matches */
    public List<Match> getAll() {
        return new ArrayList<>(matches.values());
    }
}
