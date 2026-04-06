package datingapp.core.storage;

import java.time.Instant;
import java.util.UUID;

/** Production/runtime user-storage contract with required operational capabilities. */
public interface OperationalUserStorage extends UserStorage {

    @Override
    int purgeDeletedBefore(Instant threshold);

    @Override
    void executeWithUserLock(UUID userId, Runnable operation);
}
