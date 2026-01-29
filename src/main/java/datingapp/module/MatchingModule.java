package datingapp.module;

import datingapp.core.AppConfig;
import datingapp.core.CandidateFinder;
import datingapp.core.DailyService;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchingService;
import datingapp.core.SessionService;
import datingapp.core.UndoService;
import java.util.Objects;

/**
 * Module containing matching-related services. Handles the core matching flow: candidates, likes,
 * matches, sessions, undo, and daily picks.
 */
public record MatchingModule(
        CandidateFinder finder,
        MatchingService matching,
        MatchQualityService quality,
        DailyService daily,
        UndoService undo,
        SessionService session)
        implements Module {

    public MatchingModule {
        Objects.requireNonNull(finder, "finder cannot be null");
        Objects.requireNonNull(matching, "matching cannot be null");
        Objects.requireNonNull(quality, "quality cannot be null");
        Objects.requireNonNull(daily, "daily cannot be null");
        Objects.requireNonNull(undo, "undo cannot be null");
        Objects.requireNonNull(session, "session cannot be null");
    }

    /**
     * Creates a MatchingModule with all required services.
     *
     * @param storage The storage module providing data access
     * @param config Application configuration
     * @return Fully configured MatchingModule
     */
    public static MatchingModule create(StorageModule storage, AppConfig config) {
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        CandidateFinder finder = new CandidateFinder();
        SessionService session = new SessionService(storage.swipeSessions(), config);

        MatchingService matching =
                new MatchingService(storage.likes(), storage.matches(), storage.users(), storage.blocks(), session);

        MatchQualityService quality = new MatchQualityService(storage.users(), storage.likes(), config);

        DailyService daily = new DailyService(
                storage.users(), storage.likes(), storage.blocks(), storage.dailyPicks(), finder, config);

        UndoService undo = new UndoService(storage.likes(), storage.matches(), config);

        return new MatchingModule(finder, matching, quality, daily, undo, session);
    }
}
