package datingapp.app.bootstrap;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.storage.AccountCleanupStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hard-deletes soft-deleted accounts past the retention window. Supports dry-run for safety. */
public final class PurgeDeletedAccountsWorker {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeDeletedAccountsWorker.class);

    private final AccountCleanupStorage accountCleanupStorage;
    private final AppConfig config;
    private final boolean dryRun;

    public PurgeDeletedAccountsWorker(AccountCleanupStorage accountCleanupStorage, AppConfig config) {
        this(accountCleanupStorage, config, false);
    }

    public PurgeDeletedAccountsWorker(AccountCleanupStorage accountCleanupStorage, AppConfig config, boolean dryRun) {
        this.accountCleanupStorage = accountCleanupStorage;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.dryRun = dryRun;
    }

    public int run() {
        if (accountCleanupStorage == null) {
            return 0;
        }
        int retentionDays = Math.max(1, config.safety().softDeleteRetentionDays());
        Instant cutoff = AppClock.now().minus(Duration.ofDays(retentionDays));
        if (dryRun) {
            int candidates = accountCleanupStorage.countPurgeCandidates(cutoff);
            if (LOG.isInfoEnabled()) {
                LOG.info("Purge dry-run: would purge {} account(s) deleted before {}", candidates, cutoff);
            }
            return 0;
        }
        int purged = accountCleanupStorage.purgeDeletedBefore(cutoff);
        if (purged > 0 && LOG.isInfoEnabled()) {
            LOG.info("Purged {} deleted account(s) older than {}", purged, cutoff);
        }
        return purged;
    }
}
