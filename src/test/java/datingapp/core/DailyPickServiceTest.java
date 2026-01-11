package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailyPickServiceTest {

  private DailyPickService service;
  private InMemoryUserStorage userStorage;
  private InMemoryLikeStorage likeStorage;
  private InMemoryBlockStorage blockStorage;
  private InMemoryDailyPickStorage dailyPickStorage;
  private AppConfig config;

  @BeforeEach
  void setUp() {
    userStorage = new InMemoryUserStorage();
    likeStorage = new InMemoryLikeStorage();
    blockStorage = new InMemoryBlockStorage();
    dailyPickStorage = new InMemoryDailyPickStorage();
    config = AppConfig.defaults();

    service =
        new DailyPickService(userStorage, likeStorage, blockStorage, dailyPickStorage, config);
  }

  @Test
  void getDailyPick_sameDateSameUser_returnsSamePick() {
    // Create seeker and candidates
    User seeker = createActiveUser("Alice", 25);
    User candidate1 = createActiveUser("Bob", 26);
    User candidate2 = createActiveUser("Charlie", 27);
    User candidate3 = createActiveUser("David", 28);

    userStorage.save(seeker);
    userStorage.save(candidate1);
    userStorage.save(candidate2);
    userStorage.save(candidate3);

    // Get daily pick twice in the same call
    Optional<DailyPickService.DailyPick> pick1 = service.getDailyPick(seeker);
    Optional<DailyPickService.DailyPick> pick2 = service.getDailyPick(seeker);

    assertTrue(pick1.isPresent());
    assertTrue(pick2.isPresent());
    assertEquals(
        pick1.get().user().getId(),
        pick2.get().user().getId(),
        "Same user should get same pick on same date");
    assertEquals(
        pick1.get().reason(), pick2.get().reason(), "Reason should be same for deterministic pick");
  }

  @Test
  void getDailyPick_differentUsers_mayReturnDifferentPicks() {
    // Create two seekers and candidates
    User alice = createActiveUser("Alice", 25);
    User bob = createActiveUser("Bob", 26);
    User candidate1 = createActiveUser("Charlie", 27);
    User candidate2 = createActiveUser("David", 28);
    User candidate3 = createActiveUser("Eve", 29);

    userStorage.save(alice);
    userStorage.save(bob);
    userStorage.save(candidate1);
    userStorage.save(candidate2);
    userStorage.save(candidate3);

    // Get daily picks for different users
    Optional<DailyPickService.DailyPick> alicePick = service.getDailyPick(alice);
    Optional<DailyPickService.DailyPick> bobPick = service.getDailyPick(bob);

    assertTrue(alicePick.isPresent());
    assertTrue(bobPick.isPresent());
    // Different users should generally get different picks (deterministic per user)
    // Note: They could theoretically get same pick by chance with only 3 candidates
  }

  @Test
  void getDailyPick_respectsBlockList() {
    User seeker = createActiveUser("Alice", 25);
    User candidate1 = createActiveUser("Bob", 26);
    User candidate2 = createActiveUser("Charlie", 27);

    userStorage.save(seeker);
    userStorage.save(candidate1);
    userStorage.save(candidate2);

    // Block one candidate
    blockStorage.save(Block.create(seeker.getId(), candidate1.getId()));

    // Get daily pick - should not include blocked user
    Optional<DailyPickService.DailyPick> pick = service.getDailyPick(seeker);
    assertTrue(pick.isPresent());
    assertNotEquals(
        candidate1.getId(),
        pick.get().user().getId(),
        "Blocked user should not appear as daily pick");
  }

  @Test
  void getDailyPick_excludesAlreadySwiped() {
    User seeker = createActiveUser("Alice", 25);
    User candidate1 = createActiveUser("Bob", 26);
    User candidate2 = createActiveUser("Charlie", 27);
    User candidate3 = createActiveUser("David", 28);

    userStorage.save(seeker);
    userStorage.save(candidate1);
    userStorage.save(candidate2);
    userStorage.save(candidate3);

    // Like one candidate
    likeStorage.save(Like.create(seeker.getId(), candidate1.getId(), Like.Direction.LIKE));

    // Get daily pick - should not include already-liked user
    Optional<DailyPickService.DailyPick> pick = service.getDailyPick(seeker);
    assertTrue(pick.isPresent());
    assertNotEquals(
        candidate1.getId(),
        pick.get().user().getId(),
        "Already swiped user should not appear as daily pick");
  }

  @Test
  void getDailyPick_noCandidates_returnsEmpty() {
    User seeker = createActiveUser("Alice", 25);
    userStorage.save(seeker);

    Optional<DailyPickService.DailyPick> pick = service.getDailyPick(seeker);

    assertFalse(pick.isPresent(), "Should return empty when no candidates");
  }

  @Test
  void getDailyPick_allCandidatesExcluded_returnsEmpty() {
    User seeker = createActiveUser("Alice", 25);
    User candidate = createActiveUser("Bob", 26);

    userStorage.save(seeker);
    userStorage.save(candidate);

    // Swipe on the only candidate
    likeStorage.save(Like.create(seeker.getId(), candidate.getId(), Like.Direction.PASS));

    Optional<DailyPickService.DailyPick> pick = service.getDailyPick(seeker);

    assertFalse(pick.isPresent(), "Should return empty when all candidates excluded");
  }

  @Test
  void getDailyPick_reasonIsNeverNull() {
    User seeker = createActiveUser("Alice", 25);
    User candidate = createActiveUser("Bob", 26);

    userStorage.save(seeker);
    userStorage.save(candidate);

    Optional<DailyPickService.DailyPick> pick = service.getDailyPick(seeker);

    assertTrue(pick.isPresent());
    assertNotNull(pick.get().reason());
    assertFalse(pick.get().reason().isBlank(), "Reason should not be blank");
  }

  @Test
  void getDailyPick_setsTodaysDate() {
    User seeker = createActiveUser("Alice", 25);
    User candidate = createActiveUser("Bob", 26);

    userStorage.save(seeker);
    userStorage.save(candidate);

    Optional<DailyPickService.DailyPick> pick = service.getDailyPick(seeker);

    assertTrue(pick.isPresent());
    assertEquals(LocalDate.now(config.userTimeZone()), pick.get().date());
  }

  @Test
  void hasViewedDailyPick_returnsFalse_whenNotViewed() {
    User seeker = createActiveUser("Alice", 25);
    userStorage.save(seeker);

    assertFalse(service.hasViewedDailyPick(seeker.getId()));
  }

  @Test
  void hasViewedDailyPick_returnsTrue_afterMarking() {
    User seeker = createActiveUser("Alice", 25);
    userStorage.save(seeker);

    service.markDailyPickViewed(seeker.getId());

    assertTrue(service.hasViewedDailyPick(seeker.getId()));
  }

  @Test
  void markDailyPickViewed_storesCorrectDate() {
    User seeker = createActiveUser("Alice", 25);
    userStorage.save(seeker);
    LocalDate today = LocalDate.now(config.userTimeZone());

    service.markDailyPickViewed(seeker.getId());

    assertTrue(dailyPickStorage.hasViewed(seeker.getId(), today));
  }

  // Helper methods

  private User createActiveUser(String name, int age) {
    User user = new User(UUID.randomUUID(), name);
    user.setBio("Test bio for " + name);
    user.setBirthDate(LocalDate.now().minusYears(age));
    user.setGender(User.Gender.MALE);
    user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
    user.setLocation(40.7128, -74.0060); // NYC
    user.addPhotoUrl("http://example.com/" + name + ".jpg");
    user.activate();
    return user;
  }

  // In-memory test storage implementations

  private static class InMemoryUserStorage implements UserStorage {
    private final List<User> users = new ArrayList<>();

    @Override
    public void save(User user) {
      users.removeIf(u -> u.getId().equals(user.getId()));
      users.add(user);
    }

    @Override
    public User get(UUID id) {
      return users.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public List<User> findAll() {
      return new ArrayList<>(users);
    }

    @Override
    public List<User> findActive() {
      return users.stream().filter(u -> u.getState() == User.State.ACTIVE).toList();
    }
  }

  private static class InMemoryLikeStorage implements LikeStorage {
    private final List<Like> likes = new ArrayList<>();

    @Override
    public void save(Like like) {
      likes.add(like);
    }

    @Override
    public boolean exists(UUID whoLikes, UUID whoGotLiked) {
      return likes.stream()
          .anyMatch(l -> l.whoLikes().equals(whoLikes) && l.whoGotLiked().equals(whoGotLiked));
    }

    @Override
    public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
      return likes.stream()
          .filter(l -> l.whoLikes().equals(fromUserId) && l.whoGotLiked().equals(toUserId))
          .findFirst();
    }

    @Override
    public boolean mutualLikeExists(UUID a, UUID b) {
      return exists(a, b) && exists(b, a);
    }

    @Override
    public java.util.Set<UUID> getLikedOrPassedUserIds(UUID userId) {
      return likes.stream()
          .filter(l -> l.whoLikes().equals(userId))
          .map(Like::whoGotLiked)
          .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public java.util.Set<UUID> getUserIdsWhoLiked(UUID userId) {
      return likes.stream()
          .filter(l -> l.whoGotLiked().equals(userId))
          .filter(l -> l.direction() == Like.Direction.LIKE)
          .map(Like::whoLikes)
          .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public java.util.Map<UUID, java.time.Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
      return likes.stream()
          .filter(l -> l.whoGotLiked().equals(userId))
          .filter(l -> l.direction() == Like.Direction.LIKE)
          .collect(
              java.util.stream.Collectors.toMap(
                  Like::whoLikes, Like::createdAt, (existing, replacement) -> existing));
    }

    @Override
    public int countByDirection(UUID userId, Like.Direction direction) {
      return (int)
          likes.stream()
              .filter(l -> l.whoLikes().equals(userId))
              .filter(l -> l.direction() == direction)
              .count();
    }

    @Override
    public int countReceivedByDirection(UUID userId, Like.Direction direction) {
      return (int)
          likes.stream()
              .filter(l -> l.whoGotLiked().equals(userId))
              .filter(l -> l.direction() == direction)
              .count();
    }

    @Override
    public int countMutualLikes(UUID userId) {
      return (int)
          likes.stream()
              .filter(l -> l.whoLikes().equals(userId))
              .filter(l -> l.direction() == Like.Direction.LIKE)
              .filter(l -> exists(l.whoGotLiked(), userId))
              .count();
    }

    @Override
    public void delete(UUID likeId) {
      likes.removeIf(l -> l.id().equals(likeId));
    }

    @Override
    public int countLikesToday(UUID userId, java.time.Instant startOfDay) {
      return (int)
          likes.stream()
              .filter(l -> l.whoLikes().equals(userId))
              .filter(l -> l.direction() == Like.Direction.LIKE)
              .filter(l -> l.createdAt().isAfter(startOfDay))
              .count();
    }

    @Override
    public int countPassesToday(UUID userId, java.time.Instant startOfDay) {
      return (int)
          likes.stream()
              .filter(l -> l.whoLikes().equals(userId))
              .filter(l -> l.direction() == Like.Direction.PASS)
              .filter(l -> l.createdAt().isAfter(startOfDay))
              .count();
    }
  }

  private static class InMemoryBlockStorage implements BlockStorage {
    private final List<Block> blocks = new ArrayList<>();

    @Override
    public void save(Block block) {
      blocks.add(block);
    }

    @Override
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
      return blocks.stream()
          .anyMatch(b -> b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId));
    }

    @Override
    public java.util.Set<UUID> getBlockedUserIds(UUID blockerId) {
      return blocks.stream()
          .filter(b -> b.blockerId().equals(blockerId))
          .map(Block::blockedId)
          .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public int countBlocksGiven(UUID blockerId) {
      return (int) blocks.stream().filter(b -> b.blockerId().equals(blockerId)).count();
    }

    @Override
    public int countBlocksReceived(UUID blockedId) {
      return (int) blocks.stream().filter(b -> b.blockedId().equals(blockedId)).count();
    }
  }

  private static class InMemoryDailyPickStorage implements DailyPickStorage {
    private final List<ViewRecord> views = new ArrayList<>();

    private record ViewRecord(UUID userId, LocalDate date) {}

    @Override
    public void markViewed(UUID userId, LocalDate date) {
      views.removeIf(v -> v.userId.equals(userId) && v.date.equals(date));
      views.add(new ViewRecord(userId, date));
    }

    @Override
    public boolean hasViewed(UUID userId, LocalDate date) {
      return views.stream().anyMatch(v -> v.userId.equals(userId) && v.date.equals(date));
    }

    @Override
    public int cleanup(LocalDate before) {
      int sizeBefore = views.size();
      views.removeIf(v -> v.date.isBefore(before));
      return sizeBefore - views.size();
    }
  }
}
