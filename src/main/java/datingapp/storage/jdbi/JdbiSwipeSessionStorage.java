package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.model.SwipeSession;
import datingapp.core.storage.AnalyticsStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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

/**
 * JDBI storage implementation for SwipeSession entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(JdbiSwipeSessionStorage.Mapper.class)
public interface JdbiSwipeSessionStorage {

    @SqlUpdate("""
                        MERGE INTO swipe_sessions (id, user_id, started_at, last_activity_at, ended_at,
                                                   state, swipe_count, like_count, pass_count, match_count)
                        KEY (id)
                        VALUES (:id, :userId, :startedAt, :lastActivityAt, :endedAt,
                                :state, :swipeCount, :likeCount, :passCount, :matchCount)
                        """)
    void save(@BindBean SwipeSession session);

    @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions WHERE id = :sessionId
                        """)
    Optional<SwipeSession> get(@Bind("sessionId") UUID sessionId);

    @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions WHERE user_id = :userId AND state = 'ACTIVE'
                        LIMIT 1
                        """)
    Optional<SwipeSession> getActiveSession(@Bind("userId") UUID userId);

    @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions WHERE user_id = :userId
                        ORDER BY started_at DESC LIMIT :limit
                        """)
    List<SwipeSession> getSessionsFor(@Bind("userId") UUID userId, @Bind("limit") int limit);

    @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions
                        WHERE user_id = :userId AND started_at >= :start AND started_at <= :end
                        ORDER BY started_at DESC
                        """)
    List<SwipeSession> getSessionsInRange(
            @Bind("userId") UUID userId, @Bind("start") Instant start, @Bind("end") Instant end);

    @SqlQuery("""
                        SELECT
                            COUNT(*) as total_sessions,
                            COALESCE(SUM(swipe_count), 0) as total_swipes,
                            COALESCE(SUM(like_count), 0) as total_likes,
                            COALESCE(SUM(pass_count), 0) as total_passes,
                            COALESCE(SUM(match_count), 0) as total_matches,
                            COALESCE(AVG(
                                CASE WHEN ended_at IS NOT NULL
                                THEN DATEDIFF('SECOND', started_at, ended_at)
                                ELSE 0 END
                            ), 0) as avg_session_duration_seconds,
                            CASE
                                WHEN COUNT(*) = 0 THEN 0
                                ELSE COALESCE(SUM(swipe_count), 0) * 1.0 / COUNT(*)
                            END as avg_swipes_per_session,
                            CASE
                                WHEN COALESCE(SUM(
                                    CASE WHEN ended_at IS NOT NULL
                                    THEN DATEDIFF('SECOND', started_at, ended_at)
                                    ELSE 0 END
                                ), 0) = 0 THEN 0
                                ELSE COALESCE(SUM(swipe_count), 0) * 1.0 /
                                    COALESCE(SUM(
                                        CASE WHEN ended_at IS NOT NULL
                                        THEN DATEDIFF('SECOND', started_at, ended_at)
                                        ELSE 0 END
                                    ), 0)
                            END as avg_swipe_velocity
                        FROM swipe_sessions
                        WHERE user_id = :userId
                        """)
    AnalyticsStorage.SessionAggregates getAggregates(@Bind("userId") UUID userId);

    @SqlUpdate("""
                        UPDATE swipe_sessions
                        SET state = 'COMPLETED', ended_at = :now
                        WHERE state = 'ACTIVE' AND last_activity_at < :cutoff
                        """)
    int endStaleSessions(@Bind("now") Instant now, @Bind("cutoff") Instant cutoff);

    /**
     * Wrapper method to match the interface signature with Duration parameter.
     */
    default int endStaleSessions(Duration timeout) {
        Instant now = AppClock.now();
        Instant cutoff = now.minus(timeout);
        return endStaleSessions(now, cutoff);
    }

    /**
     * Permanently deletes expired session records older than the cutoff date.
     * Used by SessionService to purge old session data.
     */
    @SqlUpdate("DELETE FROM swipe_sessions WHERE started_at < :cutoff")
    int deleteExpiredSessions(@Bind("cutoff") Instant cutoff);

    /**
     * Row mapper for SwipeSession records - inlined from former SwipeSessionMapper
     * class.
     */
    class Mapper implements RowMapper<SwipeSession> {
        @Override
        public SwipeSession map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID id = MapperHelper.readUuid(rs, "id");
            UUID userId = MapperHelper.readUuid(rs, "user_id");
            var startedAt = MapperHelper.readInstant(rs, "started_at");
            var lastActivityAt = MapperHelper.readInstant(rs, "last_activity_at");
            var endedAt = MapperHelper.readInstant(rs, "ended_at");
            SwipeSession.State state = SwipeSession.State.valueOf(rs.getString("state"));
            int swipeCount = rs.getInt("swipe_count");
            int likeCount = rs.getInt("like_count");
            int passCount = rs.getInt("pass_count");
            int matchCount = rs.getInt("match_count");

            return new SwipeSession(
                    id,
                    userId,
                    startedAt,
                    lastActivityAt,
                    endedAt,
                    state,
                    swipeCount,
                    likeCount,
                    passCount,
                    matchCount);
        }
    }
}
