# Phase 1 Stabilization — Verified Implementation Plan (v2)

> **Source of truth**: Live source code, read file-by-file. Every claim below is verified.
> **Last verified**: 2026-04-13
> **Revision**: v2 — expanded with full ViewModel async audit (all 11 ViewModels)

---

## Complete ViewModel Async Audit

Exhaustive check of every ViewModel in `src/main/java/datingapp/ui/viewmodel/`:

| ViewModel            | Use-case calls                                                | Async discipline                                                                                                                  | Status   |
|----------------------|---------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|----------|
| ChatViewModel        | messagingUseCases, socialUseCases                             | All via `asyncScope.runLatest` / `runFireAndForget` / `runPolling`                                                                | ✅        |
| SocialViewModel      | socialUseCases                                                | `refresh()` via `runLatest`, friend request/notification via `runFireAndForget`                                                   | ✅        |
| StandoutsViewModel   | matchingUseCases                                              | `loadStandouts()` via `runLatest`, `markInteracted()` via `runFireAndForget`                                                      | ✅        |
| PreferencesViewModel | profileMutationUseCases                                       | `initialize()` / `savePreferences()` via `runLatest`, `updateThemeMode()` via `runFireAndForget`                                  | ✅        |
| ProfileViewModel     | profileMutationUseCases                                       | `save()`, photo ops — all via `runFireAndForget`                                                                                  | ✅        |
| DashboardViewModel   | (aggregation only)                                            | `refresh()` via `runLatest`, daily pick via `runFireAndForget`                                                                    | ✅        |
| MatchesViewModel     | matchingUseCases, socialUseCases                              | Production uses `AsyncExecutionMode.ASYNC` (ViewModelFactory L225); SYNC is test-only                                             | ✅        |
| MatchingViewModel    | matchingUseCases                                              | `refreshCandidates` ✅, notes ✅, block/report ✅ — but **`processSwipe()` and `undo()` are SYNC**                                   | ❌ Bug 1  |
| SafetyViewModel      | verificationUseCases, profileMutationUseCases, socialUseCases | `loadBlockedUsers` ✅, `unblockUser` ✅ — but **`startVerification()`, `confirmVerification()`, `deleteCurrentAccount()` are SYNC** | ❌ Bug 1b |
| LoginViewModel       | (handled separately)                                          | N/A                                                                                                                               | ✅        |
| NotesViewModel       | (delegates to noteDataAccess)                                 | All via `asyncScope`                                                                                                              | ✅        |

---

## ROADMAP Discrepancy — DevDataSeeder Photos

> [!IMPORTANT]
> The ROADMAP.md L101 says "Photos in DevDataSeeder ❌ NOT FIXED" — **this is stale**. The current code at `DevDataSeeder.build()` L1161–1174 assigns 3 deterministic `randomuser.me` portrait URLs per seed user. The P1-A task is already complete. No action needed.

---

## Bug 1 — `processSwipe()` and `undo()` block the FX thread

**Severity**: HIGH — synchronous database write on the UI thread every swipe

### Exact call chain (verified)

```
@FXML handleLike()
  → animateCardExit(true, this::performLike)          [MatchingController L382–408]
    → exit.setOnFinished(e → { onComplete.run(); })   [animation onFinished = FX thread]
      → performLike()                                  [L435–437, still on FX thread]
        → viewModel.like()                             [L382–383]
          → processSwipe(true, false)                  [L394–432]
            → matchingUseCases.processSwipe(...)       [L410–411, SYNCHRONOUS DB WRITE]
```

`handleSuperLike()` is worse — calls `viewModel.superLike()` without waiting for an animation (L546–551).

`undo()` (L434–461) is identical: `matchingUseCases.undoSwipe(...)` synchronous from `handleUndo()` → FX thread.

### Fix

Wrap the use-case call in `asyncScope.runFireAndForget()` and dispatch the result back to the FX thread:

#### [MODIFY] [MatchingViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java)

```java
// processSwipe — make async
private void processSwipe(boolean liked, boolean superLike) {
    if (!swipeInProgress.compareAndSet(false, true)) return;
    User candidate = currentCandidate.get();
    User user = ensureCurrentUser();
    if (candidate == null || user == null) { swipeInProgress.set(false); return; }

    String action = liked ? (superLike ? "super-liked" : "liked") : "passed";
    logInfo("User {} {} candidate {}", user.getName(), action, candidate.getName());

    asyncScope.runFireAndForget("process-swipe", () -> {
        var result = matchingUseCases.processSwipe(
                new ProcessSwipeCommand(UserContext.ui(user.getId()), user, candidate, liked, superLike, false));
        asyncScope.dispatchToUi(() -> applySwipeResult(result, candidate));
    });
}

private void applySwipeResult(UseCaseResult<SwipeOutcome> result, User candidate) {
    if (!result.success()) {
        logWarn("Swipe failed: {}", result.error().message());
        infoMessage.set(result.error().message());
        swipeInProgress.set(false);
        return;
    }
    SwipeOutcome swipeResult = result.data();
    lastSwipedCandidate = candidate;
    startUndoCountdown();
    if (swipeResult.matched()) {
        logInfo("IT'S A MATCH! {} matched with {}", currentUser.getName(), candidate.getName());
        lastMatch.set(swipeResult.match());
        matchedUser.set(candidate);
    }
    nextCandidate();
    swipeInProgress.set(false);
}
```

Same pattern for `undo()`:

```java
// undo — make async
public void undo() {
    User user = ensureCurrentUser();
    if (user == null) return;

    logInfo("Undoing swipe on {}",
            lastSwipedCandidate != null ? lastSwipedCandidate.getName() : "previous candidate");

    User savedCandidate = lastSwipedCandidate;
    asyncScope.runFireAndForget("undo-swipe", () -> {
        var result = matchingUseCases.undoSwipe(new UndoSwipeCommand(UserContext.ui(user.getId())));
        asyncScope.dispatchToUi(() -> applyUndoResult(result, savedCandidate));
    });
}

private void applyUndoResult(UseCaseResult<?> result, User previousCandidate) {
    if (result.success()) {
        stopUndoCountdown();
        if (previousCandidate != null) {
            currentCandidate.set(previousCandidate);
            photoCarousel.setPhotos(previousCandidate.getPhotoUrls());
            syncPhotoCarousel();
            loadNoteForCandidate(previousCandidate);
            lastSwipedCandidate = null;
            hasMoreCandidates.set(true);
            swipeInProgress.set(false);
            refreshCandidates();
        }
        return;
    }
    infoMessage.set(result.error().message());
}
```

---

## Bug 1b — `SafetyViewModel` verification/deletion methods block the FX thread

**Severity**: LOW — these actions are infrequent (user verifies/deletes once), but still incorrect

### The three sync-on-FX-thread methods

| Method                   | Line | What it does synchronously                                                                              |
|--------------------------|------|---------------------------------------------------------------------------------------------------------|
| `startVerification()`    | L231 | `verificationUseCases.startVerification(...)` — generates code, writes user state to DB                 |
| `confirmVerification()`  | L257 | `verificationUseCases.confirmVerification(...)` — verifies code, updates user verification status in DB |
| `deleteCurrentAccount()` | L284 | `mutationUseCases.deleteAccount(...)` — deletes user account from DB                                    |

### Fix

#### [MODIFY] [SafetyViewModel.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java)

Wrap each in `asyncScope.runFireAndForget()` with `dispatchToUi()` for the UI updates. Same pattern as Bug 1.

Example for `startVerification()`:
```java
public void startVerification() {
    if (isDisposed()) return;
    User currentUser = session.getCurrentUser();
    if (currentUser == null) { reportError(PROFILE_VERIFICATION_UNAVAILABLE); return; }
    if (verificationUseCases == null) { reportError(PROFILE_VERIFICATION_UNAVAILABLE); return; }

    VerificationMethod method = verificationMethod.get();
    String contact = verificationContact.get();

    asyncScope.runFireAndForget("start-verification", () -> {
        var startResult = verificationUseCases.startVerification(
                new StartVerificationCommand(UserContext.ui(currentUser.getId()), method, contact));
        asyncScope.dispatchToUi(() -> {
            if (!startResult.success()) {
                reportError(startResult.error().message());
                return;
            }
            session.setCurrentUser(startResult.data().user());
            syncVerificationFieldsFromSession();
            statusMessage.set("Verification code generated. Local/dev code: "
                    + startResult.data().generatedCode());
        });
    });
}
```

Same pattern for `confirmVerification()` and `deleteCurrentAccount()`.

---

## Bug 2 — `ImageCache.getImage()` blocks the FX thread in two paths

**Severity**: MEDIUM — can freeze the UI for hundreds of milliseconds when loading new candidate photos

### The two blocking paths (verified)

**Path A — uncached image (most common for first candidate)**
```java
// ImageCache.loadImage() L112:
Image image = new Image(path, width, height, true, true, false);
//                                                        ^^^^^ backgroundLoading=false → blocks calling thread
```

**Path B — in-flight preload (surprisingly bad)**
```java
// ImageCache.getOrLoadCachedImage() L143-145:
CompletableFuture<Image> inFlight = IN_FLIGHT_LOADS.putIfAbsent(key, newLoad);
if (inFlight != null) {
    return inFlight.join();  // ← BLOCKS FX THREAD waiting for preload executor
}
```

### Fix

#### [MODIFY] [ImageCache.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/ImageCache.java)

Add `getImageAsync()` method:
```java
/**
 * Loads or retrieves an image off the calling thread.
 * Calls {@code callback} on the JavaFX Application Thread with the loaded image.
 * Safe to call from the FX thread — never blocks the caller.
 */
public static void getImageAsync(String path, double width, double height, Consumer<Image> callback) {
    if (path == null || path.isBlank()) {
        Platform.runLater(() -> callback.accept(getDefaultAvatar(width, height)));
        return;
    }
    String key = cacheKey(path, width, height);
    synchronized (CACHE) {
        Image cached = CACHE.get(key);
        if (cached != null) {
            Platform.runLater(() -> callback.accept(cached));
            return;
        }
    }
    preload(path, width, height);
    CompletableFuture<Image> future = IN_FLIGHT_LOADS.get(key);
    if (future != null) {
        future.thenAccept(img -> Platform.runLater(() -> callback.accept(img)));
    } else {
        Image cachedAfterPreload;
        synchronized (CACHE) {
            cachedAfterPreload = CACHE.get(key);
        }
        Image image = cachedAfterPreload != null ? cachedAfterPreload : getDefaultAvatar(width, height);
        Platform.runLater(() -> callback.accept(image));
    }
}
```

#### [MODIFY] [MatchingController.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/MatchingController.java)

```java
// BEFORE (blocks FX thread, L257):
candidatePhoto.setImage(ImageCache.getImage(url, 400, 350));

// AFTER (non-blocking):
candidatePhoto.setImage(null); // clear immediately
ImageCache.getImageAsync(url, 400, 350, img -> candidatePhoto.setImage(img));
```

---

## P1-B — Geocoding: expand offline data + optional online path

### Already correct — no changes needed
`LocationSelectionDialog` → `LocationService` → `ResolvedLocation` pipeline is correct. No UI refactoring needed.

### B1 — Expand ISRAEL_CITIES (~15 → ~50)
Pure data change. No interface changes. Zero risk.

#### [MODIFY] [LocationService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/profile/LocationService.java)

Add ~35 cities: Eilat, Nazareth, Ra'anana, Bat Yam, Lod, Ramla, Ashkelon, Tiberias, Acre, Netivot, Sderot, Dimona, Kiryat Gat, Givatayim, Or Yehuda, Kiryat Ata, Kiryat Bialik, Kiryat Motzkin, Kiryat Yam, Yokneam, Afula, Rosh HaAyin, Hod HaSharon, Ariel, Ma'ale Adumim, Beit Shemesh, Arad, Ofakim, Yavne, Kiryat Shmona, Nahariya, Carmiel, Migdal HaEmek, Tirat Carmel, Nesher, Pardes Hanna.

### B2 — GeocodingService interface (in `core/profile/`)

#### [NEW] [GeocodingService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/profile/GeocodingService.java)
```java
public interface GeocodingService {
    record GeocodingResult(String displayName, double latitude, double longitude) {}
    List<GeocodingResult> search(String query, int maxResults);
}
```

### B3 — LocalGeocodingService (offline fallback, wraps existing data)
Adapts `LocationService.searchCities()` to the `GeocodingService` interface. Lives in `core/profile/`.

#### [NEW] [LocalGeocodingService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/profile/LocalGeocodingService.java)

### B4 — NominatimGeocodingService (optional online path)
- `java.net.http.HttpClient` — no new Maven deps
- `https://nominatim.openstreetmap.org/search?q={query}&format=json&limit={n}`
- Must include `User-Agent` header (Nominatim policy)
- Rate limit: 1 req/sec max
- Falls back to empty list on any exception
- Lives in `app/` (has external I/O)

#### [NEW] [NominatimGeocodingService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/geocoding/NominatimGeocodingService.java)

### B5 — Wire into LocationSelectionDialog
Add a `TextField` for address search. Results from `GeocodingService` populate a list. Selecting one resolves to `ResolvedLocation`. Existing city/ZIP flow stays as the default.

#### [MODIFY] [LocationSelectionDialog.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/ui/screen/LocationSelectionDialog.java)

---

## Execution Order

```
Step 1  Bug 1  — async processSwipe() + undo()           HIGH, every swipe blocks
Step 2  Bug 1b — async SafetyVM verification/deletion     LOW urgency, same pattern
Step 3  Bug 2  — ImageCache.getImageAsync()               MEDIUM, photo UX
Step 4  P1-B1  — expand ISRAEL_CITIES to ~50              data-only, zero-risk
Step 5  Smoke  — mvn spotless:apply verify + manual test  validate 1–4
Step 6  P1-B2/B3 — GeocodingService + LocalGeocoding      architecture
Step 7  P1-B4  — NominatimGeocodingService                online path
Step 8  P1-B5  — wire geocoding into dialog               UI integration
Step 9  Final  — mvn spotless:apply verify + smoke test   full validation
```

---

## Verification Plan

### Automated Tests

```powershell
# After each step — targeted tests
mvn --% -Dcheckstyle.skip=true -Dtest=MatchingViewModelTest test

# After Bug 1b
mvn --% -Dcheckstyle.skip=true -Dtest=SafetyViewModelTest test

# After ImageCache changes
mvn --% -Dcheckstyle.skip=true -Dtest=ImageCacheTest test

# Full quality gate before completion
mvn spotless:apply verify
```

### Manual Verification
1. Launch app (`mvn compile && mvn exec:exec`)
2. Log in as a seed user
3. Navigate to Matching screen — swipe quickly (like, pass, super-like, undo)
4. Verify NO UI freezing during swipes
5. Check photo loading is visually smooth (no flash of blank)
6. Navigate to Safety → test verification flow → confirm no freeze
7. Open Location dialog → verify expanded city list
8. Search an address via geocoding (if Step 7 complete) → verify coordinates resolve

---

## Deferred (out of scope)

- Real authentication (replace user-select login with credentials)
- Flyway / schema migration management
- Push notifications / WebSocket messaging
- Non-Israel country geocoding support
- REST API external exposure / auth
