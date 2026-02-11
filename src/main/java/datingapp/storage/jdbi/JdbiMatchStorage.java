package datingapp.storage.jdbi;

import datingapp.core.model.Match;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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

@RegisterRowMapper(JdbiMatchStorage.Mapper.class)
public interface JdbiMatchStorage {
    String MATCH_COLUMNS = "id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason, deleted_at";

    @SqlUpdate("""
            MERGE INTO matches (
                id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason, deleted_at
            ) KEY (id)
            VALUES (
                :id, :userA, :userB, :createdAt, :state, :endedAt, :endedBy, :endReason, :deletedAt
            )
            """)
    void save(@BindBean Match match);

    @SqlUpdate("""
            UPDATE matches
            SET state = :state,
                ended_at = :endedAt,
                ended_by = :endedBy,
                end_reason = :endReason,
                deleted_at = :deletedAt
            WHERE id = :id
            """)
    void update(@BindBean Match match);

    @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE id = :matchId AND deleted_at IS NULL")
    Optional<Match> get(@Bind("matchId") String matchId);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM matches WHERE id = :matchId AND deleted_at IS NULL)")
    boolean exists(@Bind("matchId") String matchId);

    @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE (user_a = :userId OR user_b = :userId) "
            + "AND state = 'ACTIVE' AND deleted_at IS NULL")
    List<Match> getActiveMatchesFor(@Bind("userId") UUID userId);

    @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE (user_a = :userId OR user_b = :userId) "
            + "AND deleted_at IS NULL")
    List<Match> getAllMatchesFor(@Bind("userId") UUID userId);

    @SqlUpdate("UPDATE matches SET deleted_at = CURRENT_TIMESTAMP WHERE id = :matchId")
    void delete(@Bind("matchId") String matchId);

    @SqlUpdate("DELETE FROM matches WHERE deleted_at < :threshold")
    int purgeDeletedBefore(@Bind("threshold") Instant threshold);

    class Mapper implements RowMapper<Match> {
        @Override
        public Match map(ResultSet rs, StatementContext ctx) throws SQLException {
            Match match = new Match(
                    rs.getString("id"),
                    MapperHelper.readUuid(rs, "user_a"),
                    MapperHelper.readUuid(rs, "user_b"),
                    MapperHelper.readInstant(rs, "created_at"),
                    MapperHelper.readEnum(rs, "state", Match.State.class),
                    MapperHelper.readInstant(rs, "ended_at"),
                    MapperHelper.readUuid(rs, "ended_by"),
                    MapperHelper.readEnum(rs, "end_reason", Match.ArchiveReason.class));
            match.setDeletedAt(MapperHelper.readInstant(rs, "deleted_at"));
            return match;
        }
    }
}
