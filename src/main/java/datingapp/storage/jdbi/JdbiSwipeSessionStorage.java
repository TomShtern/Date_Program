package datingapp.storage.jdbi;

import datingapp.core.SwipeSession;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.storage.mapper.SwipeSessionMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for SwipeSession entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(SwipeSessionMapper.class)
public interface JdbiSwipeSessionStorage extends SwipeSessionStorage {

    @SqlUpdate("""
            MERGE INTO swipe_sessions (id, user_id, started_at, last_activity_at, ended_at,
                                       state, swipe_count, like_count, pass_count, match_count)
            KEY (id)
            VALUES (:id, :userId, :startedAt, :lastActivityAt, :endedAt,
                    :state, :swipeCount, :likeCount, :passCount, :matchCount)
            """)
    @Override
    void save(@BindBean SwipeSession session);

    @SqlQuery("""
            SELECT id, user_id, started_at, last_activity_at, ended_at,
                   state, swipe_count, like_count, pass_count, match_count
            FROM swipe_sessions WHERE id = :sessionId
            """)
    @Override
    Optional<SwipeSession> get(@Bind("sessionId") UUID sessionId);

    @SqlQuery("""
            SELECT id, user_id, started_at, last_activity_at, ended_at,
                   state, swipe_count, like_count, pass_count, match_count
            FROM swipe_sessions WHERE user_id = :userId AND state = 'ACTIVE'
            LIMIT 1
            """)
    @Override
    Optional<SwipeSession> getActiveSession(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT id, user_id, started_at, last_activity_at, ended_at,
                   state, swipe_count, like_count, pass_count, match_count
            FROM swipe_sessions WHERE user_id = :userId
            ORDER BY started_at DESC LIMIT :limit
            """)
    @Override
    List<SwipeSession> getSessionsFor(@Bind("userId") UUID userId, @Bind("limit") int limit);

    @SqlQuery("""
            SELECT id, user_id, started_at, last_activity_at, ended_at,
                   state, swipe_count, like_count, pass_count, match_count
            FROM swipe_sessions
            WHERE user_id = :userId AND started_at >= :start AND started_at <= :end
            ORDER BY started_at DESC
            """)
    @Override
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
                ), 0) as avg_duration_seconds
            FROM swipe_sessions
            WHERE user_id = :userId
            """)
    @Override
    SessionAggregates getAggregates(@Bind("userId") UUID userId);

    @SqlUpdate("""
            UPDATE swipe_sessions
            SET state = 'COMPLETED', ended_at = :now
            WHERE state = 'ACTIVE' AND last_activity_at < :cutoff
            """)
    int endStaleSessions(@Bind("now") Instant now, @Bind("cutoff") Instant cutoff);

    /**
     * Wrapper method to match the interface signature with Duration parameter.
     */
    @Override
    default int endStaleSessions(Duration timeout) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(timeout);
        return endStaleSessions(now, cutoff);
    }
}
