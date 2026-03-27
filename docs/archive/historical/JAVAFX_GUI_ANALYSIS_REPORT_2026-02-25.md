# JavaFX GUI Analysis Report
**Project:** Dating App — JavaFX 25 / JDK 25 / MVVM Architecture
**Date:** 2026-02-25
**Analyst:** Claude Code (claude-sonnet-4-6)
**Scope:** Full GUI inspection — controllers, ViewModels, FXML, CSS, resources, test coverage, feature parity vs CLI. No code was changed.

> ⚠️ **Alignment status (2026-03-01): Historical snapshot**
> This GUI report is time-bound; some findings were already implemented after this audit.
> Current baseline: **116 main + 88 test = 204 Java files**, tests: **983/0/0/2**.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [Confirmed Bugs — Critical & High](#3-confirmed-bugs--critical--high)
4. [Screen-by-Screen Analysis](#4-screen-by-screen-analysis)
   - 4.1 Login Screen
   - 4.2 Dashboard Screen
   - 4.3 Matching Screen
   - 4.4 Matches Screen
   - 4.5 Chat Screen
   - 4.6 Profile Screen
   - 4.7 Preferences Screen
   - 4.8 Stats Screen
   - 4.9 Standouts Screen
   - 4.10 Social Screen
   - 4.11 Popup Stubs — Match & Milestone
5. [ViewModel Layer Analysis](#5-viewmodel-layer-analysis)
6. [Navigation & Routing Analysis](#6-navigation--routing-analysis)
7. [CSS & Theming Analysis](#7-css--theming-analysis)
8. [Resources & Assets](#8-resources--assets)
9. [Build & Test Coverage](#9-build--test-coverage)
10. [Feature Parity: CLI vs GUI](#10-feature-parity-cli-vs-gui)
11. [Prioritized Recommendations](#11-prioritized-recommendations)
12. [Summary Tables](#12-summary-tables)

---

## 1. Executive Summary

The JavaFX GUI implements a **solid MVVM foundation** with 10 functional screens, a clean navigation system, comprehensive animations, and correct subscription lifecycle management. The architecture demonstrates production-level thinking: virtual threads for all I/O, generation-based stale-request cancellation, card caching, and a well-structured CSS theming system.

However, **several confirmed bugs would cause runtime failures in normal use**, and there are significant gaps across every screen that leave the application feeling incomplete compared to the CLI. The most critical finding is that **navigating to Preferences crashes the application** (FXML path mismatch), and **no avatar image exists** in resources despite being referenced throughout the codebase.

**Scorecard:**

| Dimension             | Rating | Notes                                             |
|-----------------------|--------|---------------------------------------------------|
| MVVM Architecture     | ★★★★☆  | Well applied; one ViewModel misuse in DashboardVM |
| Navigation & Routing  | ★★★☆☆  | Clean abstraction; 1 NPE-causing path bug         |
| Controller Quality    | ★★★☆☆  | Consistent patterns; several incomplete handlers  |
| ViewModel Quality     | ★★★★☆  | Threading model is strong; error propagation weak |
| CSS / Theming         | ★★★☆☆  | Good foundation; missing disabled/error states    |
| Resource Completeness | ★★☆☆☆  | Missing avatar image; orphaned FXML file          |
| Test Coverage (GUI)   | ★☆☆☆☆  | Near-zero: no controller tests, no TestFX         |
| CLI Feature Parity    | ★★★☆☆  | ~70% feature parity; major gaps in notes & safety |

---

## 2. Architecture Overview

### 2.1 Pattern: MVVM + Adapter Layer

```
FXML View ←→ Controller (extends BaseController)
                 ↓
           ViewModel (JavaFX properties + virtual-thread workers)
                 ↓
         UiDataAdapters (UiUserStore, UiMatchDataAccess)
                 ↓
           Core Storage Interfaces (UserStorage, etc.)
```

This indirection through `UiDataAdapters` correctly prevents ViewModels from importing `core/storage/` directly — a rule enforced in CLAUDE.md and correctly implemented.

### 2.2 Key Infrastructure Files

| File                     | Role                                                          | Quality               |
|--------------------------|---------------------------------------------------------------|-----------------------|
| `BaseController.java`    | Subscription cleanup, overlay registration, back navigation   | Excellent             |
| `ViewModelFactory.java`  | Lazy singleton ViewModel creation, controller injection       | Good (1 bug)          |
| `NavigationService.java` | Screen history, context passing, root stack management        | Good (1 NPE bug)      |
| `UiAnimations.java`      | Centralized animation library (fadeIn, shake, bounce, glow)   | Excellent             |
| `UiConstants.java`       | Animation timings, cache sizes, window bounds                 | Good (dead constants) |
| `UiFeedbackService.java` | Toast notifications, alerts, confirmation dialogs             | Good                  |
| `ImageCache.java`        | LRU image cache (max 100, `UiConstants.IMAGE_CACHE_MAX_SIZE`) | Good                  |

### 2.3 Threading Model

All ViewModels use `Thread.ofVirtual().start(...)` for background work and `Platform.runLater(...)` for FX-thread updates. `AtomicLong` generation tokens detect and discard stale responses. `AtomicBoolean disposed` prevents post-dispose callbacks. This is a correct and modern approach for JavaFX 25.

### 2.4 Subscription Management

`BaseController` maintains `List<Subscription>` and calls `unsubscribe()` on each during `cleanup()`. All controllers use the JavaFX 25 `property.subscribe(...)` API instead of `addListener()`, which returns a `Subscription` that can be stored and cancelled. This is the correct approach for the JDK 25 API and prevents memory leaks.

---

## 3. Confirmed Bugs — Critical & High

These are code-verified bugs found in the source, with exact file:line references.

### BUG-1 — CRITICAL: Preferences screen crashes on navigation (NPE)
**File:** `src/main/java/datingapp/ui/NavigationService.java:74`
```java
PREFERENCES("/fxml/MatchPreferences.fxml"),  // ← WRONG filename
```
**Actual file on disk:** `src/main/resources/fxml/preferences.fxml`

`NavigationService` will call `getClass().getResource("/fxml/MatchPreferences.fxml")`, receive `null`, then NPE when trying to load the FXML. Every user who tries to open Preferences gets a crash. **Fix:** Change to `PREFERENCES("/fxml/preferences.fxml")`.

---

### BUG-2 — CRITICAL: Default avatar image does not exist
**File:** `src/main/java/datingapp/ui/UiConstants.java:32`
```java
public static final String DEFAULT_AVATAR_PATH = "/images/default-avatar.png";
```
**Reality:** No `.png` files exist anywhere under `src/main/resources/`. The `/images/` directory does not exist.

Any code that falls back to the default avatar (e.g., on failed image loads in MatchingController, ChatController, LoginController) will produce another NPE or a blank image control. **Fix:** Create `src/main/resources/images/default-avatar.png`.

---

### BUG-3 — HIGH: DashboardViewModel receives `profileService` twice
**File:** `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java:148-154`
```java
dashboardViewModel = new DashboardViewModel(
    services.getRecommendationService(),
    createUiMatchDataAccess(),
    services.getProfileService(),     // param 3
    services.getConnectionService(),
    services.getProfileService(),     // param 5 — duplicate!
    session);
```
Parameter 5 is likely meant to be a different service (e.g., `ActivityMetricsService` for stats display on the dashboard). The wrong service is silently injected; DashboardViewModel may compile fine but show incorrect data or fail to display stats. **Fix:** Inspect `DashboardViewModel` constructor signature and wire the correct service at position 5.

---

### BUG-4 — HIGH: Super-like is not implemented — silently behaves as regular like
**File:** `src/main/java/datingapp/ui/screen/MatchingController.java` (handleSuperLike method)
```java
private void handleSuperLike() {
    // For now, acts like a regular like (super like logic to be added later)
    viewModel.like();
}
```
The UI shows a super-like button and UP-arrow keyboard shortcut. Users expect a distinct action. It silently degrades to a regular like with no indication to the user. **Fix:** Either implement super-like in the ViewModel and service layer, or disable/hide the super-like button until the feature exists.

---

### BUG-5 — HIGH: Report dialog always submits `null` description
**Files:**
- `src/main/java/datingapp/ui/screen/MatchingController.java` (showReportDialog)
- `src/main/java/datingapp/ui/screen/ChatController.java` (showReportDialog)
```java
viewModel.reportCandidate(candidate.getId(), reason, null, true);
//                                                    ^^^^
//                              description is always null
```
The dialog collects a reason but never collects a free-text description. The `null` is hardcoded. Reports reach the backend with no description text. **Fix:** Add a `TextArea` to the report dialog and pass its value.

---

### BUG-6 — MEDIUM: `achievement_popup.fxml` is a completely orphaned resource
**File:** `src/main/resources/fxml/achievement_popup.fxml` (exists on disk)

There is no:
- `ViewType` enum entry referencing this path
- Java controller class for it
- Any reference to it anywhere in the UI codebase

`MilestonePopupController` exists in `ui/screen/` but it references a different FXML (milestone popup), not achievement popup. This file does nothing and will never be loaded. **Fix:** Either implement an `AchievementPopupController` and wire it, or delete the orphaned FXML.

---

### BUG-7 — MEDIUM: `MilestonePopupController` duplicated across two packages
The class `MilestonePopupController` appears in both:
- `src/main/java/datingapp/ui/screen/MilestonePopupController.java`
- `src/main/java/datingapp/ui/popup/MilestonePopupController.java`

Two controllers with identical names in different packages cause FXML controller injection ambiguity and signal incomplete refactoring. **Fix:** Delete the stub in `ui/popup/` and canonicalize on the `ui/screen/` version (or vice versa), updating all FXML `fx:controller` attributes accordingly.

---

### BUG-8 — MEDIUM: Presence status dots hardcoded to "offline"
**File:** `src/main/java/datingapp/ui/screen/MatchesController.java` (card builder)
```java
// FUTURE(presence-tracking): status dots hardcoded pending real presence service
statusDot.getStyleClass().add("status-offline");
```
Every user in the Matches tab shows as offline regardless of their actual activity. The comment confirms this is known but unfixed. **Fix:** Implement a lightweight presence service or remove the status dot until the feature is real.

---

### BUG-9 — LOW: Theme toggle state not persisted across sessions
**File:** `src/main/java/datingapp/ui/screen/PreferencesController.java:184`

The `themeToggle` ToggleButton state is not saved to `UserPreferences` or any persistent store. On next app launch, the toggle always resets to its FXML-default state regardless of the user's last choice. **Fix:** Save and restore theme preference in `PreferencesViewModel.savePreferences()` / `initialize()`.

---

### BUG-10 — LOW: Theme switching uses additive CSS instead of swap
**File:** `src/main/java/datingapp/ui/screen/PreferencesController.java:197-210`

Switching to light mode *adds* `light-theme.css` on top of `theme.css` (cascade override), but switching back to dark mode only *removes* `light-theme.css` without verifying `theme.css` is present. If `theme.css` was never in the stylesheet list for that scene, dark mode will have no styling at all. The approach is fragile. **Fix:** Swap the two stylesheets atomically (`remove` one, `add` the other) in a single operation.

---

## 4. Screen-by-Screen Analysis

### 4.1 Login Screen (`LoginController.java`, 657 lines)

**What works:**
- User list with richly rendered `UserListCell`: circular avatar clip, name+age, `UserState` badge, verification checkmark, profile-completion color coding (green ≥90%, blue ≥60%, yellow <60%), relative activity timestamp
- Create-account dialog: name field, age spinner (min/max from config), gender ComboBox
- Handles first-time setup flow

**Gaps:**
| Gap                                  | Severity | Notes                                                                                                                 |
|--------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------|
| Single-value `interestedIn` ComboBox | Medium   | Profile screen has full multi-select; login has only one value — inconsistency in what gets saved on account creation |
| No authentication (password/PIN)     | Low      | Demo app concern, but noted                                                                                           |
| No validation feedback inline        | Medium   | Errors shown via toast, not inline field markers                                                                      |
| Avatar always fallback               | High     | DEFAULT_AVATAR_PATH file missing (BUG-2)                                                                              |

---

### 4.2 Dashboard Screen (`DashboardController.java`, 287 lines)

**What works:**
- Responsive layout: switches to compact mode at 900px window width via `UiFeedbackService.ResponsiveController`
- 8 navigation buttons wired programmatically in `wireNavigationButtons()`
- Subscription to `stage.widthProperty()` for live responsiveness

**Gaps:**
| Gap                                        | Severity | Notes                                                                                                  |
|--------------------------------------------|----------|--------------------------------------------------------------------------------------------------------|
| "Daily Pick" navigates to generic MATCHING | Medium   | `handleViewDailyPick()` goes to `ViewType.MATCHING` — loses the specific recommended candidate context |
| DashboardViewModel receives wrong service  | High     | BUG-3 above — 5th constructor param is duplicate `profileService`                                      |
| No online-user count or platform stats     | Low      | Stats screen has per-user stats; dashboard shows none                                                  |
| No notification badge / unread count       | Medium   | Social screen has notifications but dashboard has no badge indicator                                   |

---

### 4.3 Matching Screen (`MatchingController.java`, 633 lines)

The most complete and polished screen. Implements a full swipe card UI with gesture and keyboard support.

**What works:**
- Swipe gestures: drag threshold 150px, `ParallelTransition(TranslateTransition + RotateTransition + FadeTransition)` for card exit
- Keyboard: LEFT=pass, RIGHT=like, UP=super-like, ESCAPE=back, CTRL+Z=undo
- Block and Report dialogs with reason selection
- Match popup (programmatic, not via FXML)
- Candidate queue with preloading
- Generation-based stale response cancellation

**Gaps:**
| Gap                                                       | Severity | Notes                                                                               |
|-----------------------------------------------------------|----------|-------------------------------------------------------------------------------------|
| Super-like not implemented (BUG-4)                        | High     | Silently degrades to regular like                                                   |
| No loading overlay bound to `viewModel.loadingProperty()` | Medium   | ViewModel exposes `loadingProperty()` but controller doesn't bind a loading overlay |
| Report description always null (BUG-5)                    | High     | Confirmed above                                                                     |
| Match popup built programmatically                        | Low      | `match_popup.fxml` exists but is bypassed; popup controller is a stub               |
| `resolveStylesheet()` duplicated                          | Low      | Same helper in LoginController, ChatController — should be in BaseController        |
| No "undo" feedback animation                              | Low      | Undo works but no visual confirmation (card slides back in)                         |
| `showMatchPopup()` on match — no confetti/celebration     | Low      | Purely cosmetic but important for engagement                                        |

---

### 4.4 Matches Screen (`MatchesController.java`, 835 lines)

**What works:**
- Three-section tabs: MATCHES, LIKES_YOU, YOU_LIKED with ToggleGroup
- Card cache: `EnumMap<Section, Map<String, VBox>>` + data cache for efficient re-render without rebuild
- Floating heart particle animation in empty state (max 20 particles)
- Like-back, pass-on, withdraw-like action buttons in Like cards
- `cleanup()` overrides BaseController to clear particle layer + card caches

**Gaps:**
| Gap                                   | Severity | Notes                                           |
|---------------------------------------|----------|-------------------------------------------------|
| Status dots hardcoded offline (BUG-8) | Medium   | Every user shows as offline                     |
| No match card click-to-view profile   | Medium   | Cards are visual-only; no navigation to profile |
| No unmatch option                     | Medium   | CLI has unmatch; GUI has no such control        |
| No sort / filter options              | Low      | All matches in chronological order only         |
| No "mutual interests" summary in card | Low      | Would improve discovery                         |

---

### 4.5 Chat Screen (`ChatController.java`, 363 lines)

**What works:**
- Two-panel layout: conversation list (left) + message list (right)
- Read receipts: compares `conversation.getLastReadAt(otherUserId)` vs message timestamp
- Navigation context consumption via `NavigationService.consumeNavigationContext(CHAT, UUID.class)`
- Unread badge, message snippet (truncated at 35 chars via `UiConstants.CONVERSATION_PREVIEW_CHARS`)
- Own messages styled with `message-bubble-gradient` CSS class

**Gaps:**
| Gap                                     | Severity | Notes                                                                                             |
|-----------------------------------------|----------|---------------------------------------------------------------------------------------------------|
| No Enter-to-send shortcut               | High     | `TextArea` eats Enter key; users must click Send button                                           |
| No typing indicator                     | Medium   | `UiConstants` defines `TYPING_DOT_COUNT`, `TYPING_BOUNCE_DURATION`, etc. — constants exist, no UI |
| No real-time message updates            | High     | Messages only load on navigation; no polling or push for new messages while screen is open        |
| No message length constraint or counter | Medium   | No max-length enforced; no character counter shown                                                |
| No error state on send failure          | Medium   | `SendResult.failure()` returned by service but no UI feedback                                     |
| Report description null (BUG-5)         | High     | Same as MatchingController                                                                        |
| No loading state during message send    | Low      | Button stays active during async send                                                             |
| No empty-state for no conversations     | Low      | No placeholder when conversation list is empty                                                    |

---

### 4.6 Profile Screen (`ProfileController.java`, 880 lines)

The largest controller. Comprehensive form for bio, location, height, lifestyle, gender, interests, dealbreakers, and photos.

**What works:**
- Bio textarea with live character counter (max 500)
- `handleEditInterests()` opens dialog with `Interest.Category` grouping + chip buttons
- `handleEditDealbreakers()` scrollable dialog with smoking/drinking/kids/lookingFor multi-select
- Photo upload via `FileChooser` → local file URI
- Correct save order: calls `cleanup()` then navigates

**Gaps:**
| Gap                                  | Severity | Notes                                                                                                  |
|--------------------------------------|----------|--------------------------------------------------------------------------------------------------------|
| Photo URL is local file path         | High     | Saved as `file:///...` URI — won't work after reinstall, on another device, or for other users to view |
| No inline validation errors          | Medium   | Validation errors shown via toast only, not next to the offending field                                |
| No unsaved-changes warning on cancel | Medium   | User can lose all edits by pressing back without confirmation                                          |
| Location is free-text only           | Low      | No geocoding, no "Use my location" button, no map picker                                               |
| No photo reordering                  | Low      | Photo order is insertion-order only                                                                    |
| No photo deletion                    | Medium   | Can add photos, cannot remove them                                                                     |

---

### 4.7 Preferences Screen (`PreferencesController.java`, 254 lines)

**What works:**
- Age sliders with bi-directional enforcement (min ≤ max, max ≥ min)
- Distance slider
- Gender ToggleGroup with null-deselect prevention (reselects old value)
- Theme toggle

**Gaps:**
| Gap                                   | Severity | Notes                                                       |
|---------------------------------------|----------|-------------------------------------------------------------|
| Screen crashes on navigate (BUG-1)    | Critical | Wrong FXML path in `NavigationService.ViewType.PREFERENCES` |
| Theme state not persisted (BUG-9)     | Medium   | Toggle resets on each launch                                |
| Additive CSS theme switching (BUG-10) | Medium   | Fragile approach; can leave scene unstyled                  |
| `savePreferences()` runs on FX thread | Low      | Should dispatch to virtual thread to avoid jank on slow I/O |
| No "reset to defaults" button         | Low      | Users cannot easily restore factory preference settings     |

---

### 4.8 Stats Screen (`StatsController.java`, 138 lines)

**What works:**
- Binds `totalLikesLabel`, `totalMatchesLabel`, `responseRateLabel`
- `AchievementListCell` maps emoji to Ikonli icon literals (🔥→`mdi2f-fire`, etc.)

**Gaps:**
| Gap                              | Severity | Notes                                                              |
|----------------------------------|----------|--------------------------------------------------------------------|
| No achievement unlock animation  | Medium   | Achievement list is purely static; no celebration on new unlock    |
| No progress bars on achievements | Medium   | Users can't see how close they are to the next tier                |
| No historical trend charts       | Low      | Only current totals; no chart of likes/matches over time           |
| No platform-wide stats           | Low      | CLI `StatsHandler` shows platform stats; GUI shows only user stats |
| Achievement list not sorted      | Low      | Not sorted by unlocked-first or recency                            |

---

### 4.9 Standouts Screen (`StandoutsController.java`, 148 lines)

**What works:**
- `StandoutListCell`: rank badge, name, compatibility score %, reason string, "View Profile" button
- `handleStandoutSelected()` marks the entry as interacted

**Gaps:**
| Gap                                     | Severity | Notes                                                                                                                                            |
|-----------------------------------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| Navigation loses candidate context      | High     | `handleStandoutSelected()` navigates to generic `MATCHING` without pre-loading the specific candidate; user sees a random card, not the standout |
| No contextual "Why this person?" detail | Medium   | `reason` string is shown but no breakdown of scoring factors                                                                                     |
| No expiry indicator                     | Low      | CLI standouts have a daily refresh; GUI shows no countdown                                                                                       |

---

### 4.10 Social Screen (`SocialController.java`, 199 lines)

**What works:**
- TabPane: notifications tab + friend requests tab
- `NotificationListCell`: tap-to-mark-read via `setOnMouseClicked`
- `FriendRequestListCell`: Accept / Decline buttons wired to ViewModel actions

**Gaps:**
| Gap                                      | Severity | Notes                                                                         |
|------------------------------------------|----------|-------------------------------------------------------------------------------|
| No loading state                         | Medium   | `viewModel.initialize()` fires async load but no spinner shown                |
| No error state                           | Medium   | Network/DB error leaves list empty with no explanation                        |
| No "mark all read" action                | Low      | Must tap each notification individually                                       |
| No notification filtering by type        | Low      | All notification types shown in single flat list                              |
| No friend-request sending from GUI       | High     | CLI supports sending requests; no "Add Friend" button in Matches/Chat screens |
| `@SuppressWarnings("unused")` on TabPane | Low      | Field injected but only implicit tab switching used — consider removing field |

---

### 4.11 Popup Stubs — Match & Milestone

**`MatchPopupController.java`** (`ui/popup/`)
- Nearly empty stub; no bindings to ViewModel data
- `match_popup.fxml` exists on disk but `MatchingController.showMatchPopup()` bypasses it entirely and builds the popup programmatically
- The FXML is unused dead code

**`MilestonePopupController.java`** (`ui/screen/` AND `ui/popup/`)
- Exists in two packages (BUG-7)
- Neither version has real implementation
- `achievement_popup.fxml` is orphaned (BUG-6)
- No ViewModel, no bindings, no navigation entry point

**Impact:** Achievement/milestone celebrations are completely absent from the UI, despite the backend (`EngagementDomain.Achievement`) fully supporting them.

---

## 5. ViewModel Layer Analysis

### 5.1 ViewModel Inventory

| ViewModel              | Lines | Threading                        | Error Handler | `dispose()` | Loading Property |
|------------------------|-------|----------------------------------|---------------|-------------|------------------|
| `LoginViewModel`       | ~200  | Virtual threads                  | No            | Yes         | Yes              |
| `DashboardViewModel`   | ~250  | Virtual threads                  | No            | Yes         | Yes              |
| `MatchingViewModel`    | 505   | Virtual threads + AtomicLong gen | No            | Yes         | Yes              |
| `MatchesViewModel`     | ~300  | Virtual threads                  | Yes           | Yes         | Yes              |
| `ChatViewModel`        | ~280  | Virtual threads                  | Yes           | Yes         | Yes              |
| `ProfileViewModel`     | ~220  | Virtual threads                  | Yes           | Yes         | No               |
| `PreferencesViewModel` | ~120  | Synchronous (FX thread)          | No            | Yes         | No               |
| `StatsViewModel`       | ~150  | Virtual threads                  | No            | Yes         | No               |
| `StandoutsViewModel`   | ~130  | Virtual threads                  | No            | Yes         | No               |
| `SocialViewModel`      | ~180  | Virtual threads                  | Yes           | Yes         | No               |

**Observation:** 6 of 10 ViewModels do not expose an `errorHandler` sink, meaning errors in those ViewModels are logged but never surfaced to the user. This is a significant gap given CLAUDE.md specifies: "In catch blocks — notify via handler."

### 5.2 Error Propagation Coverage

```
LoginViewModel:      logged only (no errorHandler)
DashboardViewModel:  logged only (no errorHandler)
MatchingViewModel:   logged only (no errorHandler)
MatchesViewModel:    ✓ errorHandler wired
ChatViewModel:       ✓ errorHandler wired
ProfileViewModel:    ✓ errorHandler wired
PreferencesViewModel:logged only (no errorHandler)
StatsViewModel:      logged only (no errorHandler)
StandoutsViewModel:  logged only (no errorHandler)
SocialViewModel:     ✓ errorHandler wired
```

4 of 10 ViewModels correctly surface errors. The other 6 silently swallow errors — users see blank/stale UI with no indication of failure.

### 5.3 ViewModel-in-Controller Violations

`LoginController` is constructed as:
```java
new LoginController(getLoginViewModel(), services.getProfileService())
```
The `ProfileService` is passed directly to the controller — bypassing the MVVM adapter layer. This violates the pattern that controllers should only receive ViewModels (and optionally `NavigationService`).

---

## 6. Navigation & Routing Analysis

### 6.1 ViewType Enum Status

| ViewType        | FXML Path in Code                 | Actual File          | Status    |
|-----------------|-----------------------------------|----------------------|-----------|
| LOGIN           | `/fxml/login.fxml`                | login.fxml           | ✓ OK      |
| DASHBOARD       | `/fxml/dashboard.fxml`            | dashboard.fxml       | ✓ OK      |
| MATCHING        | `/fxml/matching.fxml`             | matching.fxml        | ✓ OK      |
| MATCHES         | `/fxml/matches.fxml`              | matches.fxml         | ✓ OK      |
| CHAT            | `/fxml/chat.fxml`                 | chat.fxml            | ✓ OK      |
| PROFILE         | `/fxml/profile.fxml`              | profile.fxml         | ✓ OK      |
| **PREFERENCES** | **`/fxml/MatchPreferences.fxml`** | **preferences.fxml** | **❌ NPE** |
| STATS           | `/fxml/stats.fxml`                | stats.fxml           | ✓ OK      |
| STANDOUTS       | `/fxml/standouts.fxml`            | standouts.fxml       | ✓ OK      |
| SOCIAL          | `/fxml/social.fxml`               | social.fxml          | ✓ OK      |

### 6.2 Orphaned FXML (no ViewType entry)

| FXML File                | Controller                    | Status                                  |
|--------------------------|-------------------------------|-----------------------------------------|
| `match_popup.fxml`       | `MatchPopupController` (stub) | Unused — bypassed by programmatic popup |
| `achievement_popup.fxml` | None                          | Completely orphaned                     |

### 6.3 Navigation Context Usage

| Source Screen                  | Context Set   | Destination        | Context Consumed          |
|--------------------------------|---------------|--------------------|---------------------------|
| MatchesController → CHAT       | UUID (userId) | ChatController     | ✓ Consumed                |
| StandoutsController → MATCHING | None          | MatchingController | ❌ Context lost            |
| DashboardController → MATCHING | None          | MatchingController | ❌ Daily pick not surfaced |

---

## 7. CSS & Theming Analysis

### 7.1 Architecture

Two stylesheets:
- `theme.css` (~1271 lines): Base dark theme with full Modena override
- `light-theme.css`: Overrides applied on top for light mode (additive cascade)

CSS custom properties defined in `:root` equivalent (base selectors):
```css
-fx-primary: #667eea;        /* Purple-blue gradient primary */
-fx-accent-like: #f43f5e;    /* Red — like/heart actions */
-fx-accent-pass: #10b981;    /* Green — pass/decline actions */
-fx-accent-super: #f59e0b;   /* Amber — super-like actions */
-fx-bg-card: #1e2030;        /* Card backgrounds */
-fx-text-primary: #e2e8f0;   /* Primary text color */
```

Font stack: `"Inter", "Segoe UI", Roboto` — none of these are embedded in resources, so rendering depends on what the host system has installed. On machines without Inter, the font falls back silently.

### 7.2 Missing CSS States

| Pseudo-class                | Critical? | Impact                                                                                  |
|-----------------------------|-----------|-----------------------------------------------------------------------------------------|
| `:disabled` on inputs       | High      | Disabled fields have no visual distinction (same style as enabled)                      |
| `:error` / validation state | High      | No red-border or error indication for invalid fields                                    |
| `:loading` skeleton state   | Low       | `UiConstants.SKELETON_SHIMMER_*` constants exist but skeleton loading never implemented |
| `.status-online`            | Medium    | Only `.status-offline` used (BUG-8); online dot has no styling                          |

### 7.3 Inline Style Violations

Multiple controllers set styles programmatically via `setStyle(...)` rather than CSS classes:
- `SocialController`: `unreadDot.setStyle("-fx-background-color: #3b82f6; -fx-background-radius: 4;")`
- `SocialController`: `titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;")`
- `SocialController`: `setStyle("-fx-background-color: rgba(59, 130, 246, 0.08);")` for unread highlight

Hardcoded hex colors in Java code bypass the theming system — they won't change when switching between dark and light themes. These should be CSS classes using custom properties.

---

## 8. Resources & Assets

### 8.1 FXML Files (12 total)

```
login.fxml, dashboard.fxml, matching.fxml, matches.fxml,
chat.fxml, profile.fxml, preferences.fxml, stats.fxml,
standouts.fxml, social.fxml, match_popup.fxml, achievement_popup.fxml
```

Issues:
- `achievement_popup.fxml` — orphaned (no controller, no ViewType)
- `match_popup.fxml` — bypassed by programmatic popup construction

### 8.2 Image Assets

**Zero image files exist in `src/main/resources/`.** The `/images/` directory does not exist.

Referenced but missing:
- `/images/default-avatar.png` (`UiConstants.DEFAULT_AVATAR_PATH`) — used as fallback avatar throughout

### 8.3 CSS Files

```
src/main/resources/css/
  theme.css        (~1271 lines) — dark theme
  light-theme.css  (size unknown) — light override
```

Both files exist and are referenced correctly.

### 8.4 Dead Constants in UiConstants.java

These constants are defined but never referenced in any controller or ViewModel:

```java
// Typing indicator — defined, never used in Chat UI
TYPING_DOT_COUNT = 3
TYPING_DOT_RADIUS = 4
TYPING_DOT_SPACING = 4
TYPING_BOUNCE_DURATION = Duration.millis(400)
TYPING_BOUNCE_DISTANCE = 6
TYPING_BOUNCE_DELAY_STEP = Duration.millis(150)

// Skeleton shimmer — defined, never used anywhere
SKELETON_SHIMMER_FRAME = Duration.millis(50)
SKELETON_SHIMMER_STEP = 0.033
SKELETON_GRADIENT_START = -0.5
SKELETON_GRADIENT_WIDTH = 0.5

// Progress ring — likely used somewhere but worth verifying
PROGRESS_RING_ANIMATION_DURATION = Duration.millis(800)
```

These represent planned features that were partially designed (constants written) but never implemented.

---

## 9. Build & Test Coverage

### 9.1 GUI Test Coverage

**Current state: Near-zero.**

The project has 65 test files achieving ≥60% JaCoCo coverage, but virtually all tests cover the core and CLI layers. No JavaFX controller tests exist.

| Layer         | Test Coverage | Notes                                                 |
|---------------|---------------|-------------------------------------------------------|
| Core services | High          | 65 test files, TestStorages, full unit coverage       |
| CLI handlers  | Medium        | Handler-level tests present                           |
| ViewModels    | None          | Zero ViewModel unit tests                             |
| Controllers   | None          | Zero TestFX or controller integration tests           |
| CSS syntax    | Minimal       | Only NavigationServiceTest does basic CSS path checks |

### 9.2 Risks from Missing GUI Tests

1. **Regression risk**: Any controller change can silently break the UI — no safety net
2. **ViewModel threading**: Async/virtual-thread code is hard to reason about without tests
3. **Navigation context**: Context passing/consuming bugs will only appear at runtime
4. **FXML binding correctness**: `@FXML` field injection failures are runtime NPEs — tests would catch these at build time

### 9.3 Recommended Test Strategy

```
Priority 1: ViewModel unit tests (no JavaFX needed — test property bindings with TestClock)
Priority 2: NavigationService tests (pure Java — test ViewType paths exist on classpath)
Priority 3: TestFX controller integration tests (requires JavaFX test runtime)
Priority 4: CSS property test (verify all style classes used in Java exist in theme.css)
```

---

## 10. Feature Parity: CLI vs GUI

### 10.1 Feature Matrix

| Feature                        | CLI Handler       | GUI Screen                         | Parity            |
|--------------------------------|-------------------|------------------------------------|-------------------|
| Login / select user            | MatchingHandler   | LoginController                    | ✓                 |
| View candidates / swipe        | MatchingHandler   | MatchingController                 | ✓                 |
| Like / pass                    | MatchingHandler   | MatchingController                 | ✓                 |
| Super-like                     | MatchingHandler   | MatchingController (stub)          | ⚠ Stub            |
| Undo last swipe                | MatchingHandler   | MatchingController                 | ✓                 |
| View matches                   | MessagingHandler  | MatchesController                  | ✓                 |
| View likes received            | MessagingHandler  | MatchesController                  | ✓                 |
| Send message                   | MessagingHandler  | ChatController                     | ✓                 |
| View conversation history      | MessagingHandler  | ChatController                     | ✓                 |
| Edit profile                   | ProfileHandler    | ProfileController                  | ✓                 |
| Set preferences (age/distance) | ProfileHandler    | PreferencesController              | ⚠ Crashes (BUG-1) |
| Upload photo                   | ProfileHandler    | ProfileController                  | ⚠ Local URI only  |
| View stats                     | StatsHandler      | StatsController                    | ✓ (partial)       |
| View achievements              | StatsHandler      | StatsController                    | ⚠ No animations   |
| Platform-wide stats            | StatsHandler      | None                               | ❌ Missing         |
| Report user                    | SafetyHandler     | MatchingController, ChatController | ⚠ No description  |
| Block user                     | SafetyHandler     | MatchingController                 | ✓                 |
| Profile notes                  | ProfileHandler    | None                               | ❌ Missing         |
| Graceful exit from match       | MessagingHandler  | None                               | ❌ Missing         |
| Send friend request            | ConnectionService | None                               | ❌ Missing         |
| View standout recommendations  | MatchingHandler   | StandoutsController                | ⚠ Loses context   |
| View notifications             | ConnectionService | SocialController                   | ✓                 |
| Accept/decline friend requests | ConnectionService | SocialController                   | ✓                 |
| Unmatch                        | MatchingHandler   | None                               | ❌ Missing         |
| Theme toggle                   | N/A               | PreferencesController              | ⚠ Not persisted   |

**Legend:** ✓ Implemented | ⚠ Partial/Broken | ❌ Missing

### 10.2 Major GUI-Only Gaps (features in CLI but not GUI)

1. **Profile Notes**: CLI users can add private notes to profiles (`ProfileNote`). No GUI for this.
2. **Graceful Exit**: CLI has a "graceful exit" relationship transition. No GUI button exists.
3. **Unmatch**: CLI `MatchingHandler` supports unmatching. No option in Matches screen.
4. **Send Friend Request**: Can accept/decline in GUI but cannot initiate a request.
5. **Platform Statistics**: CLI shows aggregate platform stats. GUI's StatsScreen is user-stats only.

---

## 11. Prioritized Recommendations

### P0 — Must Fix (App-Breaking)

| #    | Recommendation                                                                | File(s)                   | Effort |
|------|-------------------------------------------------------------------------------|---------------------------|--------|
| P0.1 | Fix PREFERENCES FXML path: `MatchPreferences.fxml` → `preferences.fxml`       | NavigationService.java:74 | 1 line |
| P0.2 | Create `/images/default-avatar.png` resource (SVG→PNG placeholder at minimum) | resources/images/         | Small  |
| P0.3 | Fix DashboardViewModel 5th constructor param (wrong service)                  | ViewModelFactory.java:153 | Small  |

### P1 — High Priority (Significant UX Impact)

| #    | Recommendation                                                          | File(s)                                     | Effort |
|------|-------------------------------------------------------------------------|---------------------------------------------|--------|
| P1.1 | Add Enter-to-send in Chat (consume Enter key in TextArea send handler)  | ChatController.java                         | Small  |
| P1.2 | Add real-time message polling (e.g., 5s interval or WebSocket)          | ChatViewModel.java                          | Medium |
| P1.3 | Implement report description TextArea in report dialog                  | MatchingController, ChatController          | Small  |
| P1.4 | Show Standout candidate directly on navigation to Matching              | StandoutsController, MatchingViewModel      | Medium |
| P1.5 | Add unmatch option to Matches screen                                    | MatchesController, MatchesViewModel         | Medium |
| P1.6 | Persist theme preference in PreferencesViewModel                        | PreferencesController, PreferencesViewModel | Small  |
| P1.7 | Add send-friend-request button to Chat or Matches screen                | ChatController or MatchesController         | Medium |
| P1.8 | Add loading overlay to MatchingController (bind to `loadingProperty()`) | MatchingController                          | Small  |

### P2 — Medium Priority (Polish & Completeness)

| #     | Recommendation                                                                             | File(s)                                                               | Effort  |
|-------|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|---------|
| P2.1  | Implement typing indicator in Chat using existing `UiConstants.TYPING_*`                   | ChatController, ChatViewModel                                         | Medium  |
| P2.2  | Implement `MatchPopupController` (wire `match_popup.fxml`, remove programmatic popup)      | MatchPopupController, MatchingController                              | Medium  |
| P2.3  | Implement achievement unlock popup using `achievement_popup.fxml` + new controller         | New AchievementPopupController                                        | Medium  |
| P2.4  | Delete `ui/popup/MilestonePopupController.java` stub; canonicalize on `ui/screen/` version | Both files                                                            | Trivial |
| P2.5  | Move `resolveStylesheet()` to BaseController (remove duplication in 3 controllers)         | BaseController + 3 controllers                                        | Small   |
| P2.6  | Add error surfaces to 6 ViewModels missing `errorHandler`                                  | LoginVM, DashboardVM, MatchingVM, PreferencesVM, StatsVM, StandoutsVM | Medium  |
| P2.7  | Replace inline `setStyle(...)` hex colors in SocialController with CSS classes             | SocialController, theme.css                                           | Small   |
| P2.8  | Fix theme CSS switching to use swap-not-add approach                                       | PreferencesController                                                 | Small   |
| P2.9  | Add photo deletion to ProfileController                                                    | ProfileController, ProfileViewModel                                   | Small   |
| P2.10 | Add unsaved-changes confirmation dialog on Profile cancel                                  | ProfileController                                                     | Small   |
| P2.11 | Surface `SendResult.failure()` errors to user in Chat                                      | ChatController                                                        | Small   |
| P2.12 | Add `:disabled` and validation-error CSS states                                            | theme.css, light-theme.css                                            | Medium  |

### P3 — Low Priority (Nice-to-Have)

| #     | Recommendation                                                                         | File(s)                                           | Effort |
|-------|----------------------------------------------------------------------------------------|---------------------------------------------------|--------|
| P3.1  | Add achievement progress bars to Stats screen                                          | StatsController                                   | Medium |
| P3.2  | Add profile-click navigation from Match cards                                          | MatchesController                                 | Small  |
| P3.3  | Add "mark all read" to Social notifications                                            | SocialController, SocialViewModel                 | Small  |
| P3.4  | Add platform-wide stats tab to Stats screen                                            | StatsController, StatsViewModel                   | Medium |
| P3.5  | Implement Profile Notes UI                                                             | New ProfileNoteController or in ProfileController | Large  |
| P3.6  | Add graceful-exit option to Matches screen                                             | MatchesController                                 | Medium |
| P3.7  | Embed Inter font in resources for consistent cross-platform rendering                  | resources/fonts/                                  | Small  |
| P3.8  | Remove or implement skeleton loading (remove dead `SKELETON_*` constants or implement) | UiConstants, various                              | Medium |
| P3.9  | Add ViewModel unit tests (JUnit 5, no TestFX required)                                 | src/test/java/datingapp/ui/viewmodel/             | Large  |
| P3.10 | Remove `services.getProfileService()` from `LoginController` constructor               | ViewModelFactory, LoginController                 | Small  |

---

## 12. Summary Tables

### Bug Severity Summary

| Severity | Count | Examples                                                                       |
|----------|-------|--------------------------------------------------------------------------------|
| Critical | 2     | BUG-1 (Preferences NPE), BUG-2 (missing avatar)                                |
| High     | 3     | BUG-3 (wrong service), BUG-4 (super-like), BUG-5 (null report)                 |
| Medium   | 3     | BUG-6 (orphaned FXML), BUG-7 (duplicate controller), BUG-8 (hardcoded offline) |
| Low      | 2     | BUG-9 (theme not persisted), BUG-10 (additive CSS theme)                       |

| Screen      | Completeness | Biggest Gap                                                 |
|-------------|--------------|-------------------------------------------------------------|
| Matching    | 80%          | Super-like stub, no loading overlay                         |
| Chat        | 60%          | No real-time updates, no typing indicator, no Enter-to-send |
| Profile     | 75%          | Photo is local URI, no photo deletion, no inline validation |
| Matches     | 70%          | No unmatch, no profile click, hardcoded offline status      |
| Login       | 75%          | Single interestedIn, no inline errors, missing avatar       |
| Dashboard   | 70%          | Wrong service injected, no notification badge               |
| Social      | 70%          | No loading/error states, no friend-request sending          |
| Stats       | 55%          | No progress bars, no trends, no platform stats              |
| Standouts   | 60%          | Navigation loses candidate context                          |
| Preferences | 40%          | Crashes on load (BUG-1), theme not persisted                |

### Recommended Fix Order

```
Week 1 — P0 fixes (app-breaking)
  → Fix FXML path in NavigationService
  → Add default avatar PNG resource
  → Fix DashboardViewModel constructor

Week 2 — P1 high-impact UX
  → Enter-to-send in Chat
  → Report description dialog
  → Loading overlay in Matching
  → Theme persistence in Preferences

Week 3 — P1/P2 feature parity
  → Real-time chat updates
  → Standout → Matching context
  → Unmatch option
  → Send friend request

Week 4+ — Polish, tests, P3
  → Typing indicator
  → Achievement popup
  → ViewModel unit tests
  → CSS error/disabled states
```

---

*Report generated by Claude Code (claude-sonnet-4-6) via parallel subagent analysis + direct source inspection.*
*All file:line references were verified against the live codebase as of 2026-02-25.*
