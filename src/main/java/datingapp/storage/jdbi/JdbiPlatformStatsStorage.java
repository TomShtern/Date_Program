package datingapp.storage.jdbi;

import datingapp.core.Stats.PlatformStats;
import datingapp.core.storage.PlatformStatsStorage;
import datingapp.storage.mapper.PlatformStatsMapper;
import java.util.List;
import java.util.Optional;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for PlatformStats entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(PlatformStatsMapper.class)
public interface JdbiPlatformStatsStorage extends PlatformStatsStorage {

    @SqlUpdate("""
            INSERT INTO platform_stats (id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio)
            VALUES (:id, :computedAt, :totalActiveUsers, :avgLikesReceived,
                :avgLikesGiven, :avgMatchRate, :avgLikeRatio)
            """)
    @Override
    void save(@BindBean PlatformStats stats);

    @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    @Override
    Optional<PlatformStats> getLatest();

    @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT :limit
            """)
    @Override
    List<PlatformStats> getHistory(@Bind("limit") int limit);
}
