# Plan P1-A — Realistic Multi-Photo Dev Data

> **Phase:** 1 — Finish & Polish (current priority)
> **Effort:** Small (~10–25 lines across 2 files including regression test)
> **Blocked by:** Nothing — fully independent
> **Source-verified:** 2026-03-13 against actual code

---

## Verification & Progress (updated 2026-03-13)

- [x] Plan validated against repository source.
- [x] `DevDataSeeder.build(...)` confirmed as the correct photo-assignment point.
- [x] Seeder photo assignment updated from single Dicebear URL to deterministic multi-photo `randomuser.me` URLs.
- [x] Regression test added for seeded sentinel user photo URLs.
- [x] Full quality gate run (`mvn spotless:apply verify`) completed and recorded.

Verification result recorded (2026-03-13): `BUILD SUCCESS`, `Tests run: 1098, Failures: 0, Errors: 0, Skipped: 2`.

---

## Goal

Give each seeded test user 2–3 realistic-looking person photos instead of the current
single cartoon avatar. This makes the matching screen, standouts, and profile view
actually look like a dating app during development.

---

## Current State

**File:** `src/main/java/datingapp/storage/DevDataSeeder.java`
**Current code location:** inside the `build()` helper method (around line ~1030 in current file):

```java
user.addPhotoUrl("https://api.dicebear.com/9.x/avataaars/png?seed=" + id);
```

One cartoon/avatar-style image per user. The storage layer already supports multiple
photos — `JdbiUserStorage.saveUserPhotos(UUID, List<String>)` takes a list, and the
`user_photos` table has a `position` column for ordering. The `build()` method has
`gender` as a parameter at line ~933, which is needed to pick gender-appropriate photos.

**Idempotency:** The seeder checks for `SEED_SENTINEL_ID` (`11111111-1111-1111-1111-000000000001`)
at lines 69–75 before doing anything. This plan does not affect that check.

---

## Change Required

### File: `src/main/java/datingapp/storage/DevDataSeeder.java`

**Before (inside `build(...)`):**
```java
user.addPhotoUrl("https://api.dicebear.com/9.x/avataaars/png?seed=" + id);
```

**After (replace that single-photo line with the following block):**
```java
// Assign 3 deterministic, gender-appropriate portrait photos per user.
// randomuser.me serves stable portraits indexed 0–99 by gender path.
// The index math below keeps values in the safe 5–94 range.
int photoBase = (int) Math.floorMod(id.getLeastSignificantBits(), 85) + 5;
String portraitGender = switch (gender) {
    case MALE   -> "men";
    case FEMALE -> "women";
    case OTHER  -> (id.getLeastSignificantBits() % 2 == 0) ? "men" : "women";
};
user.addPhotoUrl("https://randomuser.me/api/portraits/" + portraitGender + "/" + photoBase + ".jpg");
user.addPhotoUrl("https://randomuser.me/api/portraits/" + portraitGender + "/" + ((photoBase + 12) % 90 + 5) + ".jpg");
user.addPhotoUrl("https://randomuser.me/api/portraits/" + portraitGender + "/" + ((photoBase + 27) % 90 + 5) + ".jpg");
```

This is the only production-code change in `DevDataSeeder`; add one regression test file update as described below.

---

## How It Works

- `id.getLeastSignificantBits()` returns a `long` derived from the UUID, which is stable
  per user across all runs. Combined with `Math.floorMod(..., 85) + 5`, this maps every
  UUID to a consistent non-negative index in the range 5–89.
- The `+12` and `+27` offsets produce two additional distinct indices for the same user.
- `% 90 + 5` keeps the derived offsets in the 5–94 range that `randomuser.me` reliably
  serves. Indices outside 0–99 return HTTP 404.
- `randomuser.me` portrait URLs require no API key, no account, and no rate limit for
  individual portrait fetches. They are stable — the same URL always returns the same image.
- `user.addPhotoUrl()` appends to the user's internal photo list. `JdbiUserStorage.save()`
  → `saveUserPhotos()` persists the full list with `position` ordering. No change to the
  storage layer is required.

---

## Files to Modify

| File                                                                         | Change                                           |
|------------------------------------------------------------------------------|--------------------------------------------------|
| `src/main/java/datingapp/storage/DevDataSeeder.java`                         | Replace single Dicebear URL with 3 portrait URLs |
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` | Add regression test for seeded photo URLs        |

---

## Test Requirements

1. Find the existing test(s) that cover `DevDataSeeder`. Search for `"DevDataSeeder"` in
   `src/test/java/`. Likely candidates: `SchemaInitializerTest`, storage integration tests.

2. Add an assertion that verifies the sentinel user has **at least 2** photo URLs after seeding:
   ```java
   List<String> photos = userStorage.loadUserPhotos(DevDataSeeder.SEED_SENTINEL_ID);
   assertThat(photos).hasSizeGreaterThanOrEqualTo(2);
   assertThat(photos).allMatch(url -> url.startsWith("https://randomuser.me/"));
   ```
   (`SEED_SENTINEL_ID` may need to be package-accessible — check its visibility modifier.
   If it is `private`, use the first male user UUID directly: `UUID.fromString("11111111-1111-1111-1111-000000000001")`.)

3. Run `mvn verify` — the JaCoCo 60% line coverage gate must pass. Run `mvn spotless:apply`
   before `mvn verify`.

---

## Gotchas

- **Use `Math.floorMod` for UUID-based indexing.**
  Prefer:
  ```java
  int photoBase = (int) Math.floorMod(id.getLeastSignificantBits(), 85) + 5;
  ```
  This guarantees a non-negative index without `Math.abs` edge cases.

- **Do not remove the `addPhotoUrl()` method** from `User.java`. It is used correctly here
  and in tests.

- **Idempotency is not broken** by this change. The sentinel check is on whether the user
  *exists*, not on their photo count. If the seeder has already run (sentinel present),
  the entire `seed()` method returns early — photos are never re-assigned.

- **The seeder `build()` method does NOT call `userStorage.saveUserPhotos()` directly.**
  It calls `user.addPhotoUrl()` on the User object, then `userStorage.save(user)` which
  internally calls `saveUserPhotos()`. Follow the existing pattern — do not add a
  separate `saveUserPhotos` call.

---

## Verification After Implementation

Run `mvn javafx:run`, log in as any user, open the Browse/Matching screen. Profile cards
should now show real person portraits instead of cartoon avatars.
