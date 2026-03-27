# Chat Screen — Phase 2 Plan
_Created: 2026-03-13 · Based on live screenshot review after Phase 1 implementation_

## Execution status (updated: 2026-03-13)

### Completion checklist

- [x] **CB1** `@BindBean` -> `@BindMethods` in `JdbiUserStorage` (record binding fixed)
- [x] **CB2A** Constrained note status label in `chat.fxml` (`maxWidth` + `ellipsis`)
- [x] **CB2B** Sanitized profile-note failures in `ChatViewModel` (no raw exception internals in UI)
- [x] **CB3** Forced English locale for chat date formatters in `ChatController`
- [x] **CB4** Eliminated middle-line artifact risk by separating composer style (`chat-composer-bar`) and explicit border styling in `theme.css` + `light-theme.css`
- [x] **L1** Note panel max height + conversation list minimum height in `chat.fxml`
- [x] **L2** Added profile-note status auto-dismiss (3s) with token-safe cancellation in `ChatViewModel`
- [x] **L3** Added vertical toolbar separator between relationship and safety actions in `chat.fxml`
- [x] **L4** Replaced generic avatar icon with deterministic initials + color palette in `ChatController`
- [x] **U1** Added note section visual separation (margin + divider) in `chat.fxml`
- [x] **U2** Added disabled send-button reason tooltip logic in `ChatController`
- [x] **U3** Added explicit conversation-list empty placeholder in `ChatController`
- [x] **U4** Added smart auto-scroll behavior (scroll only when near bottom/new-send/new-select) in `ChatController`
- [x] **R1** Added targeted storage regression coverage for profile-note round trip in `JdbiUserStorageNormalizationTest`
- [x] **R2** Strengthened `sameConversationPreview` identity checks with `otherUser` ID in `ChatViewModel`
- [x] **R3** Ensured note state is cleared before loading note for new selected conversation in `ChatViewModel`

### Validation run results

- [x] Focused tests passed:
    - `ChatViewModelTest`
    - `ChatControllerTest`
    - `JdbiUserStorageNormalizationTest`
- [x] Full quality gate passed: `mvn spotless:apply verify`
    - Result: **BUILD SUCCESS**
    - Tests summary: **1095 run, 0 failures, 0 errors, 2 skipped**

### Post-completion hotfix (2026-03-13)

- [x] **HF1** Fixed "send message exits chat" regression root cause in `ChatViewModel`:
    - On transient `listConversations` failures, keep existing `conversations` list + `selectedConversation` intact (no destructive empty-list replacement).
- [x] **HF2** Hardened `MessagingUseCases.loadConversation(...)`:
    - `markAsRead(...)` is now best-effort; failures are logged and do not fail message loading.
- [x] **HF3** Reduced lock contention in `ConnectionService.markAsRead(...)`:
    - Skip write when unread count is already zero.
- [x] **HF4** Added regression tests:
    - `ChatViewModelTest#refreshFailureDoesNotClearSelectedConversation`
    - `MessagingUseCasesTest#loadConversationSucceedsWhenMarkAsReadFails`
- [x] **HF5** Re-verified quality gate:
    - `mvn spotless:apply verify` => **BUILD SUCCESS**
    - Tests summary: **1097 run, 0 failures, 0 errors, 2 skipped**

---

## What the screenshot shows

| Symptom                                           | Visible In Screenshot                             |
|---------------------------------------------------|---------------------------------------------------|
| Raw JDBI exception text in note status label      | Long multi-line error in sidebar panel            |
| Note panel overflows sidebar with wrapped error   | Error text bleeds past sidebar boundary           |
| "PRIVATE NOTE" label visually disconnected        | Floats above conversation list item               |
| Hebrew date "יוד 13" in conversation timestamp    | `dd MMM` with system Hebrew locale                |
| Green horizontal rendering artifact               | Line crossing entire width at middle of chat area |
| Last toolbar button (flag) shows purple highlight | Lingering focus/active state                      |
| Note save does not work at all                    | Confirmed — saves fail silently with JDBI error   |

---

## CB1 — CRITICAL: `@BindBean` fails on Java record `ProfileNote`

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:496`

**Root cause:** JDBI's `@BindBean` uses Java Beans introspection, which expects getter methods
of the form `getAuthorId()`. Java `record` types expose `authorId()` without the `get` prefix.
JDBI cannot find `authorId` as a bean property, so binding fails with:
```
Missing named parameter 'authorId' in binding:{finder:[{lazy bean property arguments
"ProfileNote[authorId=11111111-..., subjectId=...]"}]}
```
Every attempt to save or upsert a profile note fails. This error propagates all the way to the
UI's status label.

**Fix:** Replace `@BindBean` with `@BindMethods` (which works with record accessors).
JDBI 3.x ships `@BindMethods` for exactly this use case.

```java
// Wrong — JDBI bean binding can't see Java record accessors
void saveProfileNote(@BindBean ProfileNote note);

// Fix — BindMethods uses method-style introspection compatible with records
void saveProfileNote(@BindMethods ProfileNote note);
```

Required import: `org.jdbi.v3.sqlobject.customizer.BindMethods`

**Impact:** Fixes save, update, and the whole note workflow in one line.

---

## CB2 — Note status label overflows sidebar with raw error text

**File:** `src/main/resources/fxml/chat.fxml:35`

**Problem:** `profileNoteStatusLabel` has `wrapText="true"` and `HBox.hgrow="ALWAYS"` but no
`maxHeight`. When the raw JDBI exception string is bound to it (because CB1 throws), the label
wraps across dozens of lines, ballooning the note panel and breaking the sidebar layout.

Two fixes required — one in FXML, one in the ViewModel:

**Fix A — FXML:** Constrain the label and add `ellipsis` overflow instead of wrap:
```xml
<!-- Before -->
<Label fx:id="profileNoteStatusLabel" styleClass="text-secondary" wrapText="true" HBox.hgrow="ALWAYS"/>

<!-- After — single line, ellipsis, capped width -->
<Label fx:id="profileNoteStatusLabel" styleClass="text-secondary"
       maxWidth="200" ellipsisString="..." HBox.hgrow="ALWAYS"/>
```

**Fix B — ViewModel `applyProfileNoteFailure`:** Sanitize error messages before setting them.
Never expose raw exception messages to the user. Use a short user-facing string:
```java
// ChatViewModel.java — applyProfileNoteFailure (currently ~line 337)
private void applyProfileNoteFailure(UUID otherUserId, int token, String message, Exception error) {
    if (!isSelectedConversation(otherUserId, token)) {
        return;
    }
    // Sanitize: never expose raw JDBI / exception internals
    profileNoteStatusMessage.set(message);   // just the short message, no error.getMessage()
    profileNoteBusy.set(false);
}
```

---

## CB3 — Date locale: Hebrew "יוד 13" in conversation list

**File:** `src/main/java/datingapp/ui/screen/ChatController.java:53`

**Problem:** `DateTimeFormatter.ofPattern("dd MMM")` resolves to the JVM default locale.
On a system configured with the Hebrew locale, `MMM` renders as Hebrew month abbreviations.

**Fix:** Specify `Locale.ENGLISH` explicitly on both formatters:
```java
// Before
private static final DateTimeFormatter CONVERSATION_DATE_FORMAT =
    DateTimeFormatter.ofPattern("dd MMM");

// After
private static final DateTimeFormatter CONVERSATION_DATE_FORMAT =
    DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.ENGLISH);
```
Same fix needed for `THREAD_DATE_FORMAT` on line 54:
```java
private static final DateTimeFormatter THREAD_DATE_FORMAT =
    DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);
```

---

## CB4 — Green horizontal rendering artifact

**Location:** Chat center panel, appears around the boundary between the message list and
composer area.

**Probable cause:** JavaFX CSS border or outline on `header-bar` (applied to the composer
`HBox`) rendering a 1px colored border at top. The `header-bar` style is likely:
```css
.header-bar {
    -fx-border-color: <some-green>;
    -fx-border-width: 1 0 0 0;
}
```
Or it could be `typingIndicatorHost` `HBox` rendering its background color with an unwanted
border when `managed=false` but `visible=false` doesn't fully suppress layout participation.

**Investigation steps:**
1. Open the JavaFX CSS inspector (Scene Builder or runtime CSS debugging) and inspect the
   element at the green line's Y coordinate.
2. Check `header-bar` class definition in the app's CSS for any green color reference.
3. Check if removing `typingIndicatorHost` from the FXML makes the line disappear.

**Fix options:**
- If it's a CSS border: remove/change the offending color in the stylesheet.
- If it's `typingIndicatorHost`: add `maxHeight="0"` when not visible, or use a `StackPane`
  overlay instead of VBox child for the typing indicator.
- Add explicit `setClip()` on the message VBox region to prevent any child overflow.

---

## L1 — Note panel needs a maximum height constraint

**File:** `src/main/resources/fxml/chat.fxml:29`

**Problem:** The note panel `VBox` has no `maxPrefHeight`/`maxHeight`, so it can expand to
fill the entire sidebar, compressing the conversation list. Even without CB1, a long note or
long status message causes this.

**Fix:**
```xml
<VBox fx:id="notePanelContainer" spacing="10.0" styleClass="card-info-container"
      managed="false" visible="false" maxHeight="220.0">
```
Also, the `conversationListView` should have an explicit `minHeight` so it is never crushed:
```xml
<ListView fx:id="conversationListView" VBox.vgrow="ALWAYS" minHeight="120.0"/>
```

---

## L2 — Note status auto-dismiss

**File:** `ChatViewModel.java`

**Problem:** Note status messages (both success and failure) persist in the sidebar forever.
After saving, "Private note saved." stays visible until the user switches conversations.

**Fix:** Add a PlatformTimer-based auto-dismiss in `ChatViewModel`. After setting a non-null
status message, schedule a 3-second clear:
```java
// In applyLoadedProfileNote and save/delete success branches, after setting status:
asyncScope.dispatchToUi(() -> {
    profileNoteStatusMessage.set("Private note saved.");
    profileNoteBusy.set(false);
    // Auto-dismiss after 3s
    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
        javafx.util.Duration.seconds(3));
    pause.setOnFinished(_ -> profileNoteStatusMessage.set(null));
    pause.play();
});
```
This applies to all three outcomes: load failure, save success, delete success, save/delete
failure.

---

## L3 — Header toolbar visual grouping

**File:** `src/main/resources/fxml/chat.fxml:52–99`

**Problem:** All seven action buttons (refresh, friend-zone, graceful-exit, unmatch, block,
report) sit in an undifferentiated row. Users can't easily distinguish safe actions from
destructive ones.

**Fix:** Insert a thin vertical `Separator` between the relationship actions and the
safety/destructive actions:
```xml
<!-- After unmatchButton, before blockButton -->
<Separator orientation="VERTICAL" prefHeight="20.0" opacity="0.3"/>
```
This creates a clear visual boundary:
```
[Refresh] [FriendZone] [GracefulExit] [Unmatch] | [Block] [Report]
```

---

## L4 — Conversation list avatar shows initials

**File:** `ChatController.java:364` — `ConversationListCell` constructor

**Problem:** The avatar `StackPane` shows a generic account icon for every conversation.
Showing initials gives the list personality and makes it faster to scan.

**Implementation:** Replace `avatarIcon` with a `Label`:
```java
// Instead of FontIcon avatarIcon, use a Label:
private final Label avatarInitials = new Label();
// In constructor:
avatarInitials.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
avatarStack.getChildren().add(avatarInitials);

// In updateItem():
String name = item.otherUser().getName();
String initials = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
avatarInitials.setText(initials);
```
Assign a consistent background color per user based on `name.hashCode()` from a small palette:
```java
private static final String[] AVATAR_COLORS = {
    "#6366f1", "#ec4899", "#f59e0b", "#10b981", "#3b82f6", "#ef4444"
};
String color = AVATAR_COLORS[Math.abs(name.hashCode() % AVATAR_COLORS.length)];
avatarStack.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 20;");
```

---

## U1 — Note panel visual affordance

**File:** `src/main/resources/fxml/chat.fxml:29`

**Problem:** The note panel header "PRIVATE NOTE" is a small label that doesn't read as a
section header. Combined with its current position it looks like a floating overlay.

**Fix:** Add a top margin/padding to the note panel container to visually separate it from the
conversation list, and add a horizontal divider:
```xml
<VBox fx:id="notePanelContainer" spacing="10.0" styleClass="card-info-container"
      managed="false" visible="false" maxHeight="220.0">
  <VBox.margin>
    <Insets top="10"/>
  </VBox.margin>
  <Separator opacity="0.3"/>
  <Label styleClass="text-secondary conversation-section-label" text="PRIVATE NOTE"/>
  ...
</VBox>
```

---

## U2 — Message composer send button visual state

**File:** `ChatController.java:285` — `configureSendButtonState`

**Problem:** The send button just becomes `disabled` when inactive but has no visual feedback
about *why* (no conversation selected vs. blank text vs. sending in progress).

**Fix:** Add a `Tooltip` that changes based on why the button is disabled:
```java
sendButton.disableProperty().addListener((obs, wasDisabled, isNowDisabled) -> {
    if (isNowDisabled) {
        Tooltip t = new Tooltip(
            viewModel.selectedConversationProperty().get() == null
                ? "Select a conversation first"
                : viewModel.sendingProperty().get() ? "Sending..." : "Type a message first"
        );
        Tooltip.install(sendButton, t);
    } else {
        Tooltip.uninstall(sendButton, sendButton.getTooltip());
    }
});
```

---

## U3 — Empty conversation list state

**File:** `ChatController.java` — `initialize()`

**Problem:** When `conversationListView` is empty (no matches, no conversations), it shows a
blank grey list with no feedback. The empty state `VBox` in center only shows when no
conversation is *selected*, not when the list itself is empty.

**Fix:** Bind a placeholder label to the conversation list using JavaFX's built-in
`setPlaceholder`:
```java
// In initialize():
Label noConversationsLabel = new Label("No conversations yet.\nMatch with someone to start.");
noConversationsLabel.getStyleClass().add("text-secondary");
noConversationsLabel.setWrapText(true);
noConversationsLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
conversationListView.setPlaceholder(noConversationsLabel);
```

---

## U4 — Scroll-to-bottom stability on rapid poll updates

**File:** `ChatController.java:129–151`

**Problem:** The `activeMessagesListener` scrolls to the bottom on every `ListChangeListener`
event, including poll-driven updates that arrive while the user is scrolling up to read older
messages. This interrupts reading history.

**Fix:** Only scroll to bottom when:
1. A new conversation is selected (initial open)
2. The current user sends a message (already handled in `handleSendSuccess`)
3. A new message arrives AND the list was already scrolled to the bottom

```java
// Track whether user is at bottom:
private boolean isAtBottom = true;

// In initialize():
messageListView.setOnScroll(e -> {
    // Check if scrolled near bottom (within 50px)
    ScrollBar sb = (ScrollBar) messageListView.lookup(".scroll-bar:vertical");
    if (sb != null) {
        isAtBottom = sb.getValue() >= sb.getMax() - 0.05;
    }
});

// In activeMessagesListener:
if (change.next() && change.wasAdded() && isAtBottom) {
    scrollToLatestMessage();
}
```

---

## R1 — Investigate and fix note binding root cause

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:496`
(see **CB1** above — this is the main action item)

After the `@BindMethods` fix, verify with a targeted test:
- `ProfileNoteRepositoryTest` (or add one if absent): save a note, retrieve it, confirm round-trip.
- Confirm the existing note-related tests in `ValidationServiceTest` still pass.

---

## R2 — ViewModel `selectedConversation` race on rapid switching

**File:** `ChatViewModel.java:170–191`

**Problem:** The `selectionListener` guards against same-conversation re-selection using
`oldVal.conversation().getId().equals(newVal.conversation().getId())`, but `updateConversations`
calls `restoreSelection` which may fire `selectedConversation.set(restored)` even when the
restored object is equal by ID but not by reference. If `sameConversationPreview` returns
`false` (due to unread count or last message change), a selection re-fire triggers a redundant
`loadMessages` for the already-visible conversation.

**Fix:** In `restoreSelection`, only fire `set()` when the conversation is genuinely different:
```java
// Current (line ~709):
if (currentlySelected == null || !sameConversationPreview(currentlySelected, restored)) {
    selectedConversation.set(restored);
}
```
This is already correct in structure, but the `sameConversationPreview` comparison doesn't
check `otherUser` identity. If `restored` has the same conversation ID but a refreshed
`otherUser` instance, the equality check passes incorrectly. Add a user-ID check:
```java
private static boolean sameConversationPreview(ConversationPreview current, ConversationPreview candidate) {
    // ... existing checks ...
    // Also verify the other user matches (prevents stale-user re-fires)
    return Objects.equals(current.otherUser().getId(), candidate.otherUser().getId())
        && Objects.equals(currentMessageId, candidateMessageId)
        && current.unreadCount() == candidate.unreadCount();
}
```

---

## R3 — Note save does not clear stale status from a previous conversation

**File:** `ChatViewModel.java:357` — `clearProfileNoteState`

**Problem:** When switching from conversation A (where a note save error occurred) to
conversation B, `clearProfileNoteState` increments `noteLoadToken` and clears
`profileNoteStatusMessage`. However, if the `loadProfileNoteFor` background task for B
completes before the token increment resolves on the FX thread, the stale error could
briefly flash.

**Fix (already partially mitigated):** The existing token check `isSelectedConversation` in
`applyLoadedProfileNote` should prevent stale updates. Verify that `clearProfileNoteState`
is called *before* `loadProfileNoteFor` in the `selectionListener`:
```java
// In selectionListener (line ~179), ensure order:
clearProfileNoteState();          // clear first
loadProfileNoteFor(newVal.otherUser()); // then load
```
(Currently `loadProfileNoteFor` is called at line 182 which is after `clearProfileNoteState`
is not called — `clearProfileNoteState` is only called on `newVal == null`. It should also
be called at the start of a new conversation selection.)

---

## Implementation order

| Step | Items                                  | Files                  | Priority |
|------|----------------------------------------|------------------------|----------|
| 1    | **CB1** — `@BindMethods` fix           | `JdbiUserStorage.java` | CRITICAL |
| 2    | **CB2B** — sanitize error message      | `ChatViewModel.java`   | HIGH     |
| 3    | **CB2A** — constrain status label      | `chat.fxml`            | HIGH     |
| 4    | **CB3** — locale-safe date formatters  | `ChatController.java`  | HIGH     |
| 5    | **CB4** — investigate green line       | CSS + `chat.fxml`      | HIGH     |
| 6    | **L1** — note panel max height         | `chat.fxml`            | MEDIUM   |
| 7    | **L2** — note status auto-dismiss      | `ChatViewModel.java`   | MEDIUM   |
| 8    | **L3** — toolbar separator             | `chat.fxml`            | LOW      |
| 9    | **L4** — initials avatar               | `ChatController.java`  | LOW      |
| 10   | **U1** — note panel divider            | `chat.fxml`            | LOW      |
| 11   | **U3** — conversation list placeholder | `ChatController.java`  | MEDIUM   |
| 12   | **U4** — smart scroll-to-bottom        | `ChatController.java`  | MEDIUM   |
| 13   | **R1** — note round-trip test          | test files             | MEDIUM   |
| 14   | **R2** — selection race fix            | `ChatViewModel.java`   | LOW      |
| 15   | **R3** — clear state before load       | `ChatViewModel.java`   | LOW      |

---

## Files to change

| File                                | Changes                                                                                 |
|-------------------------------------|-----------------------------------------------------------------------------------------|
| `storage/jdbi/JdbiUserStorage.java` | CB1: `@BindBean` → `@BindMethods`                                                       |
| `ui/viewmodel/ChatViewModel.java`   | CB2B (sanitize error), L2 (auto-dismiss), R2 (selection race), R3 (clear order)         |
| `src/main/resources/fxml/chat.fxml` | CB2A (status label max width), L1 (note panel max height), L3 (separator), U1 (divider) |
| `ui/screen/ChatController.java`     | CB3 (locale formatters), L4 (initials avatar), U3 (list placeholder), U4 (smart scroll) |

---

## Verification

After each step:
```bash
mvn spotless:apply verify
```

Targeted tests after CB1:
- Confirm any existing `ProfileNote` storage or use-case tests pass
- Manual smoke test: open Chat → select conversation → save note → confirm "Private note saved." appears (not a JDBI error)
- Confirm timestamp in conversation list shows `HH:mm` or English `dd MMM` regardless of system locale
