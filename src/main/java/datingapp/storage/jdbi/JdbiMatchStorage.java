package datingapp.storage.jdbi;

import datingapp.core.Match;
import datingapp.core.Match.ArchiveReason;
import datingapp.core.Match.State;
import datingapp.core.storage.MatchStorage;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                        MERGE INTO matches (id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason)
                        KEY (id)
                        VALUES (:id, :userA, :userB, :createdAt, :state, :endedAt, :endedBy, :endReason)
                        """)
    @Override
    void save(@BindBean Match match);

    @SqlUpdate("""
                        UPDATE matches SET state = :state, ended_at = :endedAt, ended_by = :endedBy, end_reason = :endReason
                        WHERE id = :id
                        """)
    @Override
    void update(@BindBean Match match);

    @SqlQuery("SELECT * FROM matches WHERE id = :matchId")
    @Override
    Optional<Match> get(@Bind("matchId") String matchId);

    @SqlQuery("""
                        SELECT EXISTS (
                            SELECT 1 FROM matches WHERE id = :matchId
                        )
                        """)
    @Override
    boolean exists(@Bind("matchId") String matchId);

    @SqlQuery("""
                        SELECT * FROM matches
                        WHERE (user_a = :userId OR user_b = :userId)
                        AND state = 'ACTIVE'
                        """)
    @Override
    List<Match> getActiveMatchesFor(@Bind("userId") UUID userId);

    @SqlQuery("""
                        SELECT * FROM matches
                        WHERE user_a = :userId OR user_b = :userId
                        """)
    @Override
    List<Match> getAllMatchesFor(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM matches WHERE id = :matchId")
    @Override
    void delete(@Bind("matchId") String matchId);

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

            return new Match(id, userA, userB, createdAt, state, endedAt, endedBy, endReason);
        }
    }
}
