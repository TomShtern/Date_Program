package datingapp.storage.jdbi;

import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.UserStats;
import datingapp.core.storage.StatsStorage;
import datingapp.storage.mapper.MapperHelper;
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

/**
 * Consolidated JDBI storage implementation for statistics.
 * Combines operations from former JdbiUserStatsStorage and
 * JdbiPlatformStatsStorage.
 */
public interface JdbiStatsStorage extends StatsStorage {

    // ═══════════════════════════════════════════════════════════════
    // User Stats Operations
    // ═══════════════════════════════════════════════════════════════

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
    void saveUserStats(@BindBean UserStats stats);

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
    @RegisterRowMapper(UserStatsMapper.class)
    @Override
    Optional<UserStats> getLatestUserStats(@Bind("userId") UUID userId);

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
    @RegisterRowMapper(UserStatsMapper.class)
    @Override
    List<UserStats> getUserStatsHistory(@Bind("userId") UUID userId, @Bind("limit") int limit);

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
    @RegisterRowMapper(UserStatsMapper.class)
    @Override
    List<UserStats> getAllLatestUserStats();

    @SqlUpdate("DELETE FROM user_stats WHERE computed_at < :cutoff")
    @Override
    int deleteUserStatsOlderThan(@Bind("cutoff") Instant cutoff);

    // ═══════════════════════════════════════════════════════════════
    // Platform Stats Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO platform_stats (id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio)
            VALUES (:id, :computedAt, :totalActiveUsers, :avgLikesReceived,
                :avgLikesGiven, :avgMatchRate, :avgLikeRatio)
            """)
    @Override
    void savePlatformStats(@BindBean PlatformStats stats);

    @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    @RegisterRowMapper(PlatformStatsMapper.class)
    @Override
    Optional<PlatformStats> getLatestPlatformStats();

    @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT :limit
            """)
    @RegisterRowMapper(PlatformStatsMapper.class)
    @Override
    List<PlatformStats> getPlatformStatsHistory(@Bind("limit") int limit);

    // ═══════════════════════════════════════════════════════════════
    // Row Mappers
    // ═══════════════════════════════════════════════════════════════

    /** Row mapper for UserStats records. */
    class UserStatsMapper implements RowMapper<UserStats> {
        @Override
        public UserStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserStats(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readUuid(rs, "user_id"),
                    MapperHelper.readInstant(rs, "computed_at"),
                    rs.getInt("total_swipes_given"),
                    rs.getInt("likes_given"),
                    rs.getInt("passes_given"),
                    rs.getDouble("like_ratio"),
                    rs.getInt("total_swipes_received"),
                    rs.getInt("likes_received"),
                    rs.getInt("passes_received"),
                    rs.getDouble("incoming_like_ratio"),
                    rs.getInt("total_matches"),
                    rs.getInt("active_matches"),
                    rs.getDouble("match_rate"),
                    rs.getInt("blocks_given"),
                    rs.getInt("blocks_received"),
                    rs.getInt("reports_given"),
                    rs.getInt("reports_received"),
                    rs.getDouble("reciprocity_score"),
                    rs.getDouble("selectiveness_score"),
                    rs.getDouble("attractiveness_score"));
        }
    }

    /** Row mapper for PlatformStats records. */
    class PlatformStatsMapper implements RowMapper<PlatformStats> {
        @Override
        public PlatformStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PlatformStats(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readInstant(rs, "computed_at"),
                    rs.getInt("total_active_users"),
                    rs.getDouble("avg_likes_received"),
                    rs.getDouble("avg_likes_given"),
                    rs.getDouble("avg_match_rate"),
                    rs.getDouble("avg_like_ratio"));
        }
    }
}
