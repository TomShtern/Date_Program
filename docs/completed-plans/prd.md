# Dating App PRD

**Stack:** Java 25, Maven 3.9.12, H2 Database
**Scale:** ~300 users
**Phase 0:** Console app, local database, zero frameworks

---

## Rules (Must Follow)

1. **Core stays pure.** The `core` package has zero framework imports. No Spring, no database code. Just plain Java.

2. **One job per layer.** Core = business logic. Storage = saving/loading. Don't mix them.

3. **Start simple.** Don't build abstractions until you need them. Add complexity only when needed.

---

## Layers

```
core        → Business logic. Pure Java. The rules of how things work.
storage     → Saves/loads data. Uses H2 database.
```

That's it. Two layers.

---

## What Exists

**User** — fields:
- `id` (UUID)
- `name` (String)
- `bio` (String)
- `birthDate` (LocalDate)
- `gender` (Gender enum)
- `interestedIn` (Set of Gender)
- `lat`, `lon` (double)
- `maxDistanceKm` (int)
- `minAge`, `maxAge` (int)
- `photoUrls` (List of String, max 2)
- `state` (UserState enum)
- `createdAt`, `updatedAt` (Instant)

States: `INCOMPLETE` → `ACTIVE` ↔ `PAUSED` → `BANNED`

**Like** — fields:
- `id` (UUID)
- `whoLikes` (UUID)
- `whoGotLiked` (UUID)
- `direction` (LikeDirection enum: LIKE or PASS)
- `createdAt` (Instant)

Immutable after creation.

**Match** — fields:
- `id` (String — deterministic: sort two UUIDs, concatenate)
- `userA` (UUID — the lexicographically smaller one)
- `userB` (UUID — the lexicographically larger one)
- `createdAt` (Instant)

Created automatically when mutual like detected.

---

## The Flow

```
1. Create user                    → state = INCOMPLETE
2. Fill in profile                → state = ACTIVE (when complete)
3. Find candidates                → other ACTIVE users, filtered
4. Like or pass                   → saved
5. If mutual like exists          → match created
6. View matches
```

---

## Filtering Candidates

When finding candidates for a user:
- Must be ACTIVE
- Not self
- Not already liked/passed
- Within distance preference
- Matches gender preferences (both ways)
- Within age preferences (both ways)

Sorting: by distance (closest first). Keep it simple.

---

## Storage Interfaces

Define in `core`. Implement in `storage` using H2.

```java
interface UserStorage {
    void save(User user);
    User get(UUID id);
    List<User> findActive();
}

interface LikeStorage {
    void save(Like like);
    boolean exists(UUID from, UUID to);
    boolean mutualLikeExists(UUID a, UUID b);
    Set<UUID> getLikedUserIds(UUID userId);
}

interface MatchStorage {
    void save(Match match);
    List<Match> getMatchesFor(UUID userId);
}
```

---

## Database

**H2** — embedded SQL database.

- Runs inside your app (no server)
- Data stored in a file (`dating.mv.db`) or in-memory for tests
- Standard SQL
- One Maven dependency

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
</dependency>
```

If you ever need PostgreSQL later, same SQL mostly works.

---

## Package Structure

```
datingapp/
├── core/
│   ├── User.java
│   ├── Like.java
│   ├── Match.java
│   ├── UserState.java (enum)
│   ├── LikeDirection.java (enum)
│   ├── Gender.java (enum)
│   ├── UserStorage.java (interface)
│   ├── LikeStorage.java (interface)
│   └── MatchStorage.java (interface)
├── storage/
│   ├── H2UserStorage.java
│   ├── H2LikeStorage.java
│   └── H2MatchStorage.java
└── Main.java
```

---

## What the Engineer Decides

- SQL schema design
- Exact method signatures
- Validation approach
- Error handling
- Console menu design
- Test structure

Follow the rules. Make it work. Keep it simple.

---

## Success (Phase 0)

- [ ] Create user via console
- [ ] Complete profile → user becomes ACTIVE
- [ ] Find candidates (filtered correctly)
- [ ] Like a user
- [ ] Mutual like creates match
- [ ] View matches
- [ ] Data persists between app restarts
- [ ] Zero framework imports in `core/`

---

## What's NOT in Phase 0

- No HTTP/REST
- No Spring
- No messaging between users
- No notifications
- No authentication

Add these later. First make the core work.

---

## Summary

Users like each other. Mutual likes create matches. Build that first.