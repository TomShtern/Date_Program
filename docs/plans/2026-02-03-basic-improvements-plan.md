# Basic Improvements Implementation Plan

**Date:** 2026-02-03
**Status:** Ready for Implementation
**Estimated Total Effort:** 4-6 hours

## Overview

This plan addresses foundational improvements across CSS, database, UI, and validation layers. All items are low-risk, focused changes that improve app quality without touching core business logic.

---

## Prerequisites

Before starting, run these commands to ensure a clean baseline:

```bash
mvn clean compile
mvn test
```

All 581 tests must pass before beginning.

---

## Task 1: CSS Focus States for All Button Types

**Priority:** High (accessibility)
**Effort:** 15-20 minutes
**File:** `src/main/resources/css/theme.css`

### Problem
Only `.button:focused` has a focus style. Secondary, danger, icon, and action buttons lack `:focused` pseudo-class styles, making keyboard navigation invisible.

### Implementation

Add these CSS rules after their respective `:pressed` states:

```css
/* After line ~133 (after .button-secondary:hover) */
.button-secondary:focused {
    -fx-border-color: -fx-primary;
    -fx-border-width: 2;
    -fx-background-color: rgba(255, 255, 255, 0.03);
}

/* After line ~141 (after .button-danger:hover) */
.button-danger:focused {
    -fx-border-color: white;
    -fx-border-width: 2;
    -fx-border-radius: 12;
}

/* After .icon-button:pressed (search for ".icon-button:pressed") */
.icon-button:focused {
    -fx-border-color: -fx-primary;
    -fx-border-width: 2;
    -fx-background-color: rgba(102, 126, 234, 0.15);
}

/* After .action-button-round:pressed (search for this class) */
.action-button-round:focused {
    -fx-border-color: white;
    -fx-border-width: 3;
    -fx-effect: dropshadow(gaussian, rgba(255, 255, 255, 0.4), 15, 0, 0, 0);
}

/* After .settings-toggle:selected */
.settings-toggle:focused {
    -fx-border-color: -fx-primary;
    -fx-border-width: 2;
}
```

### Verification
1. Run the JavaFX app: `mvn javafx:run`
2. Tab through buttons on any screen - each should show visible focus indicator
3. Focus should be clearly distinguishable from hover and pressed states

---

## Task 2: Add Missing Database Indexes

**Priority:** Medium (performance)
**Effort:** 10-15 minutes
**File:** `src/main/java/datingapp/storage/DatabaseManager.java`

### Problem
Five indexes are missing for common query patterns. While not critical at small scale, these prevent performance issues as data grows.

### Implementation

Add these statements in `initializeSchema()` after line ~207 (after existing users indexes):

```java
// Additional indexes for query optimization
stmt.execute("CREATE INDEX IF NOT EXISTS idx_conversations_last_msg ON conversations(last_message_at DESC)");
stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_to_user ON friend_requests(to_user_id)");
stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(created_at DESC)");
stmt.execute("CREATE INDEX IF NOT EXISTS idx_profile_views_viewer ON profile_views(viewer_id)");
stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_picks_user ON daily_pick_views(user_id)");
```

### Verification
1. Delete `./data/dating.mv.db` (or use a fresh test DB)
2. Run `mvn compile exec:java` - app should start without errors
3. Run `mvn test` - all tests pass

---

## Task 3: Add Confirmation Dialogs

**Priority:** High (UX/data safety)
**Effort:** 30-45 minutes
**Files:**
- `src/main/java/datingapp/ui/util/UiServices.java`
- `src/main/java/datingapp/ui/controller/DashboardController.java`
- `src/main/java/datingapp/ui/controller/ProfileController.java`

### Problem
Critical actions (logout, clear dealbreakers) execute immediately without confirmation, risking accidental data loss.

### Step 3.1: Add Confirmation Dialog Utility

In `UiServices.java`, add this method to the class:

```java
/**
 * Shows a confirmation dialog and returns true if user confirms.
 *
 * @param title Dialog title
 * @param header Header text (can be null)
 * @param content Detailed message
 * @return true if user clicked OK/Yes, false otherwise
 */
public static boolean showConfirmation(String title, String header, String content) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(title);
    alert.setHeaderText(header);
    alert.setContentText(content);

    // Style the dialog to match app theme
    DialogPane dialogPane = alert.getDialogPane();
    dialogPane.getStylesheets().add(
        UiServices.class.getResource("/css/theme.css").toExternalForm()
    );
    dialogPane.getStyleClass().add("confirmation-dialog");

    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
}
```

Add required imports at top of file:
```java
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import java.util.Optional;
```

### Step 3.2: Add Dialog Styling to theme.css

Add at end of `theme.css`:

```css
/* ============================================
   Confirmation Dialogs
   ============================================ */
.confirmation-dialog {
    -fx-background-color: -fx-surface-dark;
}

.confirmation-dialog .content {
    -fx-text-fill: -fx-text-primary;
}

.confirmation-dialog .header-panel {
    -fx-background-color: -fx-surface-elevated;
}

.confirmation-dialog .button-bar .button {
    -fx-min-width: 80;
}
```

### Step 3.3: Update DashboardController Logout

In `DashboardController.java`, find the logout handler method and wrap it:

```java
private void handleLogout() {
    if (UiServices.showConfirmation(
            "Confirm Logout",
            "Are you sure you want to log out?",
            "You will need to log in again to continue.")) {
        // existing logout logic here
        AppSession.getInstance().logout();
        navigationService.navigateTo(SceneType.LOGIN);
    }
}
```

### Step 3.4: Update ProfileController Clear Dealbreakers

In `ProfileController.java`, find the clear dealbreakers handler and wrap it:

```java
private void handleClearDealbreakers() {
    if (UiServices.showConfirmation(
            "Clear All Dealbreakers",
            "Remove all dealbreaker preferences?",
            "This will reset all your dealbreaker filters. You can set them again anytime.")) {
        // existing clear logic
        currentUser.setDealbreakers(null);
        viewModel.refreshProfile();
        UiServices.showToast(root, "Dealbreakers cleared", UiServices.ToastType.SUCCESS);
    }
}
```

### Verification
1. Run `mvn javafx:run`
2. Click logout button - confirmation dialog should appear
3. Cancel should keep you logged in
4. OK should log you out
5. Test clear dealbreakers similarly

---

## Task 4: Add Error Toast Notifications in UI

**Priority:** High (UX)
**Effort:** 45-60 minutes
**Files:** Multiple ViewModel files in `src/main/java/datingapp/ui/viewmodel/`

### Problem
All catch blocks log errors but don't show user feedback. Users see silent failures.

### Context
`UiServices.showToast(Parent root, String message, ToastType type)` already exists. The ViewModels need to use it when errors occur.

### Step 4.1: Add Error Callback Interface

Create new file `src/main/java/datingapp/ui/viewmodel/ErrorHandler.java`:

```java
package datingapp.ui.viewmodel;

/**
 * Functional interface for ViewModels to report errors to their controllers.
 * Controllers implement this to show toast notifications.
 */
@FunctionalInterface
public interface ErrorHandler {
    void onError(String message);
}
```

### Step 4.2: Update BaseViewModel (if exists) or Each ViewModel

For each ViewModel that has try-catch blocks with silent failures, add:

1. A field: `private ErrorHandler errorHandler;`
2. A setter: `public void setErrorHandler(ErrorHandler handler) { this.errorHandler = handler; }`
3. In catch blocks, replace silent logging with:
```java
} catch (Exception e) {
    logger.error("Operation failed: {}", e.getMessage());
    if (errorHandler != null) {
        Platform.runLater(() -> errorHandler.onError("Operation failed: " + e.getMessage()));
    }
}
```

### Step 4.3: Wire Up in Controllers

In each controller's `initialize()` method, after getting the ViewModel:

```java
viewModel.setErrorHandler(message ->
    UiServices.showToast(root, message, UiServices.ToastType.ERROR)
);
```

### Files to Update (check each for silent catch blocks):
- `LoginViewModel.java`
- `DashboardViewModel.java`
- `MatchingViewModel.java`
- `ChatViewModel.java`
- `ProfileViewModel.java`
- `MatchesViewModel.java`

### Verification
1. Temporarily break something (e.g., stop DB)
2. Perform an action that would fail
3. Error toast should appear instead of silent failure

---

## Task 5: Fix Match Popup → Chat Navigation

**Priority:** Medium (UX)
**Effort:** 20-30 minutes
**Files:**
- `src/main/java/datingapp/ui/controller/MatchingController.java`
- `src/main/java/datingapp/ui/NavigationService.java`

### Problem
After match popup, "Send Message" navigates to CHAT without setting conversation context. User sees empty chat.

### Step 5.1: Update NavigationService

Add method to `NavigationService.java`:

```java
private Object navigationContext;

public void setNavigationContext(Object context) {
    this.navigationContext = context;
}

public Object getNavigationContext() {
    Object ctx = this.navigationContext;
    this.navigationContext = null; // Clear after retrieval
    return ctx;
}
```

### Step 5.2: Update MatchingController

Find the match popup "Send Message" button handler. Before navigating:

```java
// In the send message button handler
Match match = /* the match from popup context */;
UUID otherUserId = match.getOtherUser(currentUserId);
navigationService.setNavigationContext(otherUserId);
navigationService.navigateTo(SceneType.CHAT);
```

### Step 5.3: Update ChatController

In `initialize()`, check for context:

```java
@Override
public void initialize(URL url, ResourceBundle rb) {
    // existing init code...

    // Check if navigated with context
    Object context = navigationService.getNavigationContext();
    if (context instanceof UUID userId) {
        viewModel.selectConversationWithUser(userId);
    }
}
```

### Step 5.4: Add selectConversationWithUser to ChatViewModel

```java
public void selectConversationWithUser(UUID otherUserId) {
    UUID currentUserId = AppSession.getInstance().getCurrentUser().getId();
    String conversationId = Messaging.Conversation.generateId(currentUserId, otherUserId);

    // Find or create conversation and select it
    conversationsProperty().stream()
        .filter(c -> c.getId().equals(conversationId))
        .findFirst()
        .ifPresent(this::selectConversation);
}
```

### Verification
1. Match with someone (may need test data)
2. Click "Send Message" on match popup
3. Should navigate to chat with that conversation selected

---

## Task 6: Add Defensive Copy for PacePreferences

**Priority:** Low (code quality)
**Effort:** 10 minutes
**File:** `src/main/java/datingapp/core/User.java`

### Problem
`getPacePreferences()` returns direct reference, allowing external mutation.

### Current Code (around line 500):
```java
public PacePreferences getPacePreferences() {
    return pacePreferences;
}
```

### Implementation

`PacePreferences` is a record (immutable), so actually NO change needed. Records are immutable by default.

**Verification:** Check that `PacePreferences` is defined as a record in `Preferences.java`:
```bash
rg "record PacePreferences" src/main/java
```

If it's a record, this task is already complete. If it's a class, create a copy constructor or make it a record.

---

## Task 7: Add Loading Spinners/Skeletons

**Priority:** Low (UX polish)
**Effort:** 45-60 minutes
**Files:** Multiple controller and FXML files

### Problem
Loading states just dim content with no spinner, unclear if app is working.

### Step 7.1: Create Loading Spinner Component

Add to `UiComponents.java` (or create if doesn't exist):

```java
package datingapp.ui.component;

import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;

public class UiComponents {

    /**
     * Creates a loading overlay with spinner.
     */
    public static StackPane createLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(50, 50);
        spinner.getStyleClass().add("loading-spinner");

        StackPane overlay = new StackPane(spinner);
        overlay.getStyleClass().add("loading-overlay");
        overlay.setVisible(false);
        overlay.setManaged(false);

        return overlay;
    }
}
```

### Step 7.2: Add CSS for Loading Overlay

Add to `theme.css`:

```css
/* ============================================
   Loading States
   ============================================ */
.loading-overlay {
    -fx-background-color: rgba(15, 23, 42, 0.7);
}

.loading-spinner {
    -fx-progress-color: -fx-primary;
}

.loading-spinner .indicator {
    -fx-border-color: transparent;
    -fx-border-width: 0;
}
```

### Step 7.3: Add to Controllers

In each controller that has loading states (Dashboard, Matching, Chat):

1. Add field: `private StackPane loadingOverlay;`
2. In `initialize()`:
```java
loadingOverlay = UiComponents.createLoadingOverlay();
((StackPane) root).getChildren().add(loadingOverlay);
```
3. Bind to loading property:
```java
loadingOverlay.visibleProperty().bind(viewModel.loadingProperty());
loadingOverlay.managedProperty().bind(viewModel.loadingProperty());
```

### Verification
1. Trigger a loading state (e.g., refresh data)
2. Spinner should appear over dimmed content
3. Spinner should disappear when loading completes

---

## Task 8: Review and Clean Up stats.fxml ListView

**Priority:** Low (tech debt)
**Effort:** 15-20 minutes
**File:** `src/main/resources/fxml/stats.fxml`

### Problem
Hidden ListView at line ~143 duplicates achievement card functionality.

### Investigation Steps

1. Open `stats.fxml` and locate the hidden ListView
2. Check if it's bound to anything in `StatsController.java`
3. Decision tree:
   - If bound and used: Keep it, document purpose
   - If bound but redundant: Remove from FXML and controller
   - If not bound: Remove from FXML

### Implementation
Based on findings:
- Remove unused FXML elements
- Remove any dead bindings in controller
- Run tests to ensure nothing breaks

---

## Final Verification Checklist

After completing all tasks:

```bash
# Format code
mvn spotless:apply

# Run all tests
mvn test

# Full verification build
mvn verify

# Manual testing
mvn javafx:run
```

### Manual Test Checklist
- [ ] Tab through all screens - focus visible on all interactive elements
- [ ] Logout shows confirmation dialog
- [ ] App starts with fresh database (delete `./data/dating.mv.db` first)
- [ ] Error conditions show toast messages
- [ ] Match → Chat navigation works correctly
- [ ] Loading states show spinner

---

## Commit Message Template

```
feat: Add foundational UI/UX and database improvements

- Add focus states for all button types (accessibility)
- Add 5 missing database indexes for query optimization
- Add confirmation dialogs for logout and clear dealbreakers
- Add error toast notifications replacing silent failures
- Fix match popup → chat navigation context passing
- Add loading spinner overlay component

Closes #[issue-number-if-applicable]

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

---

## Rollback Plan

If issues arise:
1. Each task is independent - can be reverted individually
2. Database indexes are additive only - no data changes
3. CSS changes are purely visual - safe to revert
4. Keep backup of `dating.mv.db` before testing

---

## Notes for AI Agent

- Run `mvn spotless:apply` after each file modification
- Test incrementally - don't batch all changes before testing
- If a task seems more complex than described, stop and document the issue
- All imports should be explicit (no star imports)
- Follow existing code patterns in the codebase
- Check CLAUDE.md for coding standards if uncertain
