package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Block;
import datingapp.core.Conversation;
import datingapp.core.Interest;
import datingapp.core.Like;
import datingapp.core.Match;
import datingapp.core.Message;
import datingapp.core.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration tests for H2 storage implementations. These tests verify data survives round-trip to
 * database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class H2StorageIntegrationTest {

  private static DatabaseManager dbManager;
  private static H2UserStorage userStorage;
  private static H2LikeStorage likeStorage;
  private static H2MatchStorage matchStorage;
  private static H2BlockStorage blockStorage;
  private static H2ConversationStorage conversationStorage;
  private static H2MessageStorage messageStorage;

  @BeforeAll
  static void setUpOnce() {

    DatabaseManager.setJdbcUrl("jdbc:h2:./data/dating_test");
    DatabaseManager.resetInstance();
    dbManager = DatabaseManager.getInstance();

    // Clean database by dropping all tables to ensure fresh schema
    try (var conn = dbManager.getConnection();
        var stmt = conn.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    } catch (Exception e) {
      // Ignore errors if tables don't exist yet
    }

    userStorage = new H2UserStorage(dbManager);
    likeStorage = new H2LikeStorage(dbManager);
    matchStorage = new H2MatchStorage(dbManager);
    blockStorage = new H2BlockStorage(dbManager);
    conversationStorage = new H2ConversationStorage(dbManager);
    messageStorage = new H2MessageStorage(dbManager);
  }

  @org.junit.jupiter.api.AfterAll
  static void tearDown() {

    if (dbManager != null) {
      dbManager.shutdown();
    }
  }

  @Nested
  @DisplayName("H2UserStorage round-trip")
  class UserStorageTests {

    @Test
    @DisplayName("User survives round-trip to database")
    void userRoundTrip() {
      User original = createCompleteUser("RoundTrip_" + UUID.randomUUID());
      userStorage.save(original);

      User loaded = userStorage.get(original.getId());

      assertNotNull(loaded, "User should be loaded from database");
      assertEquals(original.getId(), loaded.getId());
      assertEquals(original.getName(), loaded.getName());
      assertEquals(original.getBio(), loaded.getBio());
      assertEquals(original.getBirthDate(), loaded.getBirthDate());
      assertEquals(original.getGender(), loaded.getGender());
      assertEquals(original.getInterestedIn(), loaded.getInterestedIn());
      assertEquals(original.getMaxDistanceKm(), loaded.getMaxDistanceKm());
      assertEquals(original.getMinAge(), loaded.getMinAge());
      assertEquals(original.getMaxAge(), loaded.getMaxAge());
      assertEquals(original.getState(), loaded.getState());
      assertEquals(original.getInterests(), loaded.getInterests());
    }

    @Test
    @DisplayName("Interests survive round-trip to database")
    void interestsRoundTrip() {
      User user = createCompleteUser("InterestsTest_" + UUID.randomUUID());
      Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);
      user.setInterests(interests);

      userStorage.save(user);
      User loaded = userStorage.get(user.getId());

      assertNotNull(loaded.getInterests());
      assertEquals(3, loaded.getInterests().size());
      assertTrue(loaded.getInterests().contains(Interest.HIKING));
      assertTrue(loaded.getInterests().contains(Interest.COFFEE));
      assertTrue(loaded.getInterests().contains(Interest.TRAVEL));
    }

    @Test
    @DisplayName("Photo URLs with special characters survive round-trip")
    void photoUrlsWithSpecialChars() {
      User user = new User(UUID.randomUUID(), "PhotoTest_" + UUID.randomUUID());
      user.setBio("Bio");
      user.setBirthDate(LocalDate.of(1990, 1, 1));
      user.setGender(User.Gender.MALE);
      user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
      user.setLocation(32.0, 34.0);

      // URL with comma - this might break!
      String urlWithComma = "https://example.com/photo?a=1,b=2";
      user.addPhotoUrl(urlWithComma);

      userStorage.save(user);
      User loaded = userStorage.get(user.getId());

      // BUG: If comma-separated storage is used, this will fail
      assertEquals(
          1, loaded.getPhotoUrls().size(), "Should have exactly 1 photo URL, not split by comma");
      assertEquals(urlWithComma, loaded.getPhotoUrls().get(0), "URL should be preserved exactly");
    }

    @Test
    @DisplayName("User update preserves all fields")
    void userUpdatePreservesAllFields() {
      User user = createCompleteUser("Update_" + UUID.randomUUID());
      userStorage.save(user);

      // Modify user
      user.setBio("Updated bio");
      user.setMaxDistanceKm(100);
      userStorage.save(user);

      User loaded = userStorage.get(user.getId());
      assertEquals("Updated bio", loaded.getBio());
      assertEquals(100, loaded.getMaxDistanceKm());
      // Check other fields weren't lost
      assertNotNull(loaded.getBirthDate());
      assertNotNull(loaded.getGender());
    }

    @Test
    @DisplayName("Empty interestedIn set survives round-trip")
    void emptyInterestedInSurvivesRoundTrip() {
      User user = new User(UUID.randomUUID(), "EmptyInterest_" + UUID.randomUUID());
      user.setBio("Bio");
      user.setBirthDate(LocalDate.of(1990, 1, 1));
      user.setGender(User.Gender.MALE);
      user.setInterestedIn(EnumSet.noneOf(User.Gender.class)); // Empty
      user.setLocation(32.0, 34.0);

      userStorage.save(user);
      User loaded = userStorage.get(user.getId());

      assertNotNull(loaded.getInterestedIn());
      assertTrue(
          loaded.getInterestedIn().isEmpty(), "Empty interestedIn should survive round-trip");
    }

    @Test
    @DisplayName("findActive returns only active users")
    void findActiveReturnsOnlyActive() {
      String suffix = UUID.randomUUID().toString().substring(0, 8);

      User active = createCompleteUser("Active_" + suffix);
      active.activate();
      userStorage.save(active);

      User incomplete = new User(UUID.randomUUID(), "Incomplete_" + suffix);
      userStorage.save(incomplete);

      List<User> activeUsers = userStorage.findActive();

      assertTrue(activeUsers.stream().anyMatch(u -> u.getId().equals(active.getId())));
      assertFalse(activeUsers.stream().anyMatch(u -> u.getId().equals(incomplete.getId())));
    }
  }

  @Nested
  @DisplayName("H2MatchStorage round-trip")
  class MatchStorageTests {

    @Test
    @DisplayName("Match survives round-trip with state")
    void matchRoundTripWithState() {
      // Create users first to satisfy FK constraints
      User userA = createCompleteUser("MatchUserA_" + UUID.randomUUID());
      User userB = createCompleteUser("MatchUserB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Match original = Match.create(userA.getId(), userB.getId());
      matchStorage.save(original);
      var loaded = matchStorage.get(original.getId());

      assertTrue(loaded.isPresent());
      assertEquals(Match.State.ACTIVE, loaded.get().getState());
    }

    @Test
    @DisplayName("Match state update persists")
    void matchStateUpdatePersists() {
      User userA = createCompleteUser("UpdateA_" + UUID.randomUUID());
      User userB = createCompleteUser("UpdateB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Match match = Match.create(userA.getId(), userB.getId());
      matchStorage.save(match);

      match.unmatch(userA.getId());
      matchStorage.update(match);

      var loaded = matchStorage.get(match.getId());
      assertTrue(loaded.isPresent());
      assertEquals(Match.State.UNMATCHED, loaded.get().getState());
      assertNotNull(loaded.get().getEndedAt());
      assertEquals(userA.getId(), loaded.get().getEndedBy());
    }

    @Test
    @DisplayName("getActiveMatchesFor excludes unmatched")
    void getActiveMatchesForExcludesUnmatched() {
      User seeker = createCompleteUser("Seeker_" + UUID.randomUUID());
      User partner1 = createCompleteUser("Partner1_" + UUID.randomUUID());
      User partner2 = createCompleteUser("Partner2_" + UUID.randomUUID());
      userStorage.save(seeker);
      userStorage.save(partner1);
      userStorage.save(partner2);

      Match active = Match.create(seeker.getId(), partner1.getId());
      Match unmatched = Match.create(seeker.getId(), partner2.getId());
      unmatched.unmatch(seeker.getId());

      matchStorage.save(active);
      matchStorage.save(unmatched);

      List<Match> activeMatches = matchStorage.getActiveMatchesFor(seeker.getId());

      assertTrue(activeMatches.stream().anyMatch(m -> m.getId().equals(active.getId())));
      assertFalse(activeMatches.stream().anyMatch(m -> m.getId().equals(unmatched.getId())));
    }
  }

  @Nested
  @DisplayName("H2BlockStorage round-trip")
  class BlockStorageTests {

    @Test
    @DisplayName("Block is bidirectional in isBlocked")
    void blockIsBidirectional() {
      UUID blocker = UUID.randomUUID();
      UUID blocked = UUID.randomUUID();

      Block block = Block.create(blocker, blocked);
      blockStorage.save(block);

      assertTrue(blockStorage.isBlocked(blocker, blocked), "A→B should be blocked");
      assertTrue(blockStorage.isBlocked(blocked, blocker), "B→A should also show blocked");
    }

    @Test
    @DisplayName("getBlockedUserIds returns both directions")
    void getBlockedUserIdsReturnsBothDirections() {
      UUID user = UUID.randomUUID();
      UUID blockedByUser = UUID.randomUUID();
      UUID userBlockedBy = UUID.randomUUID();

      blockStorage.save(Block.create(user, blockedByUser)); // user blocked someone
      blockStorage.save(Block.create(userBlockedBy, user)); // someone blocked user

      var blockedIds = blockStorage.getBlockedUserIds(user);

      assertTrue(blockedIds.contains(blockedByUser), "Should include users blocked by this user");
      assertTrue(blockedIds.contains(userBlockedBy), "Should include users who blocked this user");
    }
  }

  @Nested
  @DisplayName("H2LikeStorage mutual like")
  class LikeStorageTests {

    @Test
    @DisplayName("Mutual LIKE detection works")
    void mutualLikeDetection() {
      // Create users first to satisfy FK constraints
      User userA = createCompleteUser("LikeA_" + UUID.randomUUID());
      User userB = createCompleteUser("LikeB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      likeStorage.save(Like.create(userA.getId(), userB.getId(), Like.Direction.LIKE));
      assertFalse(likeStorage.mutualLikeExists(userA.getId(), userB.getId()), "Not mutual yet");

      likeStorage.save(Like.create(userB.getId(), userA.getId(), Like.Direction.LIKE));
      assertTrue(
          likeStorage.mutualLikeExists(userA.getId(), userB.getId()), "Should be mutual now");
      assertTrue(
          likeStorage.mutualLikeExists(userB.getId(), userA.getId()), "Order shouldn't matter");
    }

    @Test
    @DisplayName("PASS breaks mutual like")
    void passBreaksMutualLike() {
      User userA = createCompleteUser("PassA_" + UUID.randomUUID());
      User userB = createCompleteUser("PassB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      likeStorage.save(Like.create(userA.getId(), userB.getId(), Like.Direction.LIKE));
      likeStorage.save(Like.create(userB.getId(), userA.getId(), Like.Direction.PASS));

      assertFalse(
          likeStorage.mutualLikeExists(userA.getId(), userB.getId()), "PASS should break mutual");
    }
  }

  @Nested
  @DisplayName("H2ConversationStorage round-trip")
  class ConversationStorageTests {

    @Test
    @DisplayName("Conversation survives save and get")
    void conversationRoundTrip() {
      User userA = createCompleteUser("ConvA_" + UUID.randomUUID());
      User userB = createCompleteUser("ConvB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      Optional<Conversation> loaded = conversationStorage.get(conversation.getId());

      assertTrue(loaded.isPresent());
      assertEquals(conversation.getId(), loaded.get().getId());
      assertEquals(conversation.getUserA(), loaded.get().getUserA());
      assertEquals(conversation.getUserB(), loaded.get().getUserB());
    }

    @Test
    @DisplayName("getByUsers finds existing conversation")
    void getByUsersFindsConversation() {
      User userA = createCompleteUser("ByUsersA_" + UUID.randomUUID());
      User userB = createCompleteUser("ByUsersB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      // Order should not matter
      Optional<Conversation> found1 = conversationStorage.getByUsers(userA.getId(), userB.getId());
      Optional<Conversation> found2 = conversationStorage.getByUsers(userB.getId(), userA.getId());

      assertTrue(found1.isPresent());
      assertTrue(found2.isPresent());
      assertEquals(conversation.getId(), found1.get().getId());
      assertEquals(conversation.getId(), found2.get().getId());
    }

    @Test
    @DisplayName("getConversationsFor returns sorted by lastMessageAt")
    void getConversationsForSortedByLastMessage() {
      User user = createCompleteUser("SortUser_" + UUID.randomUUID());
      User partner1 = createCompleteUser("SortPartner1_" + UUID.randomUUID());
      User partner2 = createCompleteUser("SortPartner2_" + UUID.randomUUID());
      userStorage.save(user);
      userStorage.save(partner1);
      userStorage.save(partner2);

      Conversation conv1 = Conversation.create(user.getId(), partner1.getId());
      Conversation conv2 = Conversation.create(user.getId(), partner2.getId());
      conversationStorage.save(conv1);
      conversationStorage.save(conv2);

      // Update conv1 to have more recent lastMessageAt
      conversationStorage.updateLastMessageAt(conv1.getId(), Instant.now());

      List<Conversation> conversations = conversationStorage.getConversationsFor(user.getId());

      assertTrue(conversations.size() >= 2);
      // Most recent first
      int index1 = -1, index2 = -1;
      for (int i = 0; i < conversations.size(); i++) {
        if (conversations.get(i).getId().equals(conv1.getId())) index1 = i;
        if (conversations.get(i).getId().equals(conv2.getId())) index2 = i;
      }
      assertTrue(index1 < index2, "conv1 should come before conv2 (more recent)");
    }

    @Test
    @DisplayName("updateReadTimestamp updates correctly")
    void updateReadTimestampWorks() {
      User userA = createCompleteUser("ReadA_" + UUID.randomUUID());
      User userB = createCompleteUser("ReadB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      Instant now = Instant.now();
      conversationStorage.updateReadTimestamp(conversation.getId(), userA.getId(), now);

      Optional<Conversation> loaded = conversationStorage.get(conversation.getId());
      assertTrue(loaded.isPresent());
      // Use getLastReadAt(userId) which handles the userA/userB mapping correctly
      assertNotNull(loaded.get().getLastReadAt(userA.getId()));
    }

    @Test
    @DisplayName("delete removes conversation")
    void deleteRemovesConversation() {
      User userA = createCompleteUser("DelA_" + UUID.randomUUID());
      User userB = createCompleteUser("DelB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      conversationStorage.delete(conversation.getId());

      Optional<Conversation> loaded = conversationStorage.get(conversation.getId());
      assertFalse(loaded.isPresent());
    }
  }

  @Nested
  @DisplayName("H2MessageStorage round-trip")
  class MessageStorageTests {

    @Test
    @DisplayName("Message survives save and get")
    void messageRoundTrip() {
      User userA = createCompleteUser("MsgA_" + UUID.randomUUID());
      User userB = createCompleteUser("MsgB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      Message message = Message.create(conversation.getId(), userA.getId(), "Hello!");
      messageStorage.save(message);

      List<Message> messages = messageStorage.getMessages(conversation.getId(), 10, 0);

      assertEquals(1, messages.size());
      assertEquals(message.id(), messages.get(0).id());
      assertEquals("Hello!", messages.get(0).content());
      assertEquals(userA.getId(), messages.get(0).senderId());
    }

    @Test
    @DisplayName("Pagination with limit and offset works")
    void paginationWorks() {
      User userA = createCompleteUser("PageA_" + UUID.randomUUID());
      User userB = createCompleteUser("PageB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      // Send 5 messages
      for (int i = 1; i <= 5; i++) {
        messageStorage.save(Message.create(conversation.getId(), userA.getId(), "Message " + i));
      }

      List<Message> firstPage = messageStorage.getMessages(conversation.getId(), 2, 0);
      List<Message> secondPage = messageStorage.getMessages(conversation.getId(), 2, 2);

      assertEquals(2, firstPage.size());
      assertEquals(2, secondPage.size());
      // Ensure no overlap
      assertFalse(firstPage.get(0).id().equals(secondPage.get(0).id()));
    }

    @Test
    @DisplayName("countMessages returns correct count")
    void countMessagesWorks() {
      User userA = createCompleteUser("CountA_" + UUID.randomUUID());
      User userB = createCompleteUser("CountB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      assertEquals(0, messageStorage.countMessages(conversation.getId()));

      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "One"));
      messageStorage.save(Message.create(conversation.getId(), userB.getId(), "Two"));
      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "Three"));

      assertEquals(3, messageStorage.countMessages(conversation.getId()));
    }

    @Test
    @DisplayName("countMessagesAfter counts only messages after timestamp")
    void countMessagesAfterWorks() {
      User userA = createCompleteUser("AfterA_" + UUID.randomUUID());
      User userB = createCompleteUser("AfterB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      // Take marker well in the past to ensure all messages are after it
      Instant marker = Instant.now().minusSeconds(1);
      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "Message 1"));
      messageStorage.save(Message.create(conversation.getId(), userB.getId(), "Message 2"));

      // Both messages should be after the marker
      int afterCount = messageStorage.countMessagesAfter(conversation.getId(), marker);
      assertEquals(2, afterCount, "Both messages should be after the marker");
    }

    @Test
    @DisplayName("countMessagesNotFromSender excludes sender messages")
    void countMessagesNotFromSenderWorks() {
      User userA = createCompleteUser("NotFromA_" + UUID.randomUUID());
      User userB = createCompleteUser("NotFromB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "From A"));
      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "From A again"));
      messageStorage.save(Message.create(conversation.getId(), userB.getId(), "From B"));

      // Count messages NOT from userA (should be 1 - from userB)
      int count = messageStorage.countMessagesNotFromSender(conversation.getId(), userA.getId());
      assertEquals(1, count);

      // Count messages NOT from userB (should be 2 - both from userA)
      count = messageStorage.countMessagesNotFromSender(conversation.getId(), userB.getId());
      assertEquals(2, count);
    }

    @Test
    @DisplayName("getLatestMessage returns most recent")
    void getLatestMessageWorks() {
      User userA = createCompleteUser("LatestA_" + UUID.randomUUID());
      User userB = createCompleteUser("LatestB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "First"));
      messageStorage.save(Message.create(conversation.getId(), userB.getId(), "Second"));
      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "Third"));

      Optional<Message> latest = messageStorage.getLatestMessage(conversation.getId());

      assertTrue(latest.isPresent());
      assertEquals("Third", latest.get().content());
    }

    @Test
    @DisplayName("deleteByConversation removes all messages")
    void deleteByConversationWorks() {
      User userA = createCompleteUser("DelMsgA_" + UUID.randomUUID());
      User userB = createCompleteUser("DelMsgB_" + UUID.randomUUID());
      userStorage.save(userA);
      userStorage.save(userB);

      Conversation conversation = Conversation.create(userA.getId(), userB.getId());
      conversationStorage.save(conversation);

      messageStorage.save(Message.create(conversation.getId(), userA.getId(), "One"));
      messageStorage.save(Message.create(conversation.getId(), userB.getId(), "Two"));

      assertEquals(2, messageStorage.countMessages(conversation.getId()));

      messageStorage.deleteByConversation(conversation.getId());

      assertEquals(0, messageStorage.countMessages(conversation.getId()));
    }
  }

  private User createCompleteUser(String name) {
    User user = new User(UUID.randomUUID(), name);
    user.setBio("Test bio for " + name);
    user.setBirthDate(LocalDate.of(1990, 1, 15));
    user.setGender(User.Gender.MALE);
    user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
    user.setLocation(32.0, 34.0);
    user.setMaxDistanceKm(50);
    user.setAgeRange(18, 50);
    user.addPhotoUrl("https://example.com/photo.jpg");
    return user;
  }
}
