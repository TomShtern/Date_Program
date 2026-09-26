# Consolidated Codebase Audit — UI/JavaFX & UX (February 6, 2026)

**Category:** JavaFX controllers, ViewModels, navigation, and UX behavior
**Sources:** Kimmy 2.5, Grok, Opus 4.6 (core + storage/UI), GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Full codebase — ~119 production Java files (~19K LOC), 37 test files
**Total Unique Findings:** 75+
<!-- ChangeStamp: 1|2026-02-06 17:29:51|agent:codex|scope:audit-group-ui|Regroup audit by problem type (UI/JavaFX)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_UI.md -->

---

## Issues

### C-06: `ProfileController.handleSave()` — `cleanup()` Called BEFORE `save()`

**File:** `ui/controller/ProfileController.java`
**Source:** Storage/UI Audit

```java
cleanup(); // destroys all subscriptions FIRST
viewModel.save(); // save may trigger property updates — nobody listening
NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
```

**Fix:** Call `viewModel.save()` first, then `cleanup()`, then navigate.

---

### C-07: `MatchesController.handleStartChat()` — Missing Navigation Context

**File:** `ui/controller/MatchesController.java`
**Source:** Storage/UI Audit

Navigates to CHAT view without calling `setNavigationContext()`. The `ChatController` calls `consumeNavigationContext()` expecting a `UUID`, but receives `null`. The "Message" button on every match card is effectively broken.

**Fix:** Set navigation context before navigating.

---

### C-08: `MatchesController` — 5 INDEFINITE Animations Never Stopped

**File:** `ui/controller/MatchesController.java`
**Source:** Storage/UI Audit

5 animations use `setCycleCount(Animation.INDEFINITE)` and `.play()` but have zero `.stop()` calls in the entire file. When navigating away, animations continue running, holding scene graph references and burning CPU.

**Fix:** Store animation references. Override `cleanup()` from `BaseController` to call `.stop()` on each.

---

## 2. High-Severity Issues

### H-12: FX Thread Violations — `MatchesViewModel`, `LoginViewModel`, `StatsViewModel`

**Files:** Multiple ViewModels
**Source:** Storage/UI Audit

All three perform blocking storage calls directly on the FX Application Thread, causing UI freezes. Contrast with `DashboardViewModel` and `ChatViewModel` which correctly use `Thread.ofVirtual()` + `Platform.runLater()`.

**Fix:** Wrap all DB calls in virtual threads; update ObservableLists via `Platform.runLater()`.

---

### H-13: Memory Leaks — Untracked Listeners

**Files:** `ui/controller/ProfileController.java`, `ui/controller/DashboardController.java`, `ui/controller/MatchesController.java`
**Source:** Storage/UI Audit

Raw `addListener()` calls not wrapped in `addSubscription()`. BaseController.cleanup() cannot remove them. Lambda closures prevent garbage collection.

**Fix:** Wrap all listeners in `addSubscription()`.

---

### M-12: `ProfileController.validateHeightRange()` — Hardcoded Thresholds

Uses hardcoded `120`-`250` instead of `CONFIG.minHeightCm()` (50) and `CONFIG.maxHeightCm()` (300).

---

### M-13: `ProfileController.handleEditDealbreakers()` — Uses `HashSet` for Enums

Should use `EnumSet` per project conventions.

---

### M-14: `MatchesController` — Random Status Dots in Production

Uses random assignment for "online"/"away"/"offline" status. This is mock/demo logic showing fake presence data.

---

### M-15: `MatchesController` — Unbounded Particle Creation

Heart particles spawned every 800ms with no maximum cap. On slow devices, count can grow unboundedly.

---

### M-17: `PreferencesViewModel.savePreferences()` — No Input Validation

No bounds checking on `minAge`, `maxAge`, `maxDistance`. Could set `minAge > maxAge` or negative distances.

---

### M-18: `PreferencesViewModel` — Incomplete `OTHER` Gender Handling

If user is interested only in `Gender.OTHER`, the code falls through to the `EVERYONE` default case, losing the preference.

---

### M-19: `ProfileViewModel.savePhoto()` — Single Background Thread Tracking

Only tracks one thread. Rapid double-clicks lose the first thread reference; `dispose()` can only interrupt the last.

---

### M-21: `NavigationService` — Singleton Context Not Thread-Safe

`navigationContext` is a plain `Object` with no synchronization. Race condition between ViewModel (background thread) and controller (FX thread).

**Fix:** Use `AtomicReference<Object>`.

---

### M-22: `StatsViewModel` — Silent Degradation with Null Storage

Single-arg constructor sets `likeStorage=null` and `matchStorage=null`. `refresh()` silently skips stat sections with no user indication.

---

### L-06: `LoginViewModel.createUser()` — Double Save

Saves user, then activates, then saves again — two DB writes where one would suffice.

---

### L-07: `DashboardController` — Unnecessary `Objects.requireNonNull(obs)` in Listener

Scene property change callbacks in JavaFX never pass null for the observable parameter.

---

### UI-01: `ImageCache` Has No Eviction Policy — Unbounded Memory Growth

The image cache grows indefinitely. High-resolution images accumulate and eventually cause `OutOfMemoryError`.

**Fix:** Use LRU cache with configurable max size, `WeakHashMap`, or `SoftReference`.

---

### UI-02: `BaseController` Animations May Never Be Stopped

Loading overlays with `INDEFINITE` animations may not be stopped on cleanup if references aren't tracked.

---

### UI-03: Navigation Context Can Be Lost

Quick navigation away and back may consume context before the target controller reads it.

---

### UI-04: `ViewModelFactory` Creates Singletons — Stale State After Logout

Cached ViewModels hold stale data from previous sessions. No `reset()` or `invalidateAll()` method.

---

### UI-05: `getFirst()` Calls — Non-Standard List API

**Source:** GPT-5 Mini, Raptor Mini

Multiple files use `list.getFirst()` which is a Java 21+ `SequencedCollection` method. While it compiles with Java 25, it's non-idiomatic and lacks empty-list guards.

**Files:** `ProfileViewModel`, `MatchQualityService`, `StandoutsService`, `UiComponents`

**Fix:** Replace with guarded `list.isEmpty() ? default : list.get(0)` or `list.stream().findFirst()`.

---

## 7. Storage Layer Issues

---

*Consolidated from 7 independent audit reports — February 6, 2026*
*Auditors: Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1*

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-02-06 17:29:51|agent:codex|audit-group-ui|Regroup audit by problem type (UI/JavaFX)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_UI.md
---AGENT-LOG-END---
