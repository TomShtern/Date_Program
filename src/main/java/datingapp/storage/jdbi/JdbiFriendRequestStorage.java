package datingapp.storage.jdbi;

import datingapp.core.Social.FriendRequest;
import datingapp.core.storage.FriendRequestStorage;
import datingapp.storage.mapper.FriendRequestMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for FriendRequest entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(FriendRequestMapper.class)
public interface JdbiFriendRequestStorage extends FriendRequestStorage {

    @SqlUpdate("""
            INSERT INTO friend_requests (id, from_user_id, to_user_id, created_at, status, responded_at)
            VALUES (:id, :fromUserId, :toUserId, :createdAt, :status, :respondedAt)
            """)
    @Override
    void save(@BindBean FriendRequest request);

    @SqlUpdate("""
            UPDATE friend_requests SET status = :status, responded_at = :respondedAt
            WHERE id = :id
            """)
    @Override
    void update(@BindBean FriendRequest request);

    @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests WHERE id = :id
            """)
    @Override
    Optional<FriendRequest> get(@Bind("id") UUID id);

    @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE ((from_user_id = :user1 AND to_user_id = :user2) OR (from_user_id = :user2 AND to_user_id = :user1))
            AND status = 'PENDING'
            """)
    @Override
    Optional<FriendRequest> getPendingBetween(@Bind("user1") UUID user1, @Bind("user2") UUID user2);

    @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE to_user_id = :userId AND status = 'PENDING'
            """)
    @Override
    List<FriendRequest> getPendingForUser(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM friend_requests WHERE id = :id")
    @Override
    void delete(@Bind("id") UUID id);
}
