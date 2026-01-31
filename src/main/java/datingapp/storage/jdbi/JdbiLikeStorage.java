package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Like entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(JdbiLikeStorage.Mapper.class)
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

    /**
     * Internal query that returns like times as DTOs.
     * Not exposed in LikeStorage interface - used by default methods.
     */
    @SqlQuery("""
      SELECT who_likes, created_at FROM likes
      WHERE who_got_liked = :userId AND direction = 'LIKE'
      """)
    @RegisterRowMapper(LikeTimeEntryMapper.class)
    List<LikeTimeEntry> getLikeTimesInternal(@Bind("userId") UUID userId);

    @Override
    default List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLikedAsList(UUID userId) {
        return getLikeTimesInternal(userId).stream()
                .map(e -> Map.entry(e.userId(), e.likedAt()))
                .toList();
    }

    @Override
    default Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
        return getLikeTimesInternal(userId).stream()
                .collect(Collectors.toMap(LikeTimeEntry::userId, LikeTimeEntry::likedAt));
    }

    /** Simple DTO to hold liker ID and like timestamp. */
    record LikeTimeEntry(UUID userId, Instant likedAt) {}

    /** Row mapper for LikeTimeEntry. */
    class LikeTimeEntryMapper implements RowMapper<LikeTimeEntry> {
        @Override
        public LikeTimeEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID userId = MapperHelper.readUuid(rs, "who_likes");
            Instant likedAt = MapperHelper.readInstant(rs, "created_at");
            return new LikeTimeEntry(userId, likedAt);
        }
    }

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

    /** Row mapper for Like records - inlined from former LikeMapper class. */
    class Mapper implements RowMapper<Like> {
        @Override
        public Like map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = MapperHelper.readUuid(rs, "id");
            var whoLikes = MapperHelper.readUuid(rs, "who_likes");
            var whoGotLiked = MapperHelper.readUuid(rs, "who_got_liked");
            var direction = MapperHelper.readEnum(rs, "direction", Like.Direction.class);
            var createdAt = MapperHelper.readInstant(rs, "created_at");

            return new Like(id, whoLikes, whoGotLiked, direction, createdAt);
        }
    }
}
