# Relationship Lifecycle Management

**Date:** 2026-01-12
**Status:** Design Approved
**Estimated Implementation:** 14-19 hours
**Priority:** Medium — Enhances user experience and emotional safety

---

## Problem Statement

### The Gap

Dating apps focus heavily on **getting matches** but ignore **what happens after**. Connections evolve — sometimes into friendship, sometimes they need to end. Currently:

- No way to transition a match to "just friends" without awkward conversations
- Unmatching is silent and abrupt — feels like ghosting
- No visibility into communication style compatibility before investing time
- Users collect stale matches with no graceful way to clean up

### The Vision

Help connections evolve naturally through different stages with:
- Clear expectations about communication pace
- Respectful transitions (to friendship or ending)
- Closure without cruelty

---

## Solution: Three Integrated Features

### 1. Pace Sync

**What:** Multi-dimensional compatibility scoring on communication and dating rhythm.

**Four Dimensions:**
| Dimension | Options |
|-----------|---------|
| Messaging Frequency | Few times a week / Daily / Throughout the day |
| Time to First Date | Within a week / 2-3 weeks / Month+ / No rush |
| Communication Style | Text only / Calls welcome / Mix of everything |
| Depth Preference | Keep it light / Like going deep / Depends on vibe |

**When Set:** During profile setup (global preference, not per-match).

**Display:** Compatibility percentage (0-100%) shown on match, expandable for dimension details.

**Low Compatibility Warning:** Below 50% shows soft warning: "Your pacing styles differ. Worth discussing early!"

### 2. Friend Zone

**What:** Transition a romantic match to platonic friendship with mutual consent.

**Flow:**
1. User A initiates "Move to Friends"
2. User A must include a personal message (10+ characters)
3. User B receives request with message
4. User B can Accept (become friends) or Decline (unmatch)

**After Acceptance:**
- Match state becomes `FRIENDS`
- Conversation archived but accessible
- Can continue messaging (new messages appear)
- Cannot transition back to romantic

### 3. Graceful Exit

**What:** End a connection kindly with one click.

**Flow:**
1. User clicks "End Gracefully"
2. System sends notification: "[Name] has moved on. They wish you well."
3. Match state becomes `GRACEFUL_EXIT`
4. Conversation archived
5. No response required or expected

**Philosophy:** Make kindness easier than ghosting. Same effort (one click), better outcome.

---

## Domain Models

### New Records

```java
// Pace preferences (immutable, stored per user)
public record PacePreferences(
    MessagingFrequency messagingFrequency,
    TimeToFirstDate timeToFirstDate,
    CommunicationStyle communicationStyle,
    DepthPreference depthPreference
) {}

// Enums for pace dimensions
public enum MessagingFrequency {
    FEW_TIMES_WEEK("Few times a week"),
    DAILY("Daily"),
    THROUGHOUT_DAY("Throughout the day");

    private final String display;
    // constructor, getter
}

public enum TimeToFirstDate {
    WITHIN_WEEK("Within a week"),
    TWO_THREE_WEEKS("2-3 weeks"),
    MONTH_PLUS("A month or more"),
    NO_RUSH("No rush");

    private final String display;
}

public enum CommunicationStyle {
    TEXT_ONLY("Text only"),
    CALLS_WELCOME("Calls welcome"),
    MIX_OF_EVERYTHING("Mix of everything");

    private final String display;
}

public enum DepthPreference {
    KEEP_IT_LIGHT("Keep it light at first"),
    LIKE_GOING_DEEP("I like going deep"),
    DEPENDS_ON_VIBE("Depends on the vibe");

    private final String display;
}

// Friend request between matched users
public record FriendRequest(
    UUID id,
    UUID fromUserId,
    UUID toUserId,
    String message,
    Instant createdAt,
    FriendRequestStatus status,
    Instant respondedAt
) {}

public enum FriendRequestStatus {
    PENDING, ACCEPTED, DECLINED
}

// Archive tracking reason
public enum ArchiveReason {
    FRIEND_ZONE, GRACEFUL_EXIT, UNMATCH, BLOCK
}
```

### Extended Match Entity

```java
public class Match {
    // ... existing fields ...

    // Extended state machine
    private MatchState state;  // ACTIVE, FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED

    // Transition metadata
    private UUID endedBy;
    private Instant endedAt;
    private ArchiveReason endReason;

    public void transitionTo(MatchState newState) {
        if (!isValidTransition(this.state, newState)) {
            throw new IllegalStateException(
                "Cannot transition from " + state + " to " + newState
            );
        }
        this.state = newState;
    }

    private boolean isValidTransition(MatchState from, MatchState to) {
        return switch (from) {
            case ACTIVE -> Set.of(FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED).contains(to);
            case FRIENDS -> Set.of(UNMATCHED, GRACEFUL_EXIT, BLOCKED).contains(to);
            case UNMATCHED, GRACEFUL_EXIT, BLOCKED -> false; // Terminal states
        };
    }
}

public enum MatchState {
    ACTIVE,
    FRIENDS,
    UNMATCHED,
    GRACEFUL_EXIT,
    BLOCKED
}
```

### Extended User Entity

```java
public class User {
    // ... existing fields ...

    private PacePreferences pacePreferences;

    public boolean hasCompletePace() {
        return pacePreferences != null
            && pacePreferences.messagingFrequency() != null
            && pacePreferences.timeToFirstDate() != null
            && pacePreferences.communicationStyle() != null
            && pacePreferences.depthPreference() != null;
    }
}
```

### Extended Conversation Entity

```java
public class Conversation {
    // ... existing fields ...

    // Archive support
    private Instant archivedAt;
    private ArchiveReason archiveReason;
    private boolean visibleToUserA;
    private boolean visibleToUserB;

    public boolean isArchivedFor(UUID userId) {
        if (archivedAt == null) return false;
        if (userId.equals(userA)) return !visibleToUserA;
        if (userId.equals(userB)) return !visibleToUserB;
        return true;
    }
}
```

### Notification Entity

```java
public record Notification(
    UUID id,
    UUID userId,
    NotificationType type,
    String payloadJson,
    Instant createdAt,
    Instant readAt,
    Instant dismissedAt
) {
    public boolean isUnread() {
        return readAt == null;
    }
}

public enum NotificationType {
    GRACEFUL_EXIT,
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    FRIEND_DECLINED
}
```

---

## Services

### PaceCompatibilityService

```java
public class PaceCompatibilityService {

    private static final int LOW_COMPATIBILITY_THRESHOLD = 50;

    public int calculateCompatibility(PacePreferences a, PacePreferences b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both users must have pace preferences");
        }

        int score = 0;
        score += dimensionScore(a.messagingFrequency(), b.messagingFrequency());
        score += dimensionScore(a.timeToFirstDate(), b.timeToFirstDate());
        score += dimensionScore(a.communicationStyle(), b.communicationStyle());
        score += dimensionScore(a.depthPreference(), b.depthPreference());
        return score;
    }

    private int dimensionScore(Enum<?> a, Enum<?> b) {
        int distance = Math.abs(a.ordinal() - b.ordinal());
        return switch (distance) {
            case 0 -> 25;  // Perfect match
            case 1 -> 15;  // Close enough
            default -> 5;  // Quite different
        };
    }

    public boolean isLowCompatibility(int score) {
        return score < LOW_COMPATIBILITY_THRESHOLD;
    }

    public String getLowCompatibilityWarning() {
        return "Your pacing styles differ significantly. Worth discussing early!";
    }
}
```

### RelationshipTransitionService

```java
public class RelationshipTransitionService {

    private static final int MIN_MESSAGE_LENGTH = 10;

    private final MatchStorage matchStorage;
    private final FriendRequestStorage friendRequestStorage;
    private final ConversationStorage conversationStorage;
    private final NotificationStorage notificationStorage;
    private final UserStorage userStorage;

    // Constructor with DI...

    // ─── Friend Zone ───────────────────────────────────────────────

    public FriendRequest proposeFriendZone(UUID fromUserId, UUID toUserId, String message) {
        // Validate match exists and is active
        Match match = matchStorage.getByUsers(fromUserId, toUserId)
            .orElseThrow(() -> new TransitionValidationException(ErrorCode.NO_ACTIVE_MATCH));

        if (match.getState() != MatchState.ACTIVE) {
            throw new TransitionValidationException(ErrorCode.CANNOT_TRANSITION);
        }

        // Validate no pending request
        if (friendRequestStorage.getPending(fromUserId, toUserId).isPresent() ||
            friendRequestStorage.getPending(toUserId, fromUserId).isPresent()) {
            throw new TransitionValidationException(ErrorCode.REQUEST_PENDING);
        }

        // Validate message
        if (message == null || message.trim().length() < MIN_MESSAGE_LENGTH) {
            throw new TransitionValidationException(ErrorCode.MESSAGE_TOO_SHORT);
        }

        FriendRequest request = new FriendRequest(
            UUID.randomUUID(),
            fromUserId,
            toUserId,
            message.trim(),
            Instant.now(),
            FriendRequestStatus.PENDING,
            null
        );

        FriendRequest saved = friendRequestStorage.save(request);

        // Notify recipient
        queueNotification(toUserId, NotificationType.FRIEND_REQUEST, request);

        return saved;
    }

    public Match respondToFriendRequest(UUID requestId, UUID respondingUserId, boolean accept) {
        FriendRequest request = friendRequestStorage.get(requestId)
            .orElseThrow(() -> new TransitionValidationException(ErrorCode.REQUEST_NOT_FOUND));

        if (!request.toUserId().equals(respondingUserId)) {
            throw new TransitionValidationException(ErrorCode.NOT_AUTHORIZED);
        }

        if (request.status() != FriendRequestStatus.PENDING) {
            throw new TransitionValidationException(ErrorCode.ALREADY_RESPONDED);
        }

        Match match = matchStorage.getByUsers(request.fromUserId(), request.toUserId())
            .orElseThrow(() -> new TransitionValidationException(ErrorCode.NO_ACTIVE_MATCH));

        Instant now = Instant.now();

        if (accept) {
            match.transitionTo(MatchState.FRIENDS);
            friendRequestStorage.updateStatus(requestId, FriendRequestStatus.ACCEPTED, now);
            queueNotification(request.fromUserId(), NotificationType.FRIEND_ACCEPTED, request);
        } else {
            match.transitionTo(MatchState.UNMATCHED);
            match.setEndedBy(respondingUserId);
            match.setEndedAt(now);
            match.setEndReason(ArchiveReason.FRIEND_ZONE);
            friendRequestStorage.updateStatus(requestId, FriendRequestStatus.DECLINED, now);
            queueNotification(request.fromUserId(), NotificationType.FRIEND_DECLINED, request);
        }

        // Archive conversation
        archiveConversation(match, ArchiveReason.FRIEND_ZONE);

        return matchStorage.save(match);
    }

    public List<FriendRequest> getPendingRequestsFor(UUID userId) {
        return friendRequestStorage.getPendingFor(userId);
    }

    // ─── Graceful Exit ─────────────────────────────────────────────

    public Match gracefulExit(UUID initiatorId, UUID otherUserId) {
        Match match = matchStorage.getByUsers(initiatorId, otherUserId)
            .orElseThrow(() -> new TransitionValidationException(ErrorCode.NO_ACTIVE_MATCH));

        // Idempotent: if already exited, return success
        if (match.getState() == MatchState.GRACEFUL_EXIT) {
            return match;
        }

        if (match.getState() == MatchState.BLOCKED) {
            throw new TransitionValidationException(ErrorCode.BLOCKED_RELATIONSHIP);
        }

        if (match.getState() != MatchState.ACTIVE && match.getState() != MatchState.FRIENDS) {
            throw new TransitionValidationException(ErrorCode.CANNOT_TRANSITION);
        }

        Instant now = Instant.now();
        match.transitionTo(MatchState.GRACEFUL_EXIT);
        match.setEndedBy(initiatorId);
        match.setEndedAt(now);
        match.setEndReason(ArchiveReason.GRACEFUL_EXIT);

        // Archive conversation
        archiveConversation(match, ArchiveReason.GRACEFUL_EXIT);

        // Queue notification for other user
        User initiator = userStorage.get(initiatorId).orElseThrow();
        queueGracefulExitNotification(otherUserId, initiator.getName());

        return matchStorage.save(match);
    }

    // ─── Helper Methods ────────────────────────────────────────────

    private void archiveConversation(Match match, ArchiveReason reason) {
        conversationStorage.getByUsers(match.getUserA(), match.getUserB())
            .ifPresent(convo -> {
                convo.setArchivedAt(Instant.now());
                convo.setArchiveReason(reason);
                convo.setVisibleToUserA(true);  // Hidden by default, but accessible
                convo.setVisibleToUserB(true);
                conversationStorage.save(convo);
            });
    }

    private void queueGracefulExitNotification(UUID recipientId, String initiatorName) {
        String message = initiatorName + " has moved on. They wish you well.";
        Notification notification = new Notification(
            UUID.randomUUID(),
            recipientId,
            NotificationType.GRACEFUL_EXIT,
            "{\"message\":\"" + message + "\"}",
            Instant.now(),
            null,
            null
        );
        notificationStorage.save(notification);
    }

    private void queueNotification(UUID userId, NotificationType type, FriendRequest request) {
        // Serialize request to JSON for payload
        Notification notification = new Notification(
            UUID.randomUUID(),
            userId,
            type,
            serializePayload(request),
            Instant.now(),
            null,
            null
        );
        notificationStorage.save(notification);
    }
}
```

### Exception Handling

```java
public class TransitionValidationException extends RuntimeException {
    private final ErrorCode code;

    public TransitionValidationException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public ErrorCode getCode() { return code; }

    public enum ErrorCode {
        NO_ACTIVE_MATCH("No active match found"),
        ALREADY_FRIENDS("You are already friends"),
        ALREADY_ENDED("This connection has already ended"),
        REQUEST_PENDING("A friend request is already pending"),
        REQUEST_NOT_FOUND("Friend request not found"),
        NOT_AUTHORIZED("You are not authorized to perform this action"),
        ALREADY_RESPONDED("This request has already been responded to"),
        MESSAGE_TOO_SHORT("Please include a thoughtful message (at least 10 characters)"),
        CANNOT_TRANSITION("Cannot perform this transition"),
        BLOCKED_RELATIONSHIP("This relationship is blocked");

        private final String message;
        ErrorCode(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
```

---

## Storage Interfaces

### FriendRequestStorage

```java
public interface FriendRequestStorage {
    FriendRequest save(FriendRequest request);
    Optional<FriendRequest> get(UUID id);
    Optional<FriendRequest> getPending(UUID fromUserId, UUID toUserId);
    List<FriendRequest> getPendingFor(UUID userId);
    void updateStatus(UUID id, FriendRequestStatus status, Instant respondedAt);
}
```

### NotificationStorage

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

## Database Schema

### Modified Tables

```sql
-- Extend matches table
ALTER TABLE matches ADD COLUMN ended_by UUID;
ALTER TABLE matches ADD COLUMN ended_at TIMESTAMP;
ALTER TABLE matches ADD COLUMN end_reason VARCHAR(20);

-- Update state values: ACTIVE, FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED

-- Extend conversations table
ALTER TABLE conversations ADD COLUMN archived_at TIMESTAMP;
ALTER TABLE conversations ADD COLUMN archive_reason VARCHAR(20);
ALTER TABLE conversations ADD COLUMN visible_to_user_a BOOLEAN DEFAULT TRUE;
ALTER TABLE conversations ADD COLUMN visible_to_user_b BOOLEAN DEFAULT TRUE;

-- Extend users table for pace preferences
ALTER TABLE users ADD COLUMN pace_messaging_frequency VARCHAR(20);
ALTER TABLE users ADD COLUMN pace_time_to_first_date VARCHAR(20);
ALTER TABLE users ADD COLUMN pace_communication_style VARCHAR(20);
ALTER TABLE users ADD COLUMN pace_depth_preference VARCHAR(20);
```

### New Tables

```sql
-- Friend requests
CREATE TABLE friend_requests (
    id UUID PRIMARY KEY,
    from_user_id UUID NOT NULL REFERENCES users(id),
    to_user_id UUID NOT NULL REFERENCES users(id),
    message VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP,
    UNIQUE (from_user_id, to_user_id)
);

CREATE INDEX idx_friend_requests_to_user ON friend_requests(to_user_id, status);

-- Notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    payload_json VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP,
    dismissed_at TIMESTAMP
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at);
```

---

## MessagingService Integration

```java
public class MessagingService {

    public SendResult sendMessage(UUID senderId, UUID recipientId, String content) {
        Match match = matchStorage.getByUsers(senderId, recipientId).orElse(null);

        if (match == null) {
            return SendResult.error(ErrorCode.NO_MATCH);
        }

        // Allow messaging for both ACTIVE and FRIENDS
        if (match.getState() != MatchState.ACTIVE && match.getState() != MatchState.FRIENDS) {
            return SendResult.error(ErrorCode.MATCH_ENDED);
        }

        // ... rest of existing logic ...
    }
}
```

---

## Error Handling & Edge Cases

### Friend Zone

| Scenario | Handling |
|----------|----------|
| Both users send request simultaneously | First one wins (unique constraint), second sees "request pending" |
| Request pending, one user blocks | Cancel pending request, proceed with block |
| Request to gracefully-exited match | Reject: "This connection has ended" |
| Message too short | Reject: "Please include a thoughtful message (at least 10 characters)" |

### Graceful Exit

| Scenario | Handling |
|----------|----------|
| Already exited | No-op, return success (idempotent) |
| Try to message after | `SendResult.error(MATCH_ENDED)` |
| Both exit simultaneously | First one wins, second is idempotent |
| Exit blocked match | Reject: blocking is stronger action |

### Pace Sync

| Scenario | Handling |
|----------|----------|
| User hasn't set preferences | Skip compatibility, show "Set your pace preferences" prompt |
| Only one user has preferences | Show partial: "Your pace: Daily. Their pace: Not set yet" |
| User updates pace after matching | Existing matches NOT recalculated |

---

## UI Entry Points

| Location | New Options |
|----------|-------------|
| Match detail screen | "Move to Friends" / "End Gracefully" buttons |
| Conversation screen | Same options via `/friends` and `/exit` commands |
| Friends list (NEW) | New section showing `FRIENDS` state matches |
| Notifications (NEW) | Friend requests + graceful exit messages |
| Profile setup | Pace preference questions (4 prompts) |
| Match display | Pace compatibility percentage |

---

## Testing Strategy

### Unit Tests (~32 tests)

**PaceCompatibilityServiceTest:**
- Perfect match returns 100
- Complete opposites returns 20
- Adjacent values returns 60
- Mixed alignment calculates correctly
- Null preferences throws exception
- Low compatibility detection (3 tests)

**RelationshipTransitionServiceTest:**
- Propose friend zone: valid request, no match, message too short, already friends, pending exists
- Respond to request: accept flow, decline flow, wrong user, not found, already responded
- Graceful exit: active match, friends match, archives conversation, queues notification, idempotent, blocked throws

### Integration Tests (~12 tests)

- Friend zone happy path (propose → accept)
- Friend zone decline path
- Graceful exit from active
- Graceful exit from friends
- Messaging after friend zone (allowed)
- Messaging after graceful exit (blocked)
- Storage CRUD operations

### Estimated Total: ~52 tests

---

## Implementation Plan

### Phase 1: Foundation (3-4 hours)

| Task | Effort |
|------|--------|
| Create pace enums and record | 35 min |
| Add pace to User entity + storage | 30 min |
| Create PaceCompatibilityService | 45 min |
| Extend MatchState enum | 15 min |
| Add transition metadata to Match | 30 min |
| Database migrations | 30 min |
| Unit tests | 45 min |

**Milestone:** Pace preferences storable, compatibility calculable.

### Phase 2: Friend Zone Flow (4-5 hours)

| Task | Effort |
|------|--------|
| Create FriendRequest + storage | 1.5 hours |
| RelationshipTransitionService (friend zone) | 1.5 hours |
| Add archive fields to Conversation | 30 min |
| Database migrations | 20 min |
| Unit tests | 1 hour |

**Milestone:** Friend zone flow complete.

### Phase 3: Graceful Exit Flow (2-3 hours)

| Task | Effort |
|------|--------|
| Add gracefulExit to service | 45 min |
| Notification entity + storage | 1 hour |
| Database migrations | 15 min |
| Unit tests | 45 min |

**Milestone:** Graceful exit complete.

### Phase 4: CLI Integration (3-4 hours)

| Task | Effort |
|------|--------|
| Pace setup in profile flow | 45 min |
| Pace display on match view | 30 min |
| Friend zone / graceful exit menu options | 1 hour |
| Friends list view | 45 min |
| Notifications display | 45 min |
| ServiceRegistry wiring | 30 min |

**Milestone:** Full CLI integration.

### Phase 5: Polish & Testing (2-3 hours)

| Task | Effort |
|------|--------|
| Integration tests | 1.5 hours |
| Edge case handling | 30 min |
| Documentation update | 30 min |
| Final testing | 30 min |

**Milestone:** Production-ready.

---

## Success Criteria

### Functional

- [ ] User can set pace preferences during profile setup
- [ ] Match displays pace compatibility percentage
- [ ] Low compatibility shows warning message
- [ ] User can propose friend zone with required message
- [ ] Recipient can accept or decline friend request
- [ ] Friends can continue messaging
- [ ] User can gracefully exit any active/friends match
- [ ] Recipient sees graceful exit notification
- [ ] Conversation history preserved but archived

### Quality

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No regression in existing match/messaging functionality
- [ ] Clean state machine transitions

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-12 | Initial design from brainstorming session |

---

**Status:** Ready for Implementation
