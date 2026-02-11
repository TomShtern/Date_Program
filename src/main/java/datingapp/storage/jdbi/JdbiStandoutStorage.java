package datingapp.storage.jdbi;

import datingapp.core.model.Standout;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/** JDBI interface for standout storage. */
@RegisterRowMapper(JdbiStandoutStorage.StandoutMapper.class)
public interface JdbiStandoutStorage {

    @SqlUpdate("""
            MERGE INTO standouts (id, seeker_id, standout_user_id, featured_date, rank, score, reason, created_at, interacted_at)
            KEY (seeker_id, standout_user_id, featured_date)
            VALUES (:id, :seekerId, :standoutUserId, :featuredDate, :rank, :score, :reason, :createdAt, :interactedAt)
            """)
    void upsert(@BindBean StandoutBindingHelper standout);

    @SqlQuery("""
            SELECT id, seeker_id, standout_user_id, featured_date, rank, score, reason, created_at, interacted_at
            FROM standouts
            WHERE seeker_id = :seekerId AND featured_date = :date
            ORDER BY rank ASC
            """)
    List<Standout> getStandouts(@Bind("seekerId") UUID seekerId, @Bind("date") LocalDate date);

    @SqlUpdate("""
            UPDATE standouts
            SET interacted_at = :now
            WHERE seeker_id = :seekerId AND standout_user_id = :standoutUserId AND featured_date = :date
            """)
    void markInteracted(
            @Bind("seekerId") UUID seekerId,
            @Bind("standoutUserId") UUID standoutUserId,
            @Bind("date") LocalDate date,
            @Bind("now") Instant now);

    @SqlUpdate("DELETE FROM standouts WHERE featured_date < :before")
    int cleanup(@Bind("before") LocalDate before);

    /** Binding helper for Standout record. */
    public static class StandoutBindingHelper {
        private final Standout standout;

        public StandoutBindingHelper(Standout standout) {
            this.standout = standout;
        }

        public UUID getId() {
            return standout.id();
        }

        public UUID getSeekerId() {
            return standout.seekerId();
        }

        public UUID getStandoutUserId() {
            return standout.standoutUserId();
        }

        public LocalDate getFeaturedDate() {
            return standout.featuredDate();
        }

        public int getRank() {
            return standout.rank();
        }

        public int getScore() {
            return standout.score();
        }

        public String getReason() {
            return standout.reason();
        }

        public Instant getCreatedAt() {
            return standout.createdAt();
        }

        public Instant getInteractedAt() {
            return standout.interactedAt();
        }
    }

    /** Row mapper for Standout. */
    public static class StandoutMapper implements org.jdbi.v3.core.mapper.RowMapper<Standout> {
        @Override
        public Standout map(java.sql.ResultSet rs, org.jdbi.v3.core.statement.StatementContext ctx)
                throws java.sql.SQLException {
            java.sql.Timestamp interactedTs = rs.getTimestamp("interacted_at");
            return Standout.fromDatabase(
                    rs.getObject("id", UUID.class),
                    rs.getObject("seeker_id", UUID.class),
                    rs.getObject("standout_user_id", UUID.class),
                    rs.getDate("featured_date").toLocalDate(),
                    rs.getInt("rank"),
                    rs.getInt("score"),
                    rs.getString("reason"),
                    rs.getTimestamp("created_at").toInstant(),
                    interactedTs != null ? interactedTs.toInstant() : null);
        }
    }
}
