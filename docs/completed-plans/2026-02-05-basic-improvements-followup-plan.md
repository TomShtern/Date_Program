# Basic Improvements Follow-Up Plan (Post-Implementation Review)

**Created:** 2026-02-05
**Purpose:** Resolve remaining concerns from the original plan and ensure consistency, clarity, and testability.
**Status:** Ready for execution (no code changes applied yet in this plan).

---

## Summary

This follow-up plan focuses on three things:
1. **Consistency & clarity** in database index placement (Task 2 cleanup).
2. **Standardized error handling** where appropriate (Task 4 refinement).
3. **Clear, step-by-step manual verification** with exact locations and expectations.

Per your guidance:
- We will **not** change the loading overlay approach (Task 7) since it is not clearly broken.
- We will keep solutions simple, consistent, and well-commented where it adds value.

---

## Progress Tracker

- [ ] 1. Reconcile DB index placement (Task 2 consistency)
- [ ] 2. Standardize error handler usage (Task 4 refinement)
- [ ] 3. Manual verification pass (explicit steps)

---

## 1) Reconcile DB Index Placement (Consistency + Clarity)

**Goal:** Keep all “additional indexes” in one place to avoid confusion.

**Current state:**
- Indexes were added inside helper methods (`createMessagingSchema`, `createSocialSchema`, `createProfileSchema`).
- Original plan expected them in `initializeSchema()` after base indexes.

**Decision:**
- Move the five new indexes to `initializeSchema()` and remove them from helper methods.
- Add a single comment block in `initializeSchema()` to explain why they are grouped there.

**Files:**
- `src/main/java/datingapp/storage/DatabaseManager.java`

**Steps:**
1. In `initializeSchema()` (near the existing index block), add:
   - `idx_conversations_last_msg`
   - `idx_friend_req_to_user`
   - `idx_notifications_created`
   - `idx_daily_picks_user`
   - `idx_profile_views_viewer`
2. Add a short comment above them:
   - Example: `// Additional indexes for query optimization (kept here for consistency)`
3. Remove those same index statements from:
   - `createMessagingSchema()`
   - `createSocialSchema()`
   - `createProfileSchema()`
4. Ensure there are no duplicates and ordering remains clear.

**Done when:**
- All five indexes exist only once (inside `initializeSchema()`), with a clear comment.

---

## 2) Standardize Error Handler Usage (Small Refinement)

**Goal:** Avoid inconsistent error reporting while keeping true user-facing validation as-is.

**Scope clarification (keep simple):**
- Keep **validation warnings** (e.g., invalid user input) as direct `Toast.showWarning()`.
- Use `ErrorHandler` for **unexpected failures** (IO, storage, service errors).

**Files (likely):**
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`

**Steps:**
1. Review each `Toast.showWarning()` in viewmodels:
   - If it is **validation feedback**, keep it as-is.
   - If it is **unexpected failure**, route through `notifyError()` (if not already).
2. Keep this change minimal and avoid introducing new complexity.

**Done when:**
- Errors from exceptions consistently flow through `ErrorHandler`.
- Validation messages remain direct warnings (no change required unless clearly wrong).

---

## 3) Manual Verification (Exact Steps)

Run the app:
- `mvn javafx:run`

### A) Login Screen Fit (No Scroll)
**Where:** `login.fxml` UI
**Steps:**
1. Launch the app and view the login screen.
2. Verify all content (header, search, list, hint, Log In, Create New) fits without vertical scroll.
3. Resize window slightly smaller; content should remain visible or scale without scroll.

**Expected:**
- No vertical scrollbars in the login screen at default window size (1000x760).

### B) Button Focus Styles
**Where:** Any screen with buttons (`Login`, `Dashboard`, `Profile`, `Matching`).
**Steps:**
1. Press `Tab` to move focus between buttons.
2. Observe focus indicators on:
   - `.button-secondary`
   - `.button-danger`
   - `.icon-button`
   - `.action-button-round`
   - `.settings-toggle`

**Expected:**
- Focus styling is clearly visible and distinct from hover/pressed states.

### C) Confirmation Dialogs
**Where:**
- Dashboard logout button
- Profile > Dealbreakers > “Clear All Dealbreakers”

**Steps:**
1. Click **Logout** from Dashboard.
2. Confirm that a dialog appears; cancel keeps you logged in; OK logs out.
3. Go to Profile > Dealbreakers and click **Clear All Dealbreakers**.
4. Confirm dialog appears; cancel preserves selections; OK clears them.

**Expected:**
- Both dialogs are styled and behave correctly.

### D) Match → Chat Navigation
**Where:** Matching screen, match popup.
**Steps:**
1. Trigger a match (use existing test users if needed).
2. In the popup, click **Send Message**.
3. Verify you land in Chat with the correct conversation selected.

**Expected:**
- Chat opens with the matched user conversation selected, not empty.

### E) Loading Overlay Visibility
**Where:** Dashboard, Matching, Chat screens.
**Steps:**
1. Navigate to each screen and trigger refresh actions if available.
2. Observe loading overlay appears while `loadingProperty` is true.

**Expected:**
- Overlay shows and hides correctly; no permanent dim overlay.

---

## Testing (Automated)

Run after code changes:
```bash
mvn spotless:apply
mvn test
mvn verify
```

---

## Notes

- **No changes to loading overlay architecture** unless a clear defect is found.
- This plan is intended to close remaining clarity/consistency gaps and document the manual tests precisely.

---

## Change Log

- 2026-02-05: Plan created.
