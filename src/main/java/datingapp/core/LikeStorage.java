package datingapp.core;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Storage interface for Like entities. Defined in core, implemented in storage layer. */
public interface LikeStorage {

  /** Get a specific like record (for Match Quality computation). */
  Optional<Like> getLike(UUID fromUserId, UUID toUserId);

  /** Saves a like/pass action. */
  void save(Like like);

  /** Checks if a like/pass already exists from one user to another. */
  boolean exists(UUID from, UUID to);

  /** Checks if both users have liked each other (mutual LIKE, not PASS). */
  boolean mutualLikeExists(UUID a, UUID b);

  /** Gets all user IDs that the given user has liked or passed. */
  Set<UUID> getLikedOrPassedUserIds(UUID userId);

  /** Gets all user IDs that liked the given user (direction=LIKE). */
  Set<UUID> getUserIdsWhoLiked(UUID userId);

  /** Returns like timestamps for each user who liked the given user (direction=LIKE). */
  java.util.Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId);

  // === Statistics Methods (Phase 0.5b) ===

  /** Count likes/passes GIVEN BY a user with specific direction. */
  int countByDirection(UUID userId, Like.Direction direction);

  /** Count likes/passes RECEIVED BY a user with specific direction. */
  int countReceivedByDirection(UUID userId, Like.Direction direction);

  /** Count mutual likes (users this person liked who also liked them back). */
  int countMutualLikes(UUID userId);

  // === Daily Limit Methods (Phase 1) ===

  /**
   * Count likes given by user since the specified start of day. Used for daily limit enforcement.
   */
  int countLikesToday(UUID userId, Instant startOfDay);

  /**
   * Count passes given by user since the specified start of day. Used for daily limit enforcement.
   */
  int countPassesToday(UUID userId, Instant startOfDay);

  // === Undo Methods (Phase 1) ===

  /**
   * Delete a like by ID. Used for undo functionality.
   *
   * @param likeId the ID of the like to delete
   */
  void delete(UUID likeId);
}
