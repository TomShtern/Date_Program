package datingapp.storage.jdbi;

import datingapp.core.Match;
import datingapp.core.Match.ArchiveReason;
import datingapp.core.Match.State;
import datingapp.core.storage.MatchStorage;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Match entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(JdbiMatchStorage.Mapper.class)
public interface JdbiMatchStorage extends MatchStorage {

    @SqlUpdate("""
            MERGE INTO matches (id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason, deleted_at)
            KEY (id)
            VALUES (:id, :userA, :userB, :createdAt, :state, :endedAt, :endedBy, :endReason, :deletedAt)
            """)
    @Override
    void save(@BindBean Match match);

    @SqlUpdate("""
            UPDATE matches SET state = :state, ended_at = :endedAt, ended_by = :endedBy, end_reason = :endReason
            WHERE id = :id
            """)
    @Override
    void update(@BindBean Match match);

    @SqlQuery("SELECT * FROM matches WHERE id = :matchId AND deleted_at IS NULL")
    @Override
    Optional<Match> get(@Bind("matchId") String matchId);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM matches WHERE id = :matchId AND deleted_at IS NULL
            )
            """)
    @Override
    boolean exists(@Bind("matchId") String matchId);

    @SqlQuery("""
            SELECT * FROM matches
            WHERE user_a = :userId AND state = 'ACTIVE' AND deleted_at IS NULL
            """)
    List<Match> getActiveMatchesForUserA(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT * FROM matches
            WHERE user_b = :userId AND state = 'ACTIVE' AND deleted_at IS NULL
            """)
    List<Match> getActiveMatchesForUserB(@Bind("userId") UUID userId);

    @Override
    default List<Match> getActiveMatchesFor(UUID userId) {
        List<Match> fromA = getActiveMatchesForUserA(userId);
        List<Match> fromB = getActiveMatchesForUserB(userId);
        List<Match> combined = new ArrayList<>(fromA.size() + fromB.size());
        combined.addAll(fromA);
        combined.addAll(fromB);
        return combined;
    }

    @SqlQuery("SELECT * FROM matches WHERE user_a = :userId AND deleted_at IS NULL")
    List<Match> getAllMatchesForUserA(@Bind("userId") UUID userId);

    @SqlQuery("SELECT * FROM matches WHERE user_b = :userId AND deleted_at IS NULL")
    List<Match> getAllMatchesForUserB(@Bind("userId") UUID userId);

    @Override
    default List<Match> getAllMatchesFor(UUID userId) {
        List<Match> fromA = getAllMatchesForUserA(userId);
        List<Match> fromB = getAllMatchesForUserB(userId);
        List<Match> combined = new ArrayList<>(fromA.size() + fromB.size());
        combined.addAll(fromA);
        combined.addAll(fromB);
        return combined;
    }

    @SqlUpdate("DELETE FROM matches WHERE id = :matchId")
    @Override
    void delete(@Bind("matchId") String matchId);

    /** Permanently removes soft-deleted matches older than the given threshold. */
    @SqlUpdate("DELETE FROM matches WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
    @Override
    int purgeDeletedBefore(@Bind("threshold") Instant threshold);

    /** Row mapper for Match records - inlined from former MatchMapper class. */
    class Mapper implements RowMapper<Match> {
        @Override
        public Match map(ResultSet rs, StatementContext ctx) throws SQLException {
            String id = rs.getString("id");
            var userA = MapperHelper.readUuid(rs, "user_a");
            var userB = MapperHelper.readUuid(rs, "user_b");
            var createdAt = MapperHelper.readInstant(rs, "created_at");
            var state = MapperHelper.readEnum(rs, "state", State.class);
            var endedAt = MapperHelper.readInstant(rs, "ended_at");
            var endedBy = MapperHelper.readUuid(rs, "ended_by");
            var endReason = MapperHelper.readEnum(rs, "end_reason", ArchiveReason.class);
            var deletedAt = MapperHelper.readInstant(rs, "deleted_at");

            Match match = new Match(id, userA, userB, createdAt, state, endedAt, endedBy, endReason);
            match.setDeletedAt(deletedAt);
            return match;
        }
    }
}
