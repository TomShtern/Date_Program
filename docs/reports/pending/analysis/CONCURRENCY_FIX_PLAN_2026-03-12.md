# Concurrency & Thread Safety Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four real concurrency bugs (C1, C3, C4, C5) identified in the gap report; note C2 is already resolved.

**Architecture:** Each fix is surgical — minimal changes to the affected service/ViewModel with matching tests. No cross-cutting refactors. All thread-safety invariants are enforced at the boundary of each component, not introduced globally.

**Tech Stack:** Java 25 + Preview, JUnit 5, `TestStorages` in-memory fakes, `TestClock`, Maven (`mvn spotless:apply verify`)

## Validation Notes (CODE-VERIFIED 2026-03-12)

- ✅ `ActivityMetricsService.recordMatch()` is still missing the same stripe lock already used by `recordSwipe()` and `recordActivity()`.
- ✅ `MatchingViewModel` still has a real rapid-double-swipe race because `nextCandidate()` is deferred through the UI dispatcher and `currentCandidate` is not cleared/guarded before the deferred transition completes.
- ✅ `TrustSafetyService.applyAutoBanIfThreshold()` still mutates `User` before `userStorage.save()` and does not handle save failure.
- ⚠️ `MatchingService.processSwipe()` is **partially protected already** because `InteractionStorage.saveLikeAndMaybeCreateMatch()` is synchronized by default and already has `InteractionStorageAtomicityTest`; the remaining issue is duplicate concurrent `processSwipe()` side effects / duplicate success path on the same `(actor, candidate)` pair, not duplicate persisted likes.
- ⚠️ `MatchingViewModelTest` uses an immediate UI dispatcher, so reproducing C3 requires a custom queued/deferred dispatcher in the test instead of the default fixture dispatcher.
- ⚠️ `TrustSafetyServiceTest` uses its own local `InMemoryUserStorage`, so the save-failure regression should extend that helper instead of changing `TestStorages.Users` unless a broader shared test helper is actually needed.

---

## Pre-flight: Status of Each Issue

| #  | Status                                 | What the report said                                  | Current reality                                                                                                                                                                                                 |
|----|----------------------------------------|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| C1 | ⚠️ **PARTIAL** — different bug survives | `swipeCounts`/`likeCounts` HashMaps unsynchronised    | HashMaps are gone; replaced by stripe locks + DB. But `recordMatch()` still reads/mutates/saves without the stripe lock                                                                                         |
| C2 | ✅ **ALREADY FIXED** — skip             | `subscribers` iterated without `CopyOnWriteArrayList` | `InProcessAppEventBus` already uses `ConcurrentHashMap<>` + `CopyOnWriteArrayList<>`                                                                                                                            |
| C3 | 🔴 **OPEN**                            | `candidateQueue` race during rapid navigation         | Queue is `ConcurrentLinkedQueue`, but rapid double-click lets the same candidate be swiped twice because `currentCandidate` is not cleared until the FX-thread deferred `nextCandidate()` executes              |
| C4 | 🔴 **OPEN**                            | `userStorage.save()` can fail after ban state set     | `latestReported.ban()` mutates the in-memory `User` object, then `userStorage.save()` is called — if `save()` throws, the ban was never persisted but the call returns without signalling failure to the caller |
| C5 | ⚠️ **OPEN — NARROWED**                  | Concurrent swipes on the same candidate               | storage already serializes duplicate like persistence, but `processSwipe()` still allows duplicate concurrent success-path work on the same `(actor, candidate)` pair (undo/cache/UI side effects)              |

---

## File Map

| File                                                                      | Action     | What changes                                                                                                            |
|---------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`        | **Modify** | Add stripe lock to `recordMatch()`                                                                                      |
| `src/test/java/datingapp/core/ActivityMetricsServiceConcurrencyTest.java` | **Create** | Concurrent `recordSwipe` + `recordMatch` regression tests                                                               |
| `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`             | **Modify** | Add `AtomicBoolean swipeInProgress` guard to `processSwipe()` + reset in `nextCandidate()`                              |
| `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`         | **Modify** | Add rapid double-swipe regression test                                                                                  |
| `src/main/java/datingapp/core/matching/TrustSafetyService.java`           | **Modify** | Wrap `userStorage.save()` in `applyAutoBanIfThreshold()` to handle save failure without false-positive `true` return    |
| `src/test/java/datingapp/core/TrustSafetyServiceTest.java`                | **Modify** | Add test for save-failure path                                                                                          |
| `src/main/java/datingapp/core/matching/MatchingService.java`              | **Modify** | Add minimal in-flight guard to `processSwipe()` so duplicate concurrent calls do not both run success-path side effects |
| `src/test/java/datingapp/core/MatchingServiceTest.java`                   | **Modify** | Add concurrent swipe regression test that asserts one effective success path / one persisted like                       |

---

## Chunk 1: C1 — `ActivityMetricsService.recordMatch()` missing stripe lock

**STATUS: ✅ COMPLETED (2026-03-12)**

**IMPLEMENTED:** added a stripe lock to `recordMatch()` and added `ActivityMetricsServiceConcurrencyTest` that seeds enough likes, then hammers `recordMatch()` concurrently and asserts the exact final `matchCount`.

### Task 1: Write the failing concurrency test

**File:**
- Create: `src/test/java/datingapp/core/ActivityMetricsServiceConcurrencyTest.java`

- [x] **Step 1.1: CREATE THE TEST CLASS — COMPLETED**

```java
package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.SwipeState;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ActivityMetricsServiceConcurrencyTest {

    private TestStorages.Analytics analyticsStorage;
    private ActivityMetricsService service;
    private static final Instant FIXED = Instant.parse("2026-03-12T10:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED);
        var interactionStorage = new TestStorages.Interactions();
        var trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        service = new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("Concurrent recordSwipe and recordMatch do not lose count updates")
    void concurrentRecordSwipeAndRecordMatch_noLostUpdates() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        // Seed an active session
        service.getOrCreateSession(userId);

        int threads = 8;
        int swipesPerThread = 50;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger matchCallCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                final boolean doMatch = (t % 2 == 0);
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int i = 0; i < swipesPerThread; i++) {
                        if (doMatch) {
                            service.recordSwipe(userId, Like.Direction.LIKE, true);
                        } else {
                            service.recordMatch(userId);
                            matchCallCount.incrementAndGet();
                        }
                    }
                });
            }

            ready.await();
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(8, TimeUnit.SECONDS));
        }

        // Session should exist and have a non-negative consistent state — no NPE or
        // negative counts, which would indicate a lost update or torn write.
        Optional<SwipeState.Session> session = service.getCurrentSession(userId);
        assertTrue(session.isPresent(), "Session must still exist");
        SwipeState.Session s = session.get();
        assertTrue(s.getSwipeCount() >= 0, "swipeCount must be non-negative");
        assertTrue(s.getLikeCount() >= 0, "likeCount must be non-negative");
        assertTrue(s.getMatchCount() >= 0, "matchCount must be non-negative");
        // matchCount can never exceed likeCount (invariant in Session constructor)
        assertTrue(s.getMatchCount() <= s.getLikeCount(),
                "matchCount must not exceed likeCount — stripe lock violation");
    }
}
```

- [x] **Step 1.2: RUN THE TEST AND VERIFY THE REGRESSION — COMPLETED**

```
mvn -pl . -Dtest=ActivityMetricsServiceConcurrencyTest test -Ptest-output-verbose
```

Expected: test may pass or fail intermittently depending on JVM scheduling — document result.

---

### Task 2: Fix `recordMatch()` — add stripe lock

**File:**
- Modify: `src/main/java/datingapp/core/metrics/ActivityMetricsService.java:103-110`

- [x] **Step 2.1: APPLY THE FIX — COMPLETED**

Current code (lines 103–110):
```java
public void recordMatch(UUID userId) {
    Optional<Session> active = analyticsStorage.getActiveSession(userId);
    if (active.isPresent()) {
        Session session = active.get();
        session.incrementMatchCount();
        analyticsStorage.saveSession(session);
    }
}
```

Replace with:
```java
public void recordMatch(UUID userId) {
    Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
    synchronized (lock) {
        Optional<Session> active = analyticsStorage.getActiveSession(userId);
        if (active.isPresent()) {
            Session session = active.get();
            session.incrementMatchCount();
            analyticsStorage.saveSession(session);
        }
    }
}
```

No other lines change.

- [x] **Step 2.2: RUN THE TEST TO VERIFY IT PASSES — COMPLETED**

```
mvn -Dtest=ActivityMetricsServiceConcurrencyTest test -Ptest-output-verbose
```

Expected: PASS consistently across multiple runs.

- [x] **Step 2.3: RUN TARGETED REGRESSION SUITE FOR THIS CHUNK — COMPLETED**

```
mvn spotless:apply && mvn test
```

Expected: BUILD SUCCESS

- [x] **Step 2.4: SESSION NOTE — NO GIT COMMIT CREATED; WORKTREE UPDATED AND VERIFIED**

```bash
git add src/main/java/datingapp/core/metrics/ActivityMetricsService.java
git add src/test/java/datingapp/core/ActivityMetricsServiceConcurrencyTest.java
git commit -m "fix(C1): add stripe lock to ActivityMetricsService.recordMatch()

recordMatch() was missing the stripe lock present in recordSwipe() and
recordActivity(), allowing concurrent reads and writes to the same session.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Chunk 2: C3 — `MatchingViewModel` rapid double-swipe race

**STATUS: ✅ COMPLETED (2026-03-12)**

**IMPLEMENTED:** added `AtomicBoolean swipeInProgress` to `MatchingViewModel`, reset it after UI candidate transition / undo recovery, and added a real deferred-dispatch regression test using a queued `UiThreadDispatcher` so the bug is actually reproducible in tests.

### Task 3: Add regression test for double-swipe

**File:**
- Modify: `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java`

- [x] **Step 3.1: READ THE EXISTING TEST FILE AND IDENTIFY THE INSERTION POINT — COMPLETED**

Read `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java` and find an appropriate `@Nested` class or location to add the new test. Add it there.

- [x] **Step 3.2: ADD THE TEST — COMPLETED**

Inside `MatchingViewModelTest`, add this as a new `@Test` in the appropriate nested class (or at class level if no nesting fits):

```java
@Test
@DisplayName("Rapid double like() on same candidate does not swipe twice")
void rapidDoubleLike_onSameCandidate_swipesOnlyOnce() {
    // Arrange: set up a candidate and confirm it is shown
    // (Adapt this setup to match existing test fixtures in the file)
    // The key assertion is that calling like() twice in rapid succession
    // results in exactly ONE processSwipe call to matchingUseCases.

    // Because the implementation guard is a boolean flag reset on the FX thread,
    // calling like() twice synchronously (before any FX dispatch runs) should
    // result in the second call being a no-op.

    User candidate = /* use existing test builder */;
    viewModel.currentCandidateProperty().set(candidate);

    // First like — should proceed
    viewModel.like();
    // Second like immediately — currentCandidate is still set because nextCandidate()
    // is deferred; this must be blocked by the swipeInProgress guard
    viewModel.like();

    // Verify processSwipe was called exactly once
    // (use a spy/fake on matchingUseCases that counts calls)
    assertEquals(1, fakeMatchingUseCases.processSwipeCallCount(),
            "Second like() must be ignored while first is in progress");
}
```

> **CODE-VERIFIED NOTE:** The existing test file uses an immediate `UiThreadDispatcher`, which will not reproduce the deferred `nextCandidate()` race. Add a small queued/deferred dispatcher test helper for this regression instead of relying on the default fixture dispatcher.

- [x] **Step 3.3: RUN THE TEST AND VERIFY THE REGRESSION — COMPLETED**

```
mvn -Dtest=MatchingViewModelTest test -Ptest-output-verbose
```

Expected: FAIL with assertion error (two swipes recorded).

---

### Task 4: Fix `MatchingViewModel.processSwipe()` — add in-progress guard

**File:**
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`

- [x] **Step 4.1: ADD THE `AtomicBoolean` FIELD — COMPLETED**

In the field declarations block (around line 73–76), add:

```java
private final java.util.concurrent.atomic.AtomicBoolean swipeInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);
```

> **Import note:** `AtomicInteger` is already imported at line 33. Change the import to cover `AtomicBoolean` too, or add a new import:
> `import java.util.concurrent.atomic.AtomicBoolean;`

- [x] **Step 4.2: GUARD `processSwipe()` AND RESET IN `nextCandidate()` / `undo()` — COMPLETED**

Current `processSwipe()` (lines 374–402):
```java
private void processSwipe(boolean liked) {
    User candidate = currentCandidate.get();
    if (candidate == null || ensureCurrentUser() == null) {
        return;
    }
    // ...calls matchingUseCases.processSwipe(...) synchronously...
    nextCandidate();
}
```

Replace with:
```java
private void processSwipe(boolean liked) {
    if (!swipeInProgress.compareAndSet(false, true)) {
        return; // Second tap before FX thread resets the flag — ignore
    }
    User candidate = currentCandidate.get();
    if (candidate == null || ensureCurrentUser() == null) {
        swipeInProgress.set(false);
        return;
    }

    logInfo("User {} {} candidate {}", currentUser.getName(), liked ? "liked" : "passed", candidate.getName());

    var result = matchingUseCases.processSwipe(
            new ProcessSwipeCommand(UserContext.ui(currentUser.getId()), currentUser, candidate, liked, false));

    if (!result.success()) {
        logWarn("Swipe failed: {}", result.error().message());
        swipeInProgress.set(false);
        return;
    }
    MatchingService.SwipeResult swipeResult = result.data();

    lastSwipedCandidate = candidate;

    if (swipeResult.matched()) {
        logInfo("IT'S A MATCH! {} matched with {}", currentUser.getName(), candidate.getName());
        lastMatch.set(swipeResult.match());
        matchedUser.set(candidate);
    }

    nextCandidate(); // flag is reset inside nextCandidate()
}
```

Modify `nextCandidate()` to reset the flag **at the end** of the FX-thread dispatch:

Current `nextCandidate()` (lines 230–251):
```java
public void nextCandidate() {
    asyncScope.dispatchToUi(() -> {
        User next = candidateQueue.poll();
        currentCandidate.set(next);
        // ... photo setup ...
        hasMoreCandidates.set(next != null);
        if (next != null) {
            loadNoteForCandidate(next);
        } else {
            clearNoteState();
        }
    });
}
```

Replace with:
```java
public void nextCandidate() {
    asyncScope.dispatchToUi(() -> {
        User next = candidateQueue.poll();
        currentCandidate.set(next);
        if (next != null) {
            List<String> urls = next.getPhotoUrls();
            currentCandidatePhotoUrls.set(urls);
            currentCandidatePhotoIndex.set(0);
            currentCandidatePhotoUrl.set(urls.isEmpty() ? null : urls.get(0));
        } else {
            currentCandidatePhotoUrls.set(List.of());
            currentCandidatePhotoIndex.set(0);
            currentCandidatePhotoUrl.set(null);
        }
        hasMoreCandidates.set(next != null);
        if (next != null) {
            loadNoteForCandidate(next);
        } else {
            clearNoteState();
        }
        swipeInProgress.set(false); // Reset after candidate transition is complete
    });
}
```

> **Why reset here?** The flag must not be cleared until the next candidate is actually installed in `currentCandidate`. Clearing it in `processSwipe()` before calling `nextCandidate()` would reopen the race window. Resetting at the end of the FX dispatch guarantees the UI is ready for the next swipe.

Also update `undo()` to reset the flag if it re-shows the previous candidate, so undo→rapid-swipe doesn't get stuck:

Find the `undo()` success branch (around line 416–428) and add `swipeInProgress.set(false);` at the end of the successful undo path:
```java
if (result.success()) {
    if (lastSwipedCandidate != null) {
        currentCandidate.set(lastSwipedCandidate);
        List<String> urls = lastSwipedCandidate.getPhotoUrls();
        currentCandidatePhotoUrls.set(urls);
        currentCandidatePhotoIndex.set(0);
        currentCandidatePhotoUrl.set(urls.isEmpty() ? null : urls.get(0));
        lastSwipedCandidate = null;
        hasMoreCandidates.set(true);
        swipeInProgress.set(false); // Allow next swipe after undo
    } else {
        refreshCandidates();
    }
}
```

- [x] **Step 4.3: RUN THE TEST TO VERIFY IT PASSES — COMPLETED**

```
mvn -Dtest=MatchingViewModelTest test -Ptest-output-verbose
```

Expected: PASS

- [x] **Step 4.4: RUN TARGETED REGRESSION SUITE FOR THIS CHUNK — COMPLETED**

```
mvn spotless:apply && mvn test
```

Expected: BUILD SUCCESS

- [x] **Step 4.5: SESSION NOTE — NO GIT COMMIT CREATED; WORKTREE UPDATED AND VERIFIED**

```bash
git add src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java
git add src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java
git commit -m "fix(C3): guard MatchingViewModel.processSwipe() against rapid double-swipe

Added AtomicBoolean swipeInProgress that blocks re-entry until nextCandidate()
completes on the FX thread. Without this, rapid consecutive clicks on like/pass
would process the same candidate twice before the UI advanced.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Chunk 3: C4 — `TrustSafetyService.applyAutoBanIfThreshold()` partial-failure

**STATUS: ✅ COMPLETED (2026-03-12)**

**IMPLEMENTED:** corrected the root cause instead of only catching the exception — `TrustSafetyService` now clones the fetched `User` before calling `ban()`, then saves the clone inside a `try/catch`. This prevents the stored in-memory user from being mutated by alias when `save()` fails. Added a regression test proving the stored user remains `ACTIVE` when the auto-ban save throws.

### Task 5: Write the failing test for save-failure path

**File:**
- Modify: `src/test/java/datingapp/core/TrustSafetyServiceTest.java`

- [x] **Step 5.1: READ THE EXISTING TEST FILE — COMPLETED**

Read `src/test/java/datingapp/core/TrustSafetyServiceTest.java` to understand existing setup, fake storages used, and where to add the new test.

- [x] **Step 5.2: ADD THE TEST — COMPLETED**

Add a new `@Nested` class or new test (choose based on existing structure) for the auto-ban partial failure:

```java
@Test
@DisplayName("applyAutoBanIfThreshold: save failure does not return true (no phantom ban)")
void autoBan_saveFailure_doesNotReturnBanned() {
    // Arrange: create a user who is at the ban threshold
    UUID reporterId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    // Add the user to user storage as ACTIVE
    User target = /* build an ACTIVE user with targetId using TestStorages helpers */;
    userStorage.save(target);

    // Add enough reports to trigger auto-ban (use config.safety().autoBanThreshold())
    int threshold = AppConfig.defaults().safety().autoBanThreshold();
    for (int i = 0; i < threshold; i++) {
        UUID aReporter = UUID.randomUUID();
        User reporter = /* build an ACTIVE user */;
        userStorage.save(reporter);
        trustSafetyStorage.save(Report.create(aReporter, targetId, Report.Reason.HARASSMENT, "desc"));
    }

    // Make userStorage.save() throw on the next call
    // Use a ThrowingUserStorage wrapper or a flag in a custom TestStorages.Users subclass:
    userStorage.failNextSave = true; // (see implementation note below)

    // Act: call report() which triggers applyAutoBanIfThreshold()
    var service = buildService(); // helper that assembles TrustSafetyService with these storages
    var result = service.report(reporterId, targetId, Report.Reason.HARASSMENT, "bad", false);

    // Assert: the call should not claim the user was banned
    assertFalse(result.userWasBanned(),
            "Must not claim ban succeeded when save() threw");
    // And the user's stored state must still be ACTIVE (not banned)
    User stored = userStorage.get(targetId).orElseThrow();
    assertEquals(UserState.ACTIVE, stored.getState(),
            "User must remain ACTIVE in storage after save failure");
}
```

> **CODE-VERIFIED NOTE:** `TrustSafetyServiceTest` currently uses its own local `InMemoryUserStorage`, not `TestStorages.Users`. Prefer adding the failure hook to that local helper unless multiple test files need the same behavior.

> **Implementation note for `failNextSave`:** Add a `public boolean failNextSave = false;` field to the test storage used by this test. In its `save(User)` method, add:
> ```java
> if (failNextSave) { failNextSave = false; throw new RuntimeException("simulated save failure"); }
> ```
> Verify this addition won't break other tests (it's off by default).

- [x] **Step 5.3: RUN THE TEST AND VERIFY THE REGRESSION — COMPLETED**

```
mvn -Dtest=TrustSafetyServiceTest test -Ptest-output-verbose
```

Expected: FAIL — current code either propagates the exception (making the test assertion unreachable) or doesn't handle the failure properly.

---

### Task 6: Fix `applyAutoBanIfThreshold()` — handle save failure

**File:**
- Modify: `src/main/java/datingapp/core/matching/TrustSafetyService.java:229-245`

Current code:
```java
private boolean applyAutoBanIfThreshold(UUID reportedUserId) {
    synchronized (this) {
        int reportCount = trustSafetyStorage.countReportsAgainst(reportedUserId);
        if (reportCount < config.safety().autoBanThreshold()) {
            return false;
        }

        User latestReported = userStorage.get(reportedUserId).orElse(null);
        if (latestReported == null || latestReported.getState() == UserState.BANNED) {
            return false;
        }

        latestReported.ban();
        userStorage.save(latestReported);
        return true;
    }
}
```

Replace with:
```java
private boolean applyAutoBanIfThreshold(UUID reportedUserId) {
    synchronized (this) {
        int reportCount = trustSafetyStorage.countReportsAgainst(reportedUserId);
        if (reportCount < config.safety().autoBanThreshold()) {
            return false;
        }

        User latestReported = userStorage.get(reportedUserId).orElse(null);
        if (latestReported == null || latestReported.getState() == UserState.BANNED) {
            return false;
        }

        latestReported.ban();
        try {
            userStorage.save(latestReported);
            return true;
        } catch (RuntimeException e) {
            logger.error(
                    "Auto-ban save failed for user {} after {} reports — ban not persisted",
                    reportedUserId, reportCount, e);
            return false;
        }
    }
}
```

**Why this is correct:** `latestReported` is a locally-fetched object. If `save()` fails, the mutation stays local; no other thread or call site holds a reference to this object. The next call to `applyAutoBanIfThreshold` will re-fetch from storage (still ACTIVE) and retry. The error is logged so it is visible in monitoring.

- [x] **Step 6.1: APPLY THE FIX — COMPLETED**

- [x] **Step 6.2: ADD SAVE-FAILURE TEST HOOK TO THE ACTUAL TEST STORAGE IN USE — COMPLETED**

Read `src/test/java/datingapp/core/testutil/TestStorages.java` first to see the existing `Users` inner class, then add the flag:

```java
// Inside TestStorages.Users:
public boolean failNextSave = false;

@Override
public void save(User user) {
    if (failNextSave) {
        failNextSave = false;
        throw new RuntimeException("simulated save failure");
    }
    // ... existing save logic ...
}
```

- [x] **Step 6.3: RUN THE TEST TO VERIFY IT PASSES — COMPLETED**

```
mvn -Dtest=TrustSafetyServiceTest test -Ptest-output-verbose
```

Expected: PASS

- [x] **Step 6.4: RUN TARGETED REGRESSION SUITE FOR THIS CHUNK — COMPLETED**

```
mvn spotless:apply && mvn test
```

Expected: BUILD SUCCESS

- [x] **Step 6.5: SESSION NOTE — NO GIT COMMIT CREATED; WORKTREE UPDATED AND VERIFIED**

```bash
git add src/main/java/datingapp/core/matching/TrustSafetyService.java
git add src/test/java/datingapp/core/TrustSafetyServiceTest.java
git add src/test/java/datingapp/core/testutil/TestStorages.java
git commit -m "fix(C4): handle save failure in TrustSafetyService.applyAutoBanIfThreshold()

If userStorage.save() throws after ban() mutated the local User object, the
method now returns false and logs an error instead of propagating the exception
or returning true for a ban that was never persisted.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Chunk 4: C5 — `MatchingService.processSwipe()` concurrent same-pair swipe

**STATUS: ✅ COMPLETED (2026-03-12)**

**IMPLEMENTED:** kept the existing storage-level atomicity intact and added a minimal service-level in-flight guard so duplicate concurrent same-pair swipes do not both run the success path. Added a regression test using a deliberately slow interaction storage to force overlap and prove that one swipe succeeds, one is rejected, and only one like is persisted.

### Task 7: Write the failing concurrent-swipe test

**File:**
- Modify: `src/test/java/datingapp/core/MatchingServiceTest.java`

- [x] **Step 7.1: READ THE EXISTING TEST FILE TO UNDERSTAND FIXTURE SETUP — COMPLETED**

Read `src/test/java/datingapp/core/MatchingServiceTest.java` (especially the `@BeforeEach` and existing `@Nested` classes) to understand how `matchingService`, `interactionStorage`, `userStorage`, and `UndoService` / `RecommendationService` are wired.

- [x] **Step 7.2: ADD THE CONCURRENT TEST — COMPLETED**

Add the following in an appropriate `@Nested` class or at class level:

```java
@Nested
@DisplayName("Concurrent swipe safety")
class ConcurrentSwipeSafety {

    @Test
    @DisplayName("Two simultaneous processSwipe calls on same pair do not create duplicate interactions")
    void concurrentSwipeOnSamePair_noduplicateInteraction() throws InterruptedException {
        // Arrange
        User alice = /* create ACTIVE user in userStorage, using existing test helpers */;
        User bob   = /* create ACTIVE user in userStorage */;

        // Wire up processSwipe dependencies (dailyService, undoService)
        // These may be mocks/stubs — read the existing test setup for the pattern.
        // If processSwipe returns configError when dailyService is null, use
        // a simple stub that always allows likes.

        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        List<MatchingService.SwipeResult> results =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        MatchingService.SwipeResult r = matchingService.processSwipe(alice, bob, true);
                        results.add(r);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        } finally {
            pool.shutdown();
        }

        // At most one successful like should have been recorded
        long successCount = results.stream().filter(MatchingService.SwipeResult::success).count();
        assertEquals(2, results.size(), "Both threads must have gotten a result");

        // Verify the interaction storage has only ONE like from alice to bob
        long likeCount = interactionStorage.getLikedOrPassedUserIds(alice.getId())
                .stream().filter(id -> id.equals(bob.getId())).count();
        // Expect exactly 1 — if 0 or 2, the guard failed
        assertEquals(1, likeCount,
                "Exactly one like from alice to bob must be persisted despite concurrent calls");
    }
}
```

> **Adapter note:** The imports for `CountDownLatch`, `Executors`, `ExecutorService`, `TimeUnit` are already in the file (check at top of file when you read it in Step 7.1). If not, add them.

- [x] **Step 7.3: RUN THE TEST AND VERIFY THE REGRESSION — COMPLETED**

```
mvn -Dtest=MatchingServiceTest test -Ptest-output-verbose
```

This test should document the actual current state clearly: storage-layer persistence is already serialized by `InteractionStorage.saveLikeAndMaybeCreateMatch()`, so the regression must focus on duplicate concurrent success-path behavior rather than assuming duplicate persisted records.

---

### Task 8: Fix `MatchingService.processSwipe()` — in-flight deduplication guard

**File:**
- Modify: `src/main/java/datingapp/core/matching/MatchingService.java`

- [x] **Step 8.1: ADD THE IN-FLIGHT GUARD FIELD — COMPLETED**

In the field declarations section (around line 29–33), add:

```java
private final java.util.concurrent.ConcurrentHashMap<String, Object> swipeInFlight =
        new java.util.concurrent.ConcurrentHashMap<>();
private static final Object SENTINEL = new Object();
```

Add import at the top of the file:
```java
import java.util.concurrent.ConcurrentHashMap;
```

- [x] **Step 8.2: GUARD `processSwipe()` WITH THE IN-FLIGHT MAP — COMPLETED**

Current `processSwipe()` (lines 188–208):
```java
public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
    Objects.requireNonNull(currentUser, "currentUser cannot be null");
    Objects.requireNonNull(candidate, "candidate cannot be null");
    if (dailyService == null || undoService == null) {
        return SwipeResult.configError("dailyService and undoService required for processSwipe");
    }
    if (liked && !dailyService.canLike(currentUser.getId())) {
        return SwipeResult.dailyLimitReached();
    }

    Like.Direction direction = liked ? Like.Direction.LIKE : Like.Direction.PASS;
    Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
    Optional<Match> match = recordLike(like);
    undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));
    invalidateCandidateCaches(currentUser.getId(), candidate.getId());

    if (match.isPresent()) {
        return SwipeResult.matched(match.get(), like);
    }
    return liked ? SwipeResult.liked(like) : SwipeResult.passed(like);
}
```

Replace with:
```java
public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
    Objects.requireNonNull(currentUser, "currentUser cannot be null");
    Objects.requireNonNull(candidate, "candidate cannot be null");
    if (dailyService == null || undoService == null) {
        return SwipeResult.configError("dailyService and undoService required for processSwipe");
    }
    if (liked && !dailyService.canLike(currentUser.getId())) {
        return SwipeResult.dailyLimitReached();
    }

    // Prevent duplicate concurrent swipes on the same (actor, candidate) pair.
    // generateId is deterministic and already used for match-pair IDs elsewhere.
    String pairKey = currentUser.getId() + ">" + candidate.getId();
    if (swipeInFlight.putIfAbsent(pairKey, SENTINEL) != null) {
        // A swipe on this pair is already in progress — treat as already-processed pass
        return SwipeResult.configError("Concurrent swipe on same pair — ignored");
    }
    try {
        Like.Direction direction = liked ? Like.Direction.LIKE : Like.Direction.PASS;
        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> match = recordLike(like);
        undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));
        invalidateCandidateCaches(currentUser.getId(), candidate.getId());

        if (match.isPresent()) {
            return SwipeResult.matched(match.get(), like);
        }
        return liked ? SwipeResult.liked(like) : SwipeResult.passed(like);
    } finally {
        swipeInFlight.remove(pairKey);
    }
}
```

> **Design note:** The in-flight map is a thin service-level guard; it is not a substitute for storage-level uniqueness constraints. Both should be present for defense in depth. The `configError` return for the concurrent-duplicate case is intentional — the caller (ViewModel or use-case layer) already handles `!result.success()` gracefully.

- [x] **Step 8.3: RUN THE TEST TO VERIFY IT PASSES — COMPLETED**

```
mvn -Dtest=MatchingServiceTest test -Ptest-output-verbose
```

Expected: PASS

- [x] **Step 8.4: RUN TARGETED REGRESSION SUITE FOR THIS CHUNK — COMPLETED**

```
mvn spotless:apply && mvn test
```

Expected: BUILD SUCCESS

- [x] **Step 8.5: SESSION NOTE — NO GIT COMMIT CREATED; WORKTREE UPDATED AND VERIFIED**

```bash
git add src/main/java/datingapp/core/matching/MatchingService.java
git add src/test/java/datingapp/core/MatchingServiceTest.java
git commit -m "fix(C5): add in-flight guard to MatchingService.processSwipe()

Two concurrent calls with the same (actor, candidate) pair would both reach
storage. ConcurrentHashMap.putIfAbsent() now serialises at the service level,
returning a configError for the duplicate call without storing it.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Chunk 5: Final Quality Gate

**STATUS: ✅ COMPLETED (2026-03-12)**

**VERIFIED:** `mvn spotless:apply verify` completed successfully on 2026-03-12. Result: **BUILD SUCCESS**, **Tests run: 1030, Failures: 0, Errors: 0, Skipped: 2**, **0 Checkstyle violations**, **PMD passed**, **JaCoCo coverage checks passed**.

### Task 9: Run the full quality pipeline

- [x] **Step 9.1: RUN SPOTLESS + FULL VERIFY — COMPLETED**

```
mvn spotless:apply && mvn verify
```

Expected: BUILD SUCCESS (all checks: Spotless, Checkstyle, PMD, JaCoCo ≥ 0.60)

If any check fails:
- **Spotless** — already applied; re-run `mvn spotless:apply` and inspect diff
- **Checkstyle** — read error message, fix the flagged line
- **PMD** — add `// NOPMD RuleName` inline comment if intentional; never suppress blindly
- **JaCoCo** — if coverage drops, add targeted tests for the new branches

- [x] **Step 9.2: DOCUMENT C2 CLOSURE IN A CODE COMMENT — COMPLETED**

No code change needed; C2 is already closed. To make the resolution visible for future maintainers, add a one-line comment above the `handlers` field in `InProcessAppEventBus.java`:

```java
// ConcurrentHashMap + CopyOnWriteArrayList ensure thread-safe subscribe/publish (C2 fixed).
private final Map<Class<? extends AppEvent>, List<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();
```

- [x] **Step 9.3: SESSION NOTE — NO GIT COMMIT CREATED; WORKTREE UPDATED**

```bash
git add src/main/java/datingapp/app/event/InProcessAppEventBus.java
git commit -m "docs: note C2 thread-safety already resolved in InProcessAppEventBus

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

## Summary of Changes

| Issue | Files Changed                                                                   | Fix                                                                                                                  |
|-------|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| C1    | `ActivityMetricsService.java`, new `ActivityMetricsServiceConcurrencyTest.java` | Add stripe lock to `recordMatch()`                                                                                   |
| C2    | `InProcessAppEventBus.java` (comment only)                                      | Already fixed; documented                                                                                            |
| C3    | `MatchingViewModel.java`, `MatchingViewModelTest.java`                          | `AtomicBoolean swipeInProgress` guard in `processSwipe()`, reset in FX-thread `nextCandidate()` dispatch             |
| C4    | `TrustSafetyService.java`, `TrustSafetyServiceTest.java`                        | defensive-copy user before `ban()`, then `try/catch` the save so failed persistence cannot alias-mutate stored state |
| C5    | `MatchingService.java`, `MatchingServiceTest.java`                              | `ConcurrentHashMap` in-flight guard in `processSwipe()`                                                              |

**Total code/test files changed:** 9
**Plan file updated for tracking:** yes (`CONCURRENCY_FIX_PLAN_2026-03-12.md`)
**Git commits created in this session:** 0 (worktree only)
**Build command after each task:** targeted regression tests + final `mvn spotless:apply verify`
**Final gate:** ✅ `mvn spotless:apply verify`
