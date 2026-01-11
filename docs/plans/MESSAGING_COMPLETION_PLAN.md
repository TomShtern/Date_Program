# Messaging System - Completion Plan

**Document Version:** 1.0
**Created:** 2026-01-11
**Status:** Ready for Implementation
**Estimated Effort:** 6-8 hours

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Gap Analysis](#2-gap-analysis)
3. [Issues Requiring Fixes](#3-issues-requiring-fixes)
4. [Missing Test Coverage](#4-missing-test-coverage)
5. [Implementation Plan](#5-implementation-plan)
6. [File Change Summary](#6-file-change-summary)
7. [Verification Checklist](#7-verification-checklist)

---

## 1. Executive Summary

The messaging system is 95% complete and functional. This document captures all remaining gaps, issues, and missing elements discovered during the implementation review. The plan provides a systematic approach to achieve 100% completion with full test coverage and architectural compliance.

### Current State

| Component | Status | Completion |
|-----------|--------|------------|
| Core Domain Models | Complete | 100% |
| Storage Interfaces | Complete | 100% |
| H2 Storage Implementation | Needs Fix | 95% |
| MessagingService | Needs Fix | 90% |
| CLI Handler | Complete | 100% |
| Unit Tests | Partial | 75% |
| Integration Tests | Missing | 0% |

### Target State

All components at 100% with full test coverage, architectural compliance, and feature parity with design document.

---

## 2. Gap Analysis

### 2.1 Missing Integration Tests

> [!CAUTION]
> The storage layer has ZERO database-level test coverage. This is a significant gap.

| Missing File | Purpose | Priority |
|--------------|---------|----------|
| `H2ConversationStorageTest.java` | Test CRUD operations, cascade delete, query correctness | **CRITICAL** |
| `H2MessageStorageTest.java` | Test CRUD, pagination, counting methods | **CRITICAL** |

**Impact:** Database schema issues, query bugs, and data integrity problems will not be caught until runtime.

---

### 2.2 Missing Design Document Features

The design document (`docs/MESSAGING_SYSTEM_DESIGN.md`) specifies features that were not implemented:

| Feature | Design Doc Reference | Current State | Priority |
|---------|---------------------|---------------|----------|
| Update `lastActiveAt` on message send | Section 5.1, Line 312 | Not implemented | MEDIUM |
| Achievement trigger (first message) | Section 5.1, Line 313 | Not implemented | LOW |
| BlockStorage defensive check | Section 5.1, Line 310 | Relies on Match state only | LOW |

#### 2.2.1 Missing: `lastActiveAt` Update

**Design Specification:**
> User Activity: Update `lastActiveAt` on message send

**Current Behavior:** User's `lastActiveAt` is not updated when they send a message.

**Expected Behavior:** Each successful message send should update the sender's `lastActiveAt` timestamp.

**Location:** `MessagingService.sendMessage()` method

---

#### 2.2.2 Missing: Achievement Integration

**Design Specification:**
> Achievement Trigger: Potential: First message, N messages sent

**Current Behavior:** No achievements are triggered by messaging activity.

**Potential Achievements:**
- `FIRST_MESSAGE` - Sent first message in any conversation
- `CHATTERBOX` - Sent 100 messages total
- `CONVERSATION_STARTER` - Started 5 conversations

**Note:** This is marked as "Potential" in the design doc, so it's optional for Phase 1.

---

### 2.3 Missing Storage Method

The current implementation has an inefficient workaround in `MessagingService.java:227-231`:

```java
/** Counts messages not sent by the given user (for initial unread count). */
private int countMessagesNotFromUser(String conversationId, UUID userId) {
  // Get all messages and count those not from userId
  // This is inefficient but simple - could add a storage method for this
  List<Message> messages = messageStorage.getMessages(conversationId, 1000, 0);
  return (int) messages.stream().filter(m -> !m.senderId().equals(userId)).count();
}
```

**Problem:** Loads ALL messages (up to 1000) just to count them.

**Solution:** Add dedicated storage method:
```java
// In MessageStorage interface
int countMessagesNotFromSender(String conversationId, UUID senderId);
```

---

### 2.4 Missing Database Constraint

**Design Specification (Section 3.3):**
```sql
conversation_id VARCHAR(100) NOT NULL
    REFERENCES conversations(id) ON DELETE CASCADE
```

**Current Implementation (`H2MessageStorage.java:28-37`):**
```java
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL,  // No FK!
    sender_id UUID NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
)
```

**Problem:** Messages will NOT cascade delete when a conversation is deleted.

**Impact:** Orphaned messages in database after conversation deletion.

---

## 3. Issues Requiring Fixes

### 3.1 CRITICAL: Missing Constructor Validation

**File:** `MessagingService.java`
**Location:** Lines 20-29
**Violation:** AGENTS.md Section "Coding Standards" - "Validation in Constructors"

**Current Code:**
```java
public MessagingService(
    ConversationStorage conversationStorage,
    MessageStorage messageStorage,
    MatchStorage matchStorage,
    UserStorage userStorage) {
  this.conversationStorage = conversationStorage;  // No null check!
  this.messageStorage = messageStorage;            // No null check!
  this.matchStorage = matchStorage;                // No null check!
  this.userStorage = userStorage;                  // No null check!
}
```

**Required Code:**
```java
public MessagingService(
    ConversationStorage conversationStorage,
    MessageStorage messageStorage,
    MatchStorage matchStorage,
    UserStorage userStorage) {
  this.conversationStorage = Objects.requireNonNull(conversationStorage,
      "conversationStorage cannot be null");
  this.messageStorage = Objects.requireNonNull(messageStorage,
      "messageStorage cannot be null");
  this.matchStorage = Objects.requireNonNull(matchStorage,
      "matchStorage cannot be null");
  this.userStorage = Objects.requireNonNull(userStorage,
      "userStorage cannot be null");
}
```

---

### 3.2 HIGH: Missing Foreign Key Constraint

**File:** `H2MessageStorage.java`
**Location:** Lines 28-37

**Current Schema:**
```sql
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL,
    sender_id UUID NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
)
```

**Required Schema:**
```sql
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL
        REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
)
```

> [!WARNING]
> This change requires careful handling for existing databases. The FK can only be added if all existing `conversation_id` values exist in the `conversations` table.

---

### 3.3 MEDIUM: Inefficient Unread Count Calculation

**File:** `MessagingService.java`
**Location:** Lines 227-231

**Problem:** When a user has never read a conversation, the code loads up to 1000 messages to count unread ones.

**Solution:** Add `countMessagesNotFromSender()` to `MessageStorage` interface and implementations.

---

## 4. Missing Test Coverage

### 4.1 Missing Unit Tests in `MessagingServiceTest.java`

| Method | Test Scenario | Priority |
|--------|---------------|----------|
| `markAsRead()` | Updates read timestamp correctly | HIGH |
| `markAsRead()` | Ignores non-participant user | MEDIUM |
| `getUnreadCount()` | Returns correct count after messages | HIGH |
| `getUnreadCount()` | Returns 0 for empty conversation | MEDIUM |
| `getUnreadCount()` | Returns 0 after marking as read | HIGH |
| `getTotalUnreadCount()` | Aggregates across conversations | MEDIUM |
| `getConversations()` | Returns sorted by most recent | HIGH |
| `getConversations()` | Includes unread counts | MEDIUM |
| `getConversations()` | Skips deleted users | LOW |
| `getOrCreateConversation()` | Creates if not exists | MEDIUM |
| `getOrCreateConversation()` | Returns existing if exists | MEDIUM |

---

### 4.2 Missing Integration Tests

#### 4.2.1 `H2ConversationStorageTest.java`

| Test Case | Description |
|-----------|-------------|
| `save_persistsConversation` | Conversation survives save/get cycle |
| `get_returnsEmptyForNonExistent` | Returns Optional.empty() for unknown ID |
| `getByUsers_worksRegardlessOfOrder` | Both (a,b) and (b,a) find same conversation |
| `getConversationsFor_returnsSortedByLastMessage` | Most recent first |
| `getConversationsFor_returnsEmptyForNewUser` | Empty list for user with no conversations |
| `updateLastMessageAt_updatesTimestamp` | Timestamp is persisted |
| `updateReadTimestamp_updatesCorrectUser` | Only target user's timestamp updated |
| `delete_removesConversation` | Conversation no longer retrievable |
| `delete_cascadesToMessages` | Messages also deleted (requires FK) |

#### 4.2.2 `H2MessageStorageTest.java`

| Test Case | Description |
|-----------|-------------|
| `save_persistsMessage` | Message survives save/get cycle |
| `getMessages_returnsInChronologicalOrder` | Oldest first |
| `getMessages_respectsLimit` | Only returns requested count |
| `getMessages_respectsOffset` | Skips correct number |
| `getMessages_returnsEmptyForNoMessages` | Empty list, not null |
| `getLatestMessage_returnsNewest` | Most recent by createdAt |
| `getLatestMessage_returnsEmptyForNoMessages` | Optional.empty() |
| `countMessages_returnsCorrectCount` | Matches actual count |
| `countMessagesAfter_countsCorrectly` | Only counts after timestamp |
| `deleteByConversation_removesAllMessages` | All messages for conversation deleted |

---

## 5. Implementation Plan

### Phase 1: Critical Fixes (1 hour)

#### Task 1.1: Fix MessagingService Constructor Validation
- **File:** `src/main/java/datingapp/core/MessagingService.java`
- **Action:** Add `Objects.requireNonNull()` for all four dependencies
- **Verification:** Compile succeeds, existing tests pass

#### Task 1.2: Fix H2MessageStorage Foreign Key
- **File:** `src/main/java/datingapp/storage/H2MessageStorage.java`
- **Action:** Add FK constraint with CASCADE DELETE
- **Verification:** Schema creation succeeds, messages cascade delete

---

### Phase 2: Storage Interface Enhancement (1 hour)

#### Task 2.1: Add Efficient Unread Count Method

**File:** `src/main/java/datingapp/core/MessageStorage.java`

Add new method:
```java
/**
 * Counts messages in a conversation not sent by the specified user.
 * Used for initial unread count when user has never read the conversation.
 *
 * @param conversationId The conversation to count messages for
 * @param senderId The user whose messages to exclude
 * @return Count of messages from other participants
 */
int countMessagesNotFromSender(String conversationId, UUID senderId);
```

#### Task 2.2: Implement in H2MessageStorage

**File:** `src/main/java/datingapp/storage/H2MessageStorage.java`

Add implementation:
```java
@Override
public int countMessagesNotFromSender(String conversationId, UUID senderId) {
  String sql = """
      SELECT COUNT(*) FROM messages
      WHERE conversation_id = ?
      AND sender_id != ?
      """;

  try (Connection conn = dbManager.getConnection();
       PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, conversationId);
    stmt.setObject(2, senderId);
    ResultSet rs = stmt.executeQuery();
    return rs.next() ? rs.getInt(1) : 0;
  } catch (SQLException e) {
    throw new StorageException("Failed to count messages: " + conversationId, e);
  }
}
```

#### Task 2.3: Update MessagingService to Use New Method

**File:** `src/main/java/datingapp/core/MessagingService.java`

Replace:
```java
private int countMessagesNotFromUser(String conversationId, UUID userId) {
  List<Message> messages = messageStorage.getMessages(conversationId, 1000, 0);
  return (int) messages.stream().filter(m -> !m.senderId().equals(userId)).count();
}
```

With:
```java
private int countMessagesNotFromUser(String conversationId, UUID userId) {
  return messageStorage.countMessagesNotFromSender(conversationId, userId);
}
```

---

### Phase 3: Missing Feature - lastActiveAt Update (30 min)

#### Task 3.1: Update MessagingService.sendMessage()

**File:** `src/main/java/datingapp/core/MessagingService.java`

Add after line 86 (after updating lastMessageAt):
```java
// Update sender's lastActiveAt
sender.touch();  // If User has touch() method
userStorage.save(sender);
```

**Note:** Verify `User.touch()` exists or use equivalent timestamp update.

---

### Phase 4: Integration Tests (2-3 hours)

#### Task 4.1: Create H2ConversationStorageTest.java

**File:** `src/test/java/datingapp/storage/H2ConversationStorageTest.java`

```java
package datingapp.storage;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.Conversation;
import datingapp.core.ConversationStorage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("H2ConversationStorage Integration Tests")
class H2ConversationStorageTest {

  private static DatabaseManager dbManager;
  private static ConversationStorage storage;

  @BeforeAll
  static void setUpOnce() {
    DatabaseManager.setJdbcUrl("jdbc:h2:mem:test_conversation_" + UUID.randomUUID());
    DatabaseManager.resetInstance();
    dbManager = DatabaseManager.getInstance();
    storage = new H2ConversationStorage(dbManager);
  }

  @AfterAll
  static void tearDown() {
    if (dbManager != null) {
      dbManager.shutdown();
    }
  }

  @Nested
  @DisplayName("save and get")
  class SaveAndGet {
    // Test implementations
  }

  @Nested
  @DisplayName("getConversationsFor")
  class GetConversationsFor {
    // Test implementations
  }

  @Nested
  @DisplayName("timestamp updates")
  class TimestampUpdates {
    // Test implementations
  }

  @Nested
  @DisplayName("delete")
  class Delete {
    // Test implementations
  }
}
```

#### Task 4.2: Create H2MessageStorageTest.java

**File:** `src/test/java/datingapp/storage/H2MessageStorageTest.java`

```java
package datingapp.storage;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.Conversation;
import datingapp.core.Message;
import datingapp.core.MessageStorage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("H2MessageStorage Integration Tests")
class H2MessageStorageTest {

  private static DatabaseManager dbManager;
  private static H2ConversationStorage conversationStorage;
  private static MessageStorage storage;

  @BeforeAll
  static void setUpOnce() {
    DatabaseManager.setJdbcUrl("jdbc:h2:mem:test_message_" + UUID.randomUUID());
    DatabaseManager.resetInstance();
    dbManager = DatabaseManager.getInstance();
    conversationStorage = new H2ConversationStorage(dbManager);
    storage = new H2MessageStorage(dbManager);
  }

  @AfterAll
  static void tearDown() {
    if (dbManager != null) {
      dbManager.shutdown();
    }
  }

  @Nested
  @DisplayName("save and getMessages")
  class SaveAndGet {
    // Test implementations
  }

  @Nested
  @DisplayName("pagination")
  class Pagination {
    // Test implementations
  }

  @Nested
  @DisplayName("counting methods")
  class Counting {
    // Test implementations
  }

  @Nested
  @DisplayName("deleteByConversation")
  class Delete {
    // Test implementations
  }
}
```

---

### Phase 5: Unit Test Completion (1-2 hours)

#### Task 5.1: Add Missing Tests to MessagingServiceTest.java

**File:** `src/test/java/datingapp/core/MessagingServiceTest.java`

Add new nested classes:

```java
@Nested
@DisplayName("markAsRead")
class MarkAsRead {

  @Test
  @DisplayName("should update read timestamp")
  void updateReadTimestamp() {
    // Setup match and conversation
    // Send message from userA to userB
    // Call markAsRead for userB
    // Verify timestamp updated
  }

  @Test
  @DisplayName("should ignore non-participant")
  void ignoreNonParticipant() {
    // Setup match and conversation between userA and userB
    // Call markAsRead for userC
    // Verify no error, no change
  }
}

@Nested
@DisplayName("getUnreadCount")
class GetUnreadCount {

  @Test
  @DisplayName("should return correct count")
  void returnCorrectCount() {
    // Send 3 messages from userA to userB
    // Verify unread count for userB is 3
  }

  @Test
  @DisplayName("should return 0 after marking as read")
  void returnZeroAfterRead() {
    // Send message, mark as read
    // Verify unread count is 0
  }

  @Test
  @DisplayName("should return 0 for empty conversation")
  void returnZeroForEmpty() {
    // Create conversation with no messages
    // Verify unread count is 0
  }
}

@Nested
@DisplayName("getTotalUnreadCount")
class GetTotalUnreadCount {

  @Test
  @DisplayName("should aggregate across conversations")
  void aggregateAcrossConversations() {
    // Create matches with userA-userB and userA-userC
    // Send messages to userA from both
    // Verify total unread is sum
  }
}

@Nested
@DisplayName("getConversations")
class GetConversations {

  @Test
  @DisplayName("should return sorted by most recent")
  void returnSortedByMostRecent() {
    // Create two conversations
    // Send older message in first, newer in second
    // Verify second comes first in list
  }

  @Test
  @DisplayName("should include unread counts")
  void includeUnreadCounts() {
    // Send messages
    // Verify ConversationPreview.unreadCount() is correct
  }
}
```

#### Task 5.2: Update In-Memory Mock for New Method

**File:** `src/test/java/datingapp/core/MessagingServiceTest.java`

Add to `InMemoryMessageStorage`:

```java
@Override
public int countMessagesNotFromSender(String conversationId, UUID senderId) {
  return (int) messagesByConvo.getOrDefault(conversationId, List.of()).stream()
      .filter(m -> !m.senderId().equals(senderId))
      .count();
}
```

---

### Phase 6: Optional Enhancements (1 hour)

#### Task 6.1: Achievement Integration (Optional)

**File:** `src/main/java/datingapp/core/MessagingService.java`

Consider adding achievement triggers:
- First message sent
- N messages sent milestone

**Note:** This requires adding messaging-related achievements to the `Achievement` enum and updating `AchievementService`.

#### Task 6.2: BlockStorage Defensive Check (Optional)

The current implementation relies solely on Match state. A defensive BlockStorage check would add:

```java
// In sendMessage(), after match check
if (blockStorage.existsBetween(senderId, recipientId)) {
  return SendResult.failure("Cannot message blocked user", ErrorCode.BLOCKED);
}
```

**Note:** This is redundant if Match state is always correct, but adds defense-in-depth.

---

## 6. File Change Summary

### Files to Modify

| File | Changes | Priority |
|------|---------|----------|
| `MessagingService.java` | Add null checks, use new storage method, add lastActiveAt update | CRITICAL |
| `H2MessageStorage.java` | Add FK constraint, implement `countMessagesNotFromSender()` | CRITICAL |
| `MessageStorage.java` | Add `countMessagesNotFromSender()` method | HIGH |
| `MessagingServiceTest.java` | Add missing test methods, update mock | HIGH |

### Files to Create

| File | Purpose | Priority |
|------|---------|----------|
| `H2ConversationStorageTest.java` | Integration tests for conversation storage | CRITICAL |
| `H2MessageStorageTest.java` | Integration tests for message storage | CRITICAL |

### Files Unchanged

| File | Reason |
|------|--------|
| `Message.java` | Complete |
| `Conversation.java` | Complete |
| `ConversationStorage.java` | Complete |
| `H2ConversationStorage.java` | Complete |
| `MessagingHandler.java` | Complete |
| `Main.java` | Complete |
| `ServiceRegistry.java` | Complete |
| `ServiceRegistryBuilder.java` | Complete |

---

## 7. Verification Checklist

### After Each Phase

- [ ] `mvn compile` succeeds
- [ ] `mvn test` all tests pass
- [ ] `mvn spotless:apply` applied

### Final Verification

- [ ] All 6 phases complete
- [ ] No compiler warnings
- [ ] Test count increased by 20+
- [ ] Integration tests cover H2 storage
- [ ] Design document features implemented
- [ ] `mvn clean verify package` succeeds

### Manual Testing

1. [ ] Create two users, complete profiles
2. [ ] Both like each other (match created)
3. [ ] Send messages back and forth
4. [ ] Verify unread counts update correctly
5. [ ] Unmatch - verify read-only mode
6. [ ] Block - verify messaging disabled
7. [ ] Verify conversation history preserved

---

## Appendix A: Execution Order

```
Phase 1 (Critical Fixes)
    ├── Task 1.1: MessagingService null checks
    └── Task 1.2: H2MessageStorage FK constraint

Phase 2 (Storage Enhancement)
    ├── Task 2.1: MessageStorage interface update
    ├── Task 2.2: H2MessageStorage implementation
    └── Task 2.3: MessagingService refactor

Phase 3 (Missing Feature)
    └── Task 3.1: lastActiveAt update

Phase 4 (Integration Tests)
    ├── Task 4.1: H2ConversationStorageTest
    └── Task 4.2: H2MessageStorageTest

Phase 5 (Unit Test Completion)
    ├── Task 5.1: MessagingServiceTest additions
    └── Task 5.2: Mock updates

Phase 6 (Optional)
    ├── Task 6.1: Achievement integration
    └── Task 6.2: BlockStorage check
```

---

## Appendix B: Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| FK constraint fails on existing data | LOW | MEDIUM | Run on fresh DB or add migration |
| lastActiveAt update breaks tests | MEDIUM | LOW | Update affected mocks |
| Integration tests flaky | LOW | MEDIUM | Use unique DB per test class |
| Achievement integration scope creep | MEDIUM | LOW | Mark as optional, defer if needed |

---

**Document End**

*Last Updated: 2026-01-11*
*Author: AI Agent Analysis*



   for writing tests:

Business logic → Mockito

Persistence logic → real DB (often an in-memory one)

Critical flows → end-to-end real DB

Most tests should be unit tests.
Most unit tests should not know a database exists.