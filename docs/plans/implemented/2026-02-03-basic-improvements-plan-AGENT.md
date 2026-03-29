# Basic Improvements Implementation Plan (Agent Execution)

**Source Plan:** `docs/plans/2026-02-03-basic-improvements-plan.md`
**Created:** 2026-02-04
**Scope:** Tasks 1-8 (plus supporting changes for error toasts and loading overlays)

## Overview

Goal: Implement all eight tasks from the source plan with clean, maintainable code and no regressions. This file is optimized for agent execution.

Constraints:
- No git operations.
- Follow existing architecture and style rules.
- Do not introduce new errors; fix root causes if any arise.
- Run formatting and tests as specified.

Progress rule:
- After completing each task, update BOTH:
- This file (replace `[ ]` with `✅` in Progress Tracker and add a Status line in the task section).
- The source plan file with `✅` in the task heading and a Status line.

## Progress Tracker

- ✅ Task 0: Create this agent plan file
- ✅ Task 1: CSS focus states
- ✅ Task 2: Missing database indexes
- ✅ Task 3: Confirmation dialogs
- ✅ Task 4: Error toast notifications
- ✅ Task 5: Match popup → chat navigation
- ✅ Task 6: PacePreferences defensive copy (verification only)
- ✅ Task 7: Loading spinners/skeletons
- ✅ Task 8: Stats FXML cleanup (verification only)

## Task 1: CSS Focus States

Files:
- `src/main/resources/css/theme.css`

Steps:
1. Add `:focused` styles for `.button-secondary`, `.button-danger`, `.icon-button`, `.action-button-round`, `.settings-toggle`.
2. Place each `:focused` rule immediately after the nearest `:hover` or `:pressed` block.
3. Use the exact styles from the source plan.
4. Keep formatting consistent with the existing CSS sections.

Status: ✅ Done on 2026-02-04

## Task 2: Missing Database Indexes

Files:
- `src/main/java/datingapp/storage/DatabaseManager.java`

Steps:
1. Add `idx_conversations_last_msg` after existing conversation indexes in `createMessagingSchema`.
2. Add `idx_friend_req_to_user` after the friend request indexes in `createSocialSchema`.
3. Add `idx_notifications_created` after the notifications indexes in `createSocialSchema`.
4. Add `idx_daily_picks_user` after `idx_daily_pick_views_date` in `initializeSchema`.
5. Add `idx_profile_views_viewer` after profile views indexes in `createProfileSchema`.
6. Use `CREATE INDEX IF NOT EXISTS` and keep ordering consistent.

Status: ✅ Done on 2026-02-04

## Task 3: Confirmation Dialogs

Files:
- `src/main/java/datingapp/ui/util/UiServices.java`
- `src/main/resources/css/theme.css`
- `src/main/java/datingapp/ui/controller/DashboardController.java`
- `src/main/java/datingapp/ui/controller/ProfileController.java`

Steps:
1. Add `UiServices.showConfirmation(String title, String header, String content)` using `Alert.AlertType.CONFIRMATION`.
2. Apply the theme stylesheet and `confirmation-dialog` class to the dialog pane.
3. Add CSS for `.confirmation-dialog` at the end of `theme.css`.
4. Replace `DashboardController.handleLogout()` inline Alert with `UiServices.showConfirmation`.
5. Update ProfileController’s `clearAllBtn` handler to confirm before clearing selections.

Status: ✅ Done on 2026-02-04

## Task 4: Error Toast Notifications

Files:
- `src/main/java/datingapp/ui/viewmodel/ErrorHandler.java`
- `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/controller/DashboardController.java`
- `src/main/java/datingapp/ui/controller/LoginController.java`
- `src/main/java/datingapp/ui/controller/MatchesController.java`
- `src/main/java/datingapp/ui/controller/ProfileController.java`

Steps:
1. Create `ErrorHandler` functional interface with `void onError(String message)`.
2. Add `errorHandler` field + setter in each targeted viewmodel.
3. Add `notifyError(String userMessage, Exception e)` helper; log error and call handler on FX thread.
4. DashboardViewModel: aggregate errors per refresh and show only one toast per refresh cycle.
5. LoginViewModel: on createUser failure, call `notifyError` in addition to `errorMessage` updates.
6. MatchesViewModel: in `withdrawLike` catch, call `notifyError`.
7. ProfileViewModel: call `notifyError` in completion or photo save error cases; replace `_` catch identifiers with `ignored`.
8. Controllers: set `viewModel.setErrorHandler(Toast::showError)` (or lambda).
9. Ensure toast container exists (Task 7).

Status: ✅ Done on 2026-02-04

## Task 5: Match Popup → Chat Navigation

Files:
- `src/main/java/datingapp/ui/NavigationService.java`
- `src/main/java/datingapp/ui/controller/MatchingController.java`
- `src/main/java/datingapp/ui/controller/ChatController.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`

Steps:
1. Add `navigationContext` in `NavigationService` with `setNavigationContext(Object)` and `consumeNavigationContext()`.
2. In `MatchingController.showMatchPopup`, set context to the matched user ID before navigating to CHAT.
3. Add `ChatViewModel.openConversationWithUser(UUID otherUserId)`:
- Use `messagingService.getOrCreateConversation`.
- Call `refreshConversations`.
- Return the matching `ConversationPreview`.
4. In `ChatController.initialize`, consume context and select the returned conversation in the list.

Status: ✅ Done on 2026-02-04

## Task 6: PacePreferences Defensive Copy (Verification)

Files:
- `src/main/java/datingapp/core/PacePreferences.java`

Steps:
1. Verify `PacePreferences` is a `record`.
2. If record, no changes needed; mark task complete.

Status: ✅ Done on 2026-02-04

## Task 7: Loading Spinners / Skeletons

Files:
- `src/main/java/datingapp/ui/component/UiComponents.java`
- `src/main/resources/css/theme.css`
- `src/main/java/datingapp/ui/NavigationService.java`
- `src/main/java/datingapp/ui/controller/BaseController.java`
- `src/main/java/datingapp/ui/controller/DashboardController.java`
- `src/main/java/datingapp/ui/controller/MatchingController.java`
- `src/main/java/datingapp/ui/controller/ChatController.java`

Steps:
1. Add `UiComponents.createLoadingOverlay()` returning a `StackPane` with a `ProgressIndicator`.
2. Add CSS for `.loading-overlay` and `.loading-spinner`.
3. Update `NavigationService.initialize` to use a global `StackPane rootStack = new StackPane(rootLayout)` as the scene root.
4. Call `Toast.setContainer(rootStack)` in `NavigationService.initialize`.
5. Add `getRootStack()` in `NavigationService`.
6. Add `registerOverlay(Node overlay)` in `BaseController` and remove overlays in `cleanup()`.
7. In DashboardController, MatchingController, ChatController:
- Create overlay via `UiComponents.createLoadingOverlay()`.
- Register it.
- Bind visible/managed to `viewModel.loadingProperty()`.

Status: ✅ Done on 2026-02-04

## Task 8: Stats FXML Cleanup (Verification)

Files:
- `src/main/resources/fxml/stats.fxml`
- `src/main/java/datingapp/ui/controller/StatsController.java`

Steps:
1. Confirm ListView is bound and used (it is).
2. No changes required; mark task complete as verification-only.

Status: ✅ Done on 2026-02-04

## Testing and Validation

Commands:
- `mvn spotless:apply`
- `mvn test`
- `mvn verify`

Manual checks:
- Focus states visible on all button types.
- Confirmation dialogs for logout and clear dealbreakers.
- Error toasts appear for simulated failures.
- Match popup “Send Message” opens the correct conversation.
- Loading overlays appear during loading states.
- Stats screen still renders achievements list.

## Risks and Mitigations

- UI overlays not visible due to scene root change.
Mitigation: Validate root stack setup in `NavigationService.initialize` and bind overlays correctly.

- Toasts not appearing due to missing container.
Mitigation: Ensure `Toast.setContainer(rootStack)` is called after scene creation.

- Navigation context misuse.
Mitigation: Clear context after consumption and type-check in ChatController.
