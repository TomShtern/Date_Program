package datingapp.core;

import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Permanently removes soft-deleted records whose retention period has expired.
 *
 * <p>Soft-deleted entities remain in storage for {@link AppConfig#softDeleteRetentionDays()}
 * days, allowing recovery or GDPR-compliant data access requests. After the retention
 * window, this service hard-deletes the rows to reclaim storage.
 *
 * <p>Usage: call {@link #purgeExpired()} on a schedule (e.g., daily cron or admin trigger).
 */
public class PurgeService {

    private static final Logger logger = LoggerFactory.getLogger(PurgeService.class);
    private static final AppConfig CONFIG = AppConfig.defaults();

    private final UserStorage userStorage;
    private final MatchStorage matchStorage;

    public PurgeService(UserStorage userStorage, MatchStorage matchStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
    }

    /**
     * Result of a purge operation.
     *
     * @param usersPurged   number of user rows permanently deleted
     * @param matchesPurged number of match rows permanently deleted
     */
    public static record PurgeResult(int usersPurged, int matchesPurged) {

        /** Total rows purged across all entity types. */
        public int total() {
            return usersPurged + matchesPurged;
        }
    }

    /**
     * Purges all soft-deleted records past the configured retention period.
     *
     * @return a {@link PurgeResult} summarising what was removed
     */
    public PurgeResult purgeExpired() {
        Instant threshold = AppClock.now().minus(CONFIG.softDeleteRetentionDays(), ChronoUnit.DAYS);

        if (logger.isInfoEnabled()) {
            logger.info(
                    "Purging soft-deleted records older than {} (retention: {} days)",
                    threshold,
                    CONFIG.softDeleteRetentionDays());
        }

        int users = userStorage.purgeDeletedBefore(threshold);
        int matches = matchStorage.purgeDeletedBefore(threshold);

        PurgeResult result = new PurgeResult(users, matches);

        if (logger.isInfoEnabled()) {
            logger.info("Purge complete: {} users, {} matches removed ({} total)", users, matches, result.total());
        }

        return result;
    }
}
