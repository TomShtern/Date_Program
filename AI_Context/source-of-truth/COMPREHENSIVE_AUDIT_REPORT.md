# COMPREHENSIVE AUDIT REPORT (VERIFIED-ONLY)

**Verification date:** 2026-03-26
**Source of truth used:** local code only (`src/main/java`, `src/test/java`, `pom.xml`)
**Documentation used as truth:** none

---

## Verification scope

This report contains **only claims that were directly verified in code**.
All invalid, stale, speculative, or non-reproducible claims were removed.

---

## Verified baseline metrics

From local code scan (`src/**.java`) and `tokei src` run on 2026-03-26:

- Java files in `src/main/java`: **144**
- Java files in `src/test/java`: **152**
- Total Java files in `src`: **296**

`tokei src` totals:

- Java: **69,668 code / 4,906 comments / 12,337 blanks / 86,911 lines / 296 files**
- Total (all languages under `src`): **71,947 code / 5,195 comments / 12,801 blanks / 89,943 lines / 301 files**

---

## Verified findings (code-backed)

### 1) Schema integrity (confirmed)

- `likes` table has FK constraints and unique pair constraint.
  Evidence: `src/main/java/datingapp/storage/schema/SchemaInitializer.java:124-126`
- `matches` table has FK constraints and unique pair constraint.
  Evidence: `src/main/java/datingapp/storage/schema/SchemaInitializer.java:144-146`
- `daily_pick_views` table currently has no FK for `user_id`.
  Evidence: `src/main/java/datingapp/storage/schema/SchemaInitializer.java:222-230`
- `user_achievements` table currently has no FK for `user_id`.
  Evidence: `src/main/java/datingapp/storage/schema/SchemaInitializer.java:233-241`

### 2) Event model and handler coverage (confirmed)

- `AppEvent` currently declares **16** event record types.
  Evidence: `src/main/java/datingapp/app/event/AppEvent.java:37-71`
- Current handlers subscribe to these event types: `SwipeRecorded`, `ProfileSaved`, `MessageSent`, `MatchCreated`.
  Evidence:
  - `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java:19-20`
  - `src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java:22-24`
  - `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java:35-36`

### 3) Lifecycle and cleanup behavior (confirmed)

- `BaseViewModel.dispose()` disposes async scope and calls disposal hook.
  Evidence: `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java:68-79`
- `ChatController.cleanup()` explicitly removes `activeMessagesListener`.
  Evidence: `src/main/java/datingapp/ui/screen/ChatController.java:766-769`
- `ChatViewModel.dispose()` does **not** directly call `cancelProfileNoteStatusDismiss()` or `clearProfileNoteState()` even though profile-note dismiss transition exists.
  Evidence:
  - Dispose path: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java:214-223`
  - Dismiss transition logic: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java:366,385-390,401`

### 4) Messaging use-case behavior (confirmed)

- `MessagingUseCases.loadConversation(...)` marks a conversation as read when `query.markAsRead()` is true (best-effort call path).
  Evidence: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java:78-97,101-113`

### 5) Feature existence checks (confirmed)

- `StorageFactory` has in-memory construction path: `buildInMemory(AppConfig)`.
  Evidence: `src/main/java/datingapp/storage/StorageFactory.java:177-178`
- JavaFX profile preview wiring exists.
  Evidence: `src/main/java/datingapp/ui/screen/ProfileController.java:78,132-145`
- JavaFX profile score dialog flow exists.
  Evidence: `src/main/java/datingapp/ui/screen/ProfileController.java:1581-1588`
- Dealbreakers read/write API exists in `ProfileViewModel`.
  Evidence: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:969,978-980`

### 6) Test coverage facts (confirmed)

- No `InterestMatcher*` test file exists under `src/test/java`.
  Evidence: file scan result (no matches).
- `ImageCacheTest` exists.
  Evidence: `src/test/java/datingapp/ui/ImageCacheTest.java:30`
- `TimePolicyArchitectureTest` contains **2** disabled tests.
  Evidence: `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java:28,35`

---

## Verified action items

1. Add FK constraint for `daily_pick_views.user_id -> users(id)`.
2. Add FK constraint for `user_achievements.user_id -> users(id)`.
3. Add focused tests for `InterestMatcher` behavior.
4. Re-evaluate whether `ChatViewModel.dispose()` should explicitly clear/stop profile-note dismiss transition state.
5. Review and either justify or re-enable the two disabled tests in `TimePolicyArchitectureTest`.

---

## Final status

This report is now filtered to **verified-valid facts only** from the current local codebase.
