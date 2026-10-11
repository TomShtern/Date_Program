package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.storage.AccountCleanupStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PurgeDeletedAccountsWorker")
class PurgeWorkerTest {

    @Test
    @DisplayName("dry-run only counts candidates and live run delegates cutoff")
    void dryRunAndLive() {
        AtomicInteger purgeCalls = new AtomicInteger();
        AtomicInteger countCalls = new AtomicInteger();
        AtomicReference<Instant> purgeCutoff = new AtomicReference<>();
        AccountCleanupStorage storage = new AccountCleanupStorage() {
            @Override
            public void softDeleteAccount(User user, Instant deletedAt) {}

            @Override
            public int countPurgeCandidates(Instant cutoff) {
                countCalls.incrementAndGet();
                return 3;
            }

            @Override
            public int purgeDeletedBefore(Instant cutoff) {
                purgeCalls.incrementAndGet();
                purgeCutoff.set(cutoff);
                return 3;
            }
        };
        PurgeDeletedAccountsWorker dry = new PurgeDeletedAccountsWorker(storage, AppConfig.defaults(), true);
        assertEquals(0, dry.run());
        assertEquals(1, countCalls.get());
        assertEquals(0, purgeCalls.get());

        Instant before = AppClock.now();
        PurgeDeletedAccountsWorker live = new PurgeDeletedAccountsWorker(storage, AppConfig.defaults(), false);
        assertEquals(3, live.run());
        assertEquals(1, purgeCalls.get());
        assertEquals(1, countCalls.get());

        // Default retention is 30 days.
        Duration age = Duration.between(purgeCutoff.get(), before);
        assertEquals(30, AppConfig.defaults().safety().softDeleteRetentionDays());
        assertEquals(30, age.plusSeconds(5).toDays());
    }
}
