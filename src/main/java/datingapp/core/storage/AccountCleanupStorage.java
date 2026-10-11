package datingapp.core.storage;

import datingapp.core.model.User;
import java.time.Instant;

/** Storage seam for soft-deleting a user and the related rows that should disappear from active reads. */
public interface AccountCleanupStorage {

    /** Soft-deletes the supplied user and any related rows. */
    void softDeleteAccount(User user, Instant deletedAt);

    /** Counts the accounts {@link #purgeDeletedBefore} would purge, without changing anything. */
    default int countPurgeCandidates(Instant cutoff) {
        return 0;
    }

    /**
     * Hard-deletes the graph rows of soft-deleted accounts older than {@code cutoff}. Reports and the moderation
     * audit are kept; an account that appears in a report stays behind as an anonymised row.
     *
     * @return number of accounts purged
     */
    default int purgeDeletedBefore(Instant cutoff) {
        return 0;
    }
}
