package datingapp.core.storage;

import datingapp.core.model.User;
import java.time.Instant;

/** Storage seam for soft-deleting a user and the related rows that should disappear from active reads. */
public interface AccountCleanupStorage {

    /** Soft-deletes the supplied user and any related rows. */
    void softDeleteAccount(User user, Instant deletedAt);
}
