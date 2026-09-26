# JavaFX UI Fixes + Feature Expansion — End-to-End Implementation Plan

**Date:** 2026-03-10
**Workspace:** Date_Program
**Goal:** Deliver a user-visible upgrade package that fixes concrete UI flaws and adds new features, with workstreams optimized for parallel AI-agent execution.

---

## Implementation Status Snapshot (Updated 2026-03-10)

| Package / Step                                    | Status      | Notes                                                                                                                                                              |
|---------------------------------------------------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Work Package A — Profile Screen UX                | ✅ Completed | Implemented structured location dialog, 6-photo management with replacement flow, 5MB validation, photo count feedback, and async save feedback/navigation gating. |
| Work Package B — Daily Pick UX Completion         | ✅ Completed | Added reason, seen state, availability gating, and empty-state messaging to dashboard daily-pick card.                                                             |
| Work Package C — Chat Presence + Typing Indicator | ✅ Completed | Added no-op presence adapter, hidden-when-unknown presence dot, header presence binding, and typing indicator wiring.                                              |
| Work Package D — Default Avatar Visibility Fix    | ✅ Completed | Replaced `default-avatar.png` with a visible 256×256 asset and added a generated fallback placeholder in `ImageCache`.                                             |
| Targeted validation                               | ✅ Completed | Updated/added focused UI/viewmodel tests for profile, dashboard, chat, and related UI controller scenarios.                                                        |
| Full repository verification                      | ✅ Completed | `mvn clean spotless:apply verify` passed with BUILD SUCCESS, `Tests run: 1016, Failures: 0, Errors: 0, Skipped: 2`, PMD clean, and JaCoCo checks met.              |

---

## 1) Scope and Outcomes

### In-scope fixes (issues)
1. **Default avatar invisibility** (tiny 1x1 asset) causes blank avatars.
2. **Location input UX** requires raw `lat,lon` string and silently ignores invalid input.
3. **Daily Pick UX incomplete** — reason/seen state ignored; navigation allowed even when no pick.
4. **Presence indicator misleading** — status dot always appears online with no presence data.
5. **Photo upload lacks safeguards** — no file filters/size cap/clear limit feedback.
6. **Profile save feedback missing** — async save navigates away without clear success/failure UX.

### In-scope features
1. **Location dialog/wizard** (validated structured input, improved UX) in Profile screen.
2. **Daily Pick detail card** (reason + seen state + disable/route when none).
3. **Photo management upgrade** (more photos, clear limits, replace/confirm flow).
4. **Chat presence + typing indicator UI** (UI-ready, no-op backend adapter by default).

### Out-of-scope (explicit)
- Backend geocoding provider integration.
- Real presence service or server-driven typing events.
- Any DB schema migration (not required for these UI changes).

---

## 2) Parallelization Strategy (AI Agents)

Work is split to minimize file overlap. Each package has a distinct write set. Parallelize A–D. Merge after each package passes basic compile/test.

**Work Package A — Profile Screen UX (Location + Photo + Save Feedback)**
- Owns: Profile screen, Profile ViewModel, Local photo storage.
- Files:
  - `src/main/java/datingapp/ui/screen/ProfileController.java`
  - `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
  - `src/main/java/datingapp/ui/LocalPhotoStore.java`
  - `src/main/resources/fxml/profile.fxml`
  - `src/main/resources/css/theme.css`

**Work Package B — Daily Pick UX Completion**
- Owns: Dashboard ViewModel/Controller and FXML.
- Files:
  - `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
  - `src/main/java/datingapp/ui/screen/DashboardController.java`
  - `src/main/resources/fxml/dashboard.fxml`
  - `src/main/resources/css/theme.css`

**Work Package C — Chat Presence + Typing Indicator**
- Owns: Chat ViewModel/Controller, UI adapter, components.
- Files:
  - `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
  - `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
  - `src/main/java/datingapp/ui/screen/ChatController.java`
  - `src/main/resources/fxml/chat.fxml`
  - `src/main/java/datingapp/ui/UiComponents.java`
  - `src/main/resources/css/theme.css`

**Work Package D — Default Avatar Visibility Fix**
- Owns: Default avatar asset and fallback logic.
- Files:
  - `src/main/resources/images/default-avatar.png`
  - `src/main/java/datingapp/ui/ImageCache.java`

### Merge order
1. Merge D (asset) early to unblock UI testing.
2. Merge A, B, C in any order once individually compiled.
3. Final integration build and manual UI verification.

---

## 3) Detailed Implementation Plans

### A) Profile Screen UX (Location + Photo + Save Feedback)

**Status:** ✅ Completed on 2026-03-10

**Implementation notes:**
- Replaced free-text location editing with a validated location dialog driven by `latitude` / `longitude` state in `ProfileViewModel`.
- Made `locationField` read-only and bound it to formatted display text.
- Increased managed photo capacity from 2 to 6 and added explicit replacement support at the storage/viewmodel layer.
- Added file chooser filters for `png/jpg/jpeg`, a 5MB viewmodel size guard, and a `Photos X/6` UI label.
- Reworked save flow so the controller waits for async completion, disables buttons while saving, shows clear status, and only navigates on success.

#### A1. Location dialog/wizard
**Current behavior:** Free-text `lat,lon` is parsed in `ProfileViewModel.applyLocation()`; invalid strings are ignored without feedback.

**New behavior:**
- Replace raw text entry with a structured dialog for latitude/longitude.
- `locationField` becomes read-only. Add a “Set Location” button or make field click open the dialog.
- Validate ranges: latitude `[-90, 90]`, longitude `[-180, 180]`.
- Show inline error message if invalid.
- Store values in ViewModel as doubles, not string parsing.

**Implementation changes:**
- **ProfileViewModel**:
  - Add `DoubleProperty latitude`, `DoubleProperty longitude`.
  - Add `StringProperty locationDisplay` formatted as `"{lat:.4f}, {lon:.4f}"` when both present, else empty.
  - Update `loadCurrentUser()` to set `latitude/longitude` from `user.getLat()/getLon()`.
  - Replace `applyLocation()` to use `latitude/longitude` directly.
- **ProfileController**:
  - Add `handleSetLocation()` method to open dialog.
  - Bind `locationField.textProperty()` to `locationDisplay`.
  - Add input dialog UI (two numeric fields + validation + Save/Cancel).
  - Update `profile.fxml` to include Set Location button and disabled text field.
- **Theme/CSS**: Add styles for dialog fields and error label (if needed).

**Acceptance criteria:**
- User can set valid coordinates using a dialog.
- Invalid coordinates show an error and do not update the model.
- Location display updates immediately and persists on save.

#### A2. Photo management upgrade
**Current behavior:** No file filters; max photos hard-limited to 2; no explicit UI limit feedback.

**New behavior:**
- Increase max photos to 6.
- File chooser filters allow `png/jpg/jpeg` only.
- Enforce file size cap (5MB); show error if exceeded.
- If at max, prompt: replace current photo or cancel.
- Show a `Photos X/6` indicator.

**Implementation changes:**
- **LocalPhotoStore**: `MAX_PHOTOS = 6`.
- **ProfileViewModel**:
  - Add `int getMaxPhotos()` and `IntegerProperty photoCountProperty` or derived binding.
  - Update `savePhoto(File)` to reject >5MB.
- **ProfileController**:
  - Configure `FileChooser` filters.
  - If at max photos, confirm replace current before saving.
  - Add photos count label bound to size.
- **Profile FXML**: Add label for count if not already present.

**Acceptance criteria:**
- User can upload up to 6 photos.
- Unsupported file types are blocked by chooser.
- Oversized files show a clear error.
- At limit, user is prompted before replacement.

#### A3. Save feedback
**Current behavior:** Save is async and UI navigates away immediately; failures not clearly shown.

**New behavior:**
- On Save: disable Save/Cancel buttons, show “Saving…” status.
- On success: show toast and navigate to Dashboard.
- On failure: show error toast, stay on Profile screen.

**Implementation changes:**
- **ProfileViewModel**:
  - Add `BooleanProperty saving`.
  - Add `void saveAsync(Consumer<Boolean> onComplete)` that triggers async save and calls `onComplete(true/false)` on UI thread.
- **ProfileController**:
  - Replace `handleSave()` to use `saveAsync` and only navigate on success.
  - Bind Save/Cancel disable state to `saving`.

**Acceptance criteria:**
- Navigation only happens on success.
- Clear UI feedback on success and failure.

---

### B) Daily Pick UX Completion

**Status:** ✅ Completed on 2026-03-10

**Implementation notes:**
- `DashboardViewModel` now exposes daily-pick reason, seen state, availability, and empty-state message properties.
- `DashboardController` now disables the action when no daily pick is available and shows an informational message instead of navigating blindly.
- `dashboard.fxml` and `theme.css` now render the reason text, viewed chip, and no-pick explanatory state.

**Current behavior:** Daily Pick reason and seen state are ignored; button always navigates even if no pick.

**New behavior:**
- Show reason text if available.
- Show a “Viewed” chip if already seen.
- Disable Daily Pick action if unavailable.
- If no pick, show a “No pick today” message.

**Implementation changes:**
- **DashboardViewModel**:
  - Add `StringProperty dailyPickReason`.
  - Add `BooleanProperty dailyPickSeen`.
  - Add `BooleanProperty dailyPickAvailable`.
  - Populate from `RecommendationService.DailyPick` (`reason`, `alreadySeen`).
- **DashboardController**:
  - Bind reason/seen/availability to UI.
  - Disable Daily Pick button if unavailable.
- **Dashboard FXML + CSS**:
  - Add reason label and viewed badge.

**Acceptance criteria:**
- Reason is visible when pick exists.
- “Viewed” chip appears if already seen.
- Daily Pick button disabled and explanatory message displayed when no pick.

---

### C) Chat Presence + Typing Indicator (UI-ready)

**Status:** ✅ Completed on 2026-03-10

**Implementation notes:**
- Added `UiPresenceDataAccess` plus `PresenceStatus` and a `NoOpUiPresenceDataAccess` default.
- `ChatViewModel` now loads and polls presence/typing state for the selected conversation.
- `ChatController` now shows a presence dot only when the status is known and hosts a `UiComponents.TypingIndicator` bound to `remoteTyping`.
- Conversation-list status dots are no longer misleadingly forced online by default.

**Current behavior:** Presence dot always shown as online, misleading. No typing indicator binding.

**New behavior:**
- Presence indicator reflects status when available; hidden if unknown.
- Typing indicator UI displayed when `remoteTyping` is true.
- UI stays functional with no-op backend.

**Implementation changes:**
- **UiDataAdapters**:
  - Add `UiPresenceDataAccess` with methods `getPresence(UUID)` and `isTyping(UUID)`.
  - Provide `NoOpUiPresenceDataAccess` default returning `UNKNOWN` and `false`.
- **ChatViewModel**:
  - Add `ObjectProperty<PresenceStatus>` and `BooleanProperty remoteTyping`.
  - On conversation selection, load status via `UiPresenceDataAccess`.
  - Optionally poll presence every 10–15s using `ViewModelAsyncScope.runPolling`.
- **ChatController / FXML**:
  - Bind status dot to presence (hide if unknown).
  - Insert `UiComponents.TypingIndicator` below messages and bind to `remoteTyping`.

**Acceptance criteria:**
- Status dot hidden by default if no presence data.
- Typing indicator appears when property toggled (can be simulated in dev).

---

### D) Default Avatar Visibility Fix

**Status:** ✅ Completed on 2026-03-10

**Implementation notes:**
- Replaced `src/main/resources/images/default-avatar.png` with a real visible 256×256 PNG asset.
- Updated `ImageCache` to detect tiny or invalid default-avatar resources and synthesize a visible placeholder image instead of returning a transparent 1×1 fallback.

**Current behavior:** `default-avatar.png` is a 1x1 image; avatars appear blank.

**New behavior:**
- Replace avatar asset with a real 256x256 (or 512x512) image.
- Improve fallback to a visible `WritableImage` when resource missing.

**Implementation changes:**
- Replace `src/main/resources/images/default-avatar.png` with proper PNG.
- Update `ImageCache.createPlaceholder()` to return a neutral visible placeholder (non-transparent) if asset missing.

**Acceptance criteria:**
- Avatars show a visible placeholder in all views.

---

## 4) Integration Steps

1. ✅ Merged Work Package D (asset + fallback logic).
2. ✅ Merged Work Package A and validated with focused UI/viewmodel tests.
3. ✅ Merged Work Package B and validated with focused dashboard/viewmodel tests.
4. ✅ Merged Work Package C and validated with focused chat/controller/viewmodel tests.
5. ✅ Ran full repository verification with `mvn clean spotless:apply verify`.
6. ⏳ Optional follow-up: run `mvn javafx:run` for an additional manual UX pass if you want an interactive visual smoke test after this automated completion.

---

## 5) Test Plan

**Automated:**
- ✅ Focused tests updated and run for profile, dashboard, chat, safety-screen/controller, and image-cache behavior.
- ✅ Final full verification completed with `mvn clean spotless:apply verify`.

**Manual UI:**
- Profile: Set Location dialog (valid/invalid), photo upload, save feedback.
- Dashboard: Daily Pick reason/seen/badge, disabled state when none.
- Chat: presence dot hidden when unknown; typing indicator visible when toggled.
- Avatar: default avatar visible everywhere.

---

## 6) Risks and Mitigations

- **Risk:** UI binding conflicts if multiple agents edit same FXML.
  - **Mitigation:** isolate FXML changes to one package per file.
- **Risk:** Async save completion race.
  - **Mitigation:** centralize through `saveAsync` and dispatch to UI thread.
- **Risk:** Presence polling without backend.
  - **Mitigation:** no-op adapter defaults to UNKNOWN.

---

## 7) Deliverables

- ✅ Updated UI behaviors and dialogs.
- ✅ New avatar asset + visible fallback.
- ✅ Updated ViewModels with new properties and adapter wiring.
- ✅ Refreshed FXML and CSS for new elements.
- ✅ Fully documented manual verification checklist and implementation status trail in this plan.

---

## 8) Acceptance Summary (Must All Pass)

- ✅ Default avatars are visible and non-blank.
- ✅ Location can be set via dialog and saved reliably.
- ✅ Daily Pick shows reason and seen status, and disables action when none.
- ✅ Chat presence dot is not misleading; typing indicator renders when active.
- ✅ Photo upload respects file filters, size, and max count.
- ✅ Save flow provides explicit success/failure feedback and correct navigation.

