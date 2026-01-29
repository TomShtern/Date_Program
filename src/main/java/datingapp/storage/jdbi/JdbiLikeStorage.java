package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.storage.mapper.LikeMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Like entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(LikeMapper.class)
public interface JdbiLikeStorage extends LikeStorage {

    @SqlQuery("""
            SELECT * FROM likes
            WHERE who_likes = :fromUserId AND who_got_liked = :toUserId
            """)
    @Override
    Optional<Like> getLike(@Bind("fromUserId") UUID fromUserId, @Bind("toUserId") UUID toUserId);

    @SqlUpdate("""
            INSERT INTO likes (id, who_likes, who_got_liked, direction, created_at)
            VALUES (:id, :whoLikes, :whoGotLiked, :direction, :createdAt)
            """)
    @Override
    void save(@BindBean Like like);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM likes
                WHERE who_likes = :from AND who_got_liked = :to
            )
            """)
    @Override
    boolean exists(@Bind("from") UUID from, @Bind("to") UUID to);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM likes l1
                JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                             AND l1.who_got_liked = l2.who_likes
                WHERE l1.who_likes = :a AND l1.who_got_liked = :b
                  AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
            )
            """)
    @Override
    boolean mutualLikeExists(@Bind("a") UUID a, @Bind("b") UUID b);

    @SqlQuery("SELECT who_got_liked FROM likes WHERE who_likes = :userId")
    @Override
    Set<UUID> getLikedOrPassedUserIds(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT who_likes FROM likes
            WHERE who_got_liked = :userId AND direction = 'LIKE'
            """)
    @Override
    Set<UUID> getUserIdsWhoLiked(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT who_likes, created_at FROM likes
            WHERE who_got_liked = :userId AND direction = 'LIKE'
            """)
    @Override
    Map<UUID, Instant> getLikeTimesForUsersWhoLiked(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT COUNT(*) FROM likes
            WHERE who_likes = :userId AND direction = :direction
            """)
    @Override
    int countByDirection(@Bind("userId") UUID userId, @Bind("direction") Like.Direction direction);

    @SqlQuery("""
            SELECT COUNT(*) FROM likes
            WHERE who_got_liked = :userId AND direction = :direction
            """)
    @Override
    int countReceivedByDirection(@Bind("userId") UUID userId, @Bind("direction") Like.Direction direction);

    @SqlQuery("""
            SELECT COUNT(*) FROM likes l1
            JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                         AND l1.who_got_liked = l2.who_likes
            WHERE l1.who_likes = :userId
              AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
            """)
    @Override
    int countMutualLikes(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT COUNT(*) FROM likes
            WHERE who_likes = :userId
              AND direction = 'LIKE'
              AND created_at >= :startOfDay
            """)
    @Override
    int countLikesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

    @SqlQuery("""
            SELECT COUNT(*) FROM likes
            WHERE who_likes = :userId
              AND direction = 'PASS'
              AND created_at >= :startOfDay
            """)
    @Override
    int countPassesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

    @SqlUpdate("DELETE FROM likes WHERE id = :likeId")
    @Override
    void delete(@Bind("likeId") UUID likeId);
}
