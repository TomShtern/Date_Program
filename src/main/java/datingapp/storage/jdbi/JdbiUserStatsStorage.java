package datingapp.storage.jdbi;

import datingapp.core.Stats.UserStats;
import datingapp.core.storage.UserStatsStorage;
import datingapp.storage.mapper.UserStatsMapper;
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
 * JDBI storage implementation for UserStats entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(UserStatsMapper.class)
public interface JdbiUserStatsStorage extends UserStatsStorage {

    @SqlUpdate("""
            INSERT INTO user_stats (id, user_id, computed_at,
                total_swipes_given, likes_given, passes_given, like_ratio,
                total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                total_matches, active_matches, match_rate,
                blocks_given, blocks_received, reports_given, reports_received,
                reciprocity_score, selectiveness_score, attractiveness_score)
            VALUES (:id, :userId, :computedAt, :totalSwipesGiven, :likesGiven, :passesGiven, :likeRatio,
                :totalSwipesReceived, :likesReceived, :passesReceived, :incomingLikeRatio,
                :totalMatches, :activeMatches, :matchRate,
                :blocksGiven, :blocksReceived, :reportsGiven, :reportsReceived,
                :reciprocityScore, :selectivenessScore, :attractivenessScore)
            """)
    @Override
    void save(@BindBean UserStats stats);

    @SqlQuery("""
            SELECT id, user_id, computed_at,
                total_swipes_given, likes_given, passes_given, like_ratio,
                total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                total_matches, active_matches, match_rate,
                blocks_given, blocks_received, reports_given, reports_received,
                reciprocity_score, selectiveness_score, attractiveness_score
            FROM user_stats
            WHERE user_id = :userId
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    @Override
    Optional<UserStats> getLatest(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT id, user_id, computed_at,
                total_swipes_given, likes_given, passes_given, like_ratio,
                total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                total_matches, active_matches, match_rate,
                blocks_given, blocks_received, reports_given, reports_received,
                reciprocity_score, selectiveness_score, attractiveness_score
            FROM user_stats
            WHERE user_id = :userId
            ORDER BY computed_at DESC
            LIMIT :limit
            """)
    @Override
    List<UserStats> getHistory(@Bind("userId") UUID userId, @Bind("limit") int limit);

    @SqlQuery("""
            SELECT s.id, s.user_id, s.computed_at,
                s.total_swipes_given, s.likes_given, s.passes_given, s.like_ratio,
                s.total_swipes_received, s.likes_received, s.passes_received, s.incoming_like_ratio,
                s.total_matches, s.active_matches, s.match_rate,
                s.blocks_given, s.blocks_received, s.reports_given, s.reports_received,
                s.reciprocity_score, s.selectiveness_score, s.attractiveness_score
            FROM user_stats s
            INNER JOIN (
                SELECT user_id, MAX(computed_at) as max_date
                FROM user_stats
                GROUP BY user_id
            ) latest ON s.user_id = latest.user_id AND s.computed_at = latest.max_date
            """)
    @Override
    List<UserStats> getAllLatestStats();

    @SqlUpdate("DELETE FROM user_stats WHERE computed_at < :cutoff")
    @Override
    int deleteOlderThan(@Bind("cutoff") Instant cutoff);
}
