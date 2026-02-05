package datingapp.storage.jdbi;

import datingapp.core.Standout;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Adapter wrapping JdbiStandoutStorage to implement Standout.Storage interface. */
public class JdbiStandoutStorageAdapter implements Standout.Storage {

    private final JdbiStandoutStorage jdbi;

    public JdbiStandoutStorageAdapter(JdbiStandoutStorage jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
        for (Standout s : standouts) {
            jdbi.upsert(new JdbiStandoutStorage.StandoutBindingHelper(s));
        }
    }

    @Override
    public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
        return jdbi.getStandouts(seekerId, date);
    }

    @Override
    public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
        jdbi.markInteracted(seekerId, standoutUserId, date, Instant.now());
    }

    @Override
    public int cleanup(LocalDate before) {
        return jdbi.cleanup(before);
    }
}
