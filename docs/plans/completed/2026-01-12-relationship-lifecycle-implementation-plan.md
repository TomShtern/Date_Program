# Relationship Lifecycle Management - Implementation Plan

**Date:** 2026-01-12
**Based On:** [2026-01-12-relationship-lifecycle-design.md](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/docs/plans/2026-01-12-relationship-lifecycle-design.md)
**Estimated Time:** 19-25 hours (revised from original 14-19 hours)
**Priority:** Medium — Enhances user experience and emotional safety

---

## Goal Description

Implement the Relationship Lifecycle Management feature consisting of three integrated features:
1. **Pace Sync** - Multi-dimensional communication compatibility scoring
2. **Friend Zone** - Mutual consent transition from romantic to platonic
3. **Graceful Exit** - Kind one-click connection ending with notification

This plan incorporates all corrections, improvements, and missing considerations identified during design review.

---

## User Review Required

> [!IMPORTANT]
> **Breaking Change: Match.State Extension**
> The Match entity's State enum is being extended from 3 to 5 states (adding FRIENDS, GRACEFUL_EXIT). Existing matches in database need migration to explicitly set `state = 'ACTIVE'`.

> [!WARNING]
> **MessagingService Behavior Change**
> After implementation, `canMessage()` and `sendMessage()` will allow messaging for both ACTIVE and FRIENDS matches. Previously only ACTIVE was valid.

> [!CAUTION]
> **Messaging Integration Requirement**
> Existing `MessagingService.canMessage()` uses `matchOpt.get().isActive()`. This MUST be updated to also allow FRIENDS state to prevent breaking messaging for friend-zoned matches.

---

## Critical Fixes from Design Review

These issues were identified in the design document and are corrected in this implementation plan:

### Fix 1: getByUsers() API Gap
The design uses `matchStorage.getByUsers(fromUserId, toUserId)` but interface lacks this method.

**Solution:** Use existing pattern `matchStorage.get(Match.generateId(userId1, userId2))` which leverages deterministic ID generation already in codebase.

### Fix 2: JSON Injection Vulnerability
Design uses unsafe string concatenation for notification JSON payload.

**Solution:** Use Jackson ObjectMapper for proper JSON serialization of notification payloads.

### Fix 3: UNIQUE Constraint on friend_requests
`UNIQUE (from_user_id, to_user_id)` blocks rematched users from sending new requests.

**Solution:** Add `match_id` column and change constraint to `UNIQUE (from_user_id, to_user_id, match_id)`.

### Fix 4: DepthPreference Scoring
Ordinal-based scoring makes DEPENDS_ON_VIBE incompatible with KEEP_IT_LIGHT (distance 2).

**Solution:** Treat DEPENDS_ON_VIBE and MIX_OF_EVERYTHING as wildcards giving 20 points with any value.

### Fix 5: Match Class Design
Design proposes public setters but current code uses encapsulated methods.

**Solution:** Add new encapsulated methods `transitionToFriends(UUID)` and `gracefulExit(UUID)` following existing `unmatch()`/`block()` pattern.

---

## Proposed Changes

### Core Domain Layer

#### [MODIFY] Match.java

1. Extend `State` enum to add `FRIENDS` and `GRACEFUL_EXIT`:
```java
public enum State {
    ACTIVE, FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED
}
```

2. Add `endReason` field with getter:
```java
private ArchiveReason endReason;
public ArchiveReason getEndReason() { return endReason; }
```

3. Add new encapsulated transition methods:
```java
public void transitionToFriends(UUID initiatorId) { ... }
public void gracefulExit(UUID initiatorId) { ... }
```

4. Add state machine validation method:
```java
private boolean isValidTransition(State from, State to) {
    return switch (from) {
        case ACTIVE -> Set.of(FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED).contains(to);
        case FRIENDS -> Set.of(UNMATCHED, GRACEFUL_EXIT, BLOCKED).contains(to);
        case UNMATCHED, GRACEFUL_EXIT, BLOCKED -> false;
    };
}
```

5. Add `canMessage()` helper:
```java
public boolean canMessage() {
    return state == State.ACTIVE || state == State.FRIENDS;
}
```

---

#### [NEW] ArchiveReason.java

```java
public enum ArchiveReason {
    FRIEND_ZONE, GRACEFUL_EXIT, UNMATCH, BLOCK
}
```

---

#### [NEW] MessagingFrequency.java

```java
public enum MessagingFrequency {
    FEW_TIMES_WEEK("Few times a week"),
    DAILY("Daily"),
    THROUGHOUT_DAY("Throughout the day");

    private final String displayName;
    // constructor, getter
}
```

---

#### [NEW] TimeToFirstDate.java

```java
public enum TimeToFirstDate {
    WITHIN_WEEK("Within a week"),
    TWO_THREE_WEEKS("2-3 weeks"),
    MONTH_PLUS("A month or more"),
    NO_RUSH("No rush");

    private final String displayName;
}
```

---

#### [NEW] CommunicationStyle.java

```java
public enum CommunicationStyle {
    TEXT_ONLY("Text only"),
    CALLS_WELCOME("Calls welcome"),
    MIX_OF_EVERYTHING("Mix of everything");  // Treated as wildcard in scoring

    private final String displayName;
}
```

---

#### [NEW] DepthPreference.java

```java
public enum DepthPreference {
    KEEP_IT_LIGHT("Keep it light at first"),
    LIKE_GOING_DEEP("I like going deep"),
    DEPENDS_ON_VIBE("Depends on the vibe");  // Treated as wildcard in scoring

    private final String displayName;
}
```

---

#### [NEW] PacePreferences.java

```java
public record PacePreferences(
    MessagingFrequency messagingFrequency,
    TimeToFirstDate timeToFirstDate,
    CommunicationStyle communicationStyle,
    DepthPreference depthPreference
) {
    public boolean isComplete() {
        return messagingFrequency != null
            && timeToFirstDate != null
            && communicationStyle != null
            && depthPreference != null;
    }
}
```

---

#### [MODIFY] User.java

1. Add `pacePreferences` field:
```java
private PacePreferences pacePreferences;
```

2. Add getter and setter:
```java
public PacePreferences getPacePreferences() { return pacePreferences; }
public void setPacePreferences(PacePreferences preferences) {
    this.pacePreferences = preferences;
}
```

3. Update `fromDatabase()` factory to include pace preference columns

---

#### [NEW] PaceCompatibilityService.java

With corrected wildcard scoring:
```java
public class PaceCompatibilityService {
    private static final int LOW_COMPAT_THRESHOLD = 50;
    private static final int WILDCARD_SCORE = 20;

    public int calculateCompatibility(PacePreferences a, PacePreferences b) {
        if (a == null || b == null) return -1;  // Unknown

        int score = 0;
        score += dimensionScore(a.messagingFrequency(), b.messagingFrequency(), false);
        score += dimensionScore(a.timeToFirstDate(), b.timeToFirstDate(), false);
        score += dimensionScore(a.communicationStyle(), b.communicationStyle(),
            isWildcard(a.communicationStyle()) || isWildcard(b.communicationStyle()));
        score += dimensionScore(a.depthPreference(), b.depthPreference(),
            isWildcard(a.depthPreference()) || isWildcard(b.depthPreference()));
        return score;
    }

    private int dimensionScore(Enum<?> a, Enum<?> b, boolean hasWildcard) {
        if (hasWildcard) return WILDCARD_SCORE;
        int distance = Math.abs(a.ordinal() - b.ordinal());
        return switch (distance) {
            case 0 -> 25;
            case 1 -> 15;
            default -> 5;
        };
    }

    private boolean isWildcard(Enum<?> val) {
        return val == CommunicationStyle.MIX_OF_EVERYTHING
            || val == DepthPreference.DEPENDS_ON_VIBE;
    }

    public boolean isLowCompatibility(int score) {
        return score >= 0 && score < LOW_COMPAT_THRESHOLD;
    }
}
```

---

#### [NEW] FriendRequestStatus.java

```java
public enum FriendRequestStatus {
    PENDING, ACCEPTED, DECLINED
}
```

---

#### [NEW] FriendRequest.java

```java
public record FriendRequest(
    UUID id,
    String matchId,  // Added for constraint fix
    UUID fromUserId,
    UUID toUserId,
    String message,
    Instant createdAt,
    FriendRequestStatus status,
    Instant respondedAt
) {}
```

---

#### [NEW] FriendRequestStorage.java

```java
public interface FriendRequestStorage {
    FriendRequest save(FriendRequest request);
    Optional<FriendRequest> get(UUID id);
    Optional<FriendRequest> getPending(UUID fromUserId, UUID toUserId);
    List<FriendRequest> getPendingFor(UUID userId);
    void updateStatus(UUID id, FriendRequestStatus status, Instant respondedAt);
    int countSentToday(UUID userId);  // Rate limiting
}
```

---

#### [NEW] NotificationType.java

```java
public enum NotificationType {
    GRACEFUL_EXIT,
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    FRIEND_DECLINED
}
```

---

#### [NEW] Notification.java

```java
public record Notification(
    UUID id,
    UUID userId,
    NotificationType type,
    Map<String, Object> payload,  // Type-safe, serialized via Jackson
    Instant createdAt,
    Instant readAt,
    Instant dismissedAt
) {
    public boolean isUnread() { return readAt == null; }
}
```

---

#### [NEW] NotificationStorage.java

```java
public interface NotificationStorage {
    Notification save(Notification notification);
    List<Notification> getUnread(UUID userId);
    int countUnread(UUID userId);
    void markRead(UUID notificationId);
    void dismiss(UUID notificationId);
}
```

---

#### [NEW] TransitionValidationException.java

```java
public class TransitionValidationException extends RuntimeException {
    public enum ErrorCode {
        NO_ACTIVE_MATCH("No active match found"),
        ALREADY_FRIENDS("You are already friends"),
        ALREADY_ENDED("This connection has already ended"),
        REQUEST_PENDING("A friend request is already pending"),
        REQUEST_NOT_FOUND("Friend request not found"),
        NOT_AUTHORIZED("Not authorized for this action"),
        ALREADY_RESPONDED("Request already responded to"),
        MESSAGE_TOO_SHORT("Message must be at least 10 characters"),
        CANNOT_TRANSITION("Cannot perform this transition"),
        BLOCKED_RELATIONSHIP("This relationship is blocked"),
        RATE_LIMITED("Too many requests today, try again tomorrow"),
        COOLING_OFF("Please wait before contacting this person again");

        private final String message;
        // constructor, getMessage()
    }

    private final ErrorCode code;
    // constructor, getter
}
```

---

#### [NEW] RelationshipTransitionService.java

Main service with:
- `proposeFriendZone(UUID from, UUID to, String message)` with rate limiting
- `respondToFriendRequest(UUID requestId, UUID respondingUser, boolean accept)`
- `gracefulExit(UUID initiator, UUID other)` with cooling off tracking
- `getPendingRequestsFor(UUID userId)`
- Helper methods for notifications using Jackson ObjectMapper

---

#### [MODIFY] Conversation.java

Add archive fields:
```java
private Instant archivedAt;
private ArchiveReason archiveReason;
private boolean accessibleToUserA = true;
private boolean accessibleToUserB = true;

// Setters and getters
public void archive(ArchiveReason reason) { ... }
public boolean isArchivedFor(UUID userId) { ... }
```

---

#### [MODIFY] MessagingService.java

Update `canMessage()` and `sendMessage()` to check `match.canMessage()` instead of `match.isActive()`:

```diff
-    return matchOpt.isPresent() && matchOpt.get().isActive();
+    return matchOpt.isPresent() && matchOpt.get().canMessage();
```

---

### Storage Layer

#### [NEW] H2FriendRequestStorage.java

H2 implementation with corrected constraint and rate limiting support.

---

#### [NEW] H2NotificationStorage.java

H2 implementation using Jackson for JSON serialization of payload.

---

#### [MODIFY] H2MatchStorage.java

Add `endReason` column handling in save/update/reconstitution.

---

#### [MODIFY] H2UserStorage.java

Add pace preference columns handling.

---

#### [MODIFY] H2ConversationStorage.java

Add archive field column handling.

---

### Database Migrations

#### [NEW] V15__relationship_lifecycle.sql

```sql
-- Match extensions
ALTER TABLE matches ADD COLUMN end_reason VARCHAR(20);
UPDATE matches SET state = 'ACTIVE' WHERE state IS NULL;

-- User pace preferences
ALTER TABLE users ADD COLUMN pace_messaging_frequency VARCHAR(25);
ALTER TABLE users ADD COLUMN pace_time_to_first_date VARCHAR(25);
ALTER TABLE users ADD COLUMN pace_communication_style VARCHAR(25);
ALTER TABLE users ADD COLUMN pace_depth_preference VARCHAR(25);

-- Conversation archive
ALTER TABLE conversations ADD COLUMN archived_at TIMESTAMP;
ALTER TABLE conversations ADD COLUMN archive_reason VARCHAR(20);
ALTER TABLE conversations ADD COLUMN accessible_to_user_a BOOLEAN DEFAULT TRUE;
ALTER TABLE conversations ADD COLUMN accessible_to_user_b BOOLEAN DEFAULT TRUE;

-- Friend requests (with corrected constraint)
CREATE TABLE friend_requests (
    id UUID PRIMARY KEY,
    match_id VARCHAR(100) NOT NULL,
    from_user_id UUID NOT NULL REFERENCES users(id),
    to_user_id UUID NOT NULL REFERENCES users(id),
    message VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP,
    UNIQUE (from_user_id, to_user_id, match_id)
);
CREATE INDEX idx_friend_requests_to_user ON friend_requests(to_user_id, status);
CREATE INDEX idx_friend_requests_from_date ON friend_requests(from_user_id, created_at);

-- Notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    payload_json VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP,
    dismissed_at TIMESTAMP
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at);
```

---

### CLI Layer

#### [NEW] RelationshipHandler.java

New CLI handler for:
- Friend zone initiation with message prompt
- Friend request response (accept/decline)
- Graceful exit with confirmation
- Friends list display
- Notification display and management

---

#### [MODIFY] Main.java

1. Add pace preference setup in profile completion flow
2. Add pace compatibility display on match view
3. Add menu options for relationship transitions
4. Add friends list menu option
5. Add notifications menu option

---

#### [MODIFY] MatchingHandler.java

Add pace compatibility percentage to match display with low compatibility warning.

---

#### [MODIFY] ServiceRegistry.java

Add new service and storage getters:
- `getFriendRequestStorage()`
- `getNotificationStorage()`
- `getPaceCompatibilityService()`
- `getRelationshipTransitionService()`

---

#### [MODIFY] ServiceRegistryBuilder.java

Wire new storage implementations and services.

---

### Test Files

#### [NEW] PaceCompatibilityServiceTest.java

~12 tests covering:
- Perfect match returns 100
- Complete opposites returns minimum
- Wildcard handling for DEPENDS_ON_VIBE
- Wildcard handling for MIX_OF_EVERYTHING
- Partial preferences (null handling)
- Low compatibility detection
- Edge cases

---

#### [NEW] RelationshipTransitionServiceTest.java

~25 tests covering:
- Friend zone: valid request, no match, message too short, already friends, pending exists, rate limited
- Respond: accept flow, decline flow, wrong user, not found, already responded
- Graceful exit: active match, friends match, idempotent, blocked throws, cooling off
- Notifications queued correctly

---

#### [NEW] RelationshipLifecycleIntegrationTest.java

~12 integration tests with H2:
- Friend zone happy path (propose → accept)
- Friend zone decline path
- Graceful exit from active
- Graceful exit from friends
- Messaging after friend zone (allowed)
- Messaging after graceful exit (blocked)
- Pace compatibility with real users
- Notification delivery

---

## Verification Plan

### Automated Tests

```bash
# Run all tests including new ones
mvn test

# Run specific test classes
mvn test -Dtest=PaceCompatibilityServiceTest
mvn test -Dtest=RelationshipTransitionServiceTest
mvn test -Dtest=RelationshipLifecycleIntegrationTest
```

### Manual CLI Testing

1. **Pace Setup Flow:**
   - Create new user
   - Go through profile completion
   - Verify pace preference prompts appear
   - Verify preferences are saved

2. **Pace Display:**
   - Create two users with different pace preferences
   - Create a match between them
   - View match → verify compatibility % displays
   - Test low compatibility warning (< 50%)

3. **Friend Zone Flow:**
   - With active match, select "Move to Friends"
   - Verify message prompt (10+ chars required)
   - Switch to other user, verify notification
   - Accept request → verify state is FRIENDS
   - Verify messaging still works
   - Test decline path (should unmatch)

4. **Graceful Exit Flow:**
   - With active match, select "End Gracefully"
   - Verify confirmation prompt
   - Execute → verify notification sent
   - Verify messaging blocked
   - Test idempotency (exit again = no error)

5. **Friends List:**
   - Have multiple matches in FRIENDS state
   - Navigate to friends list
   - Verify correct matches displayed
   - Verify can initiate message from list

---

## Implementation Phases

### Phase 1: Foundation (4-5 hours)
| Task | Files | Effort |
|------|-------|--------|
| Create pace enums (4 files) | New files | 30 min |
| Create PacePreferences record | New file | 15 min |
| Create ArchiveReason enum | New file | 10 min |
| Extend Match.State + add fields | Match.java | 45 min |
| Add pace to User entity | User.java, H2UserStorage.java | 45 min |
| Create PaceCompatibilityService | New file | 45 min |
| Database migration | V15 file | 30 min |
| Unit tests | New test files | 1 hour |

**Milestone:** Pace preferences storable, compatibility calculable with wildcard support.

---

### Phase 2: Friend Zone (6-7 hours)
| Task | Files | Effort |
|------|-------|--------|
| Create FriendRequest + status | New files | 30 min |
| FriendRequestStorage interface | New file | 20 min |
| H2FriendRequestStorage with rate limit | New file | 1.5 hours |
| Conversation archive fields | Conversation.java, H2ConversationStorage | 45 min |
| TransitionValidationException | New file | 20 min |
| RelationshipTransitionService (friend zone) | New file | 1.5 hours |
| Unit tests | New test file | 1.5 hours |

**Milestone:** Friend zone flow complete with rate limiting.

---

### Phase 3: Graceful Exit (3-4 hours)
| Task | Files | Effort |
|------|-------|--------|
| Notification + type enums | New files | 20 min |
| NotificationStorage interface | New file | 15 min |
| H2NotificationStorage with Jackson | New file | 1 hour |
| Add gracefulExit to service | RelationshipTransitionService | 45 min |
| Update MessagingService for FRIENDS | MessagingService.java | 20 min |
| Unit tests | Existing test file | 45 min |

**Milestone:** Graceful exit complete with safe JSON serialization.

---

### Phase 4: CLI Integration (4-6 hours)
| Task | Files | Effort |
|------|-------|--------|
| RelationshipHandler (new) | New file | 1.5 hours |
| Pace setup in profile flow | Main.java | 45 min |
| Pace display on match view | MatchingHandler.java | 30 min |
| Menu options for transitions | Main.java | 45 min |
| Friends list view | RelationshipHandler | 30 min |
| Notifications display | RelationshipHandler | 45 min |
| ServiceRegistry wiring | ServiceRegistry, Builder | 30 min |

**Milestone:** Full CLI integration.

---

### Phase 5: Testing & Polish (3-4 hours)
| Task | Files | Effort |
|------|-------|--------|
| Integration tests | New test file | 1.5 hours |
| Edge case handling | Various | 30 min |
| Manual CLI testing | N/A | 45 min |
| Documentation update | Design doc, README | 30 min |

**Milestone:** Production-ready.

---

## Success Criteria

### Functional
- [ ] User can set pace preferences during profile setup
- [ ] Match displays pace compatibility percentage
- [ ] Low compatibility (<50%) shows warning message
- [ ] Wildcard preferences (DEPENDS_ON_VIBE, MIX) score 20 with anything
- [ ] User can propose friend zone with required message (10+ chars)
- [ ] Rate limiting: max 5 friend requests per day per user
- [ ] Recipient can accept or decline friend request
- [ ] Accept → state FRIENDS, decline → state UNMATCHED
- [ ] Friends can continue messaging
- [ ] User can gracefully exit any ACTIVE/FRIENDS match
- [ ] Graceful exit is idempotent
- [ ] Recipient sees graceful exit notification
- [ ] Conversation history preserved but archived
- [ ] Messaging blocked after graceful exit

### Quality
- [ ] All unit tests pass (~49 new tests)
- [ ] All integration tests pass (~12 new tests)
- [ ] No regression in existing match/messaging functionality
- [ ] JSON payloads properly serialized (no injection)
- [ ] Clean state machine transitions
- [ ] Rate limiting prevents abuse

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| MessagingService regression | Add explicit test for FRIENDS messaging |
| State machine bugs | Comprehensive transition tests |
| JSON injection | Use Jackson, never string concat |
| UNIQUE constraint issues | Include match_id in constraint |
| Performance with notifications | Index on (user_id, read_at) |

---

## Out of Scope (Future Considerations)

- GDPR data deletion for archived conversations
- Notification preferences (enable/disable)
- Analytics/metrics for feature usage
- Push notifications (mobile)
- Transition back from FRIENDS to ACTIVE

---

**Status:** Ready for Implementation
