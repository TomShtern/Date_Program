# Chat Screen — UI/UX Improvement Plan
_Created: 2026-03-13_

## Implementation status (updated 2026-03-13)

- ✅ **B1** fixed: composer counter initializes to `0/1000`.
- ✅ **B2 + L1** fixed: private note panel moved to left sidebar and bound to selected conversation visibility.
- ✅ **B3** fixed: conversation auto-scrolls to latest message on open and when message list updates.
- ✅ **L2** fixed: friend zone / graceful exit / unmatch are icon+tooltip buttons.
- ✅ **L3** fixed: inline presence status label removed; presence dot now uses tooltip.
- ✅ **U1** implemented: conversation list shows last-message timestamp (`HH:mm` for today, `dd MMM` otherwise).
- ✅ **U2** implemented: conversation snippet prefixes current-user messages with `You: `.
- ✅ **U3** implemented: thread date separators (`Today`, `Yesterday`, or date label) rendered in message cells.
- ✅ **U4** implemented: message bubble width is responsive (65% of list width).
- ✅ **U5** implemented: composer hint text added (`Enter to send · Shift+Enter for new line`).
- ✅ **U6** verified as already implemented before this session (`Matches` screen `Message` action routes to `Chat` with context).
- ✅ **PO1** implemented: loading indicator shown in message area during load.
- ✅ **PO2** implemented: opening conversation now refreshes conversation previews/unread immediately after mark-as-read.
- ✅ **PO3** implemented: `No messages yet` snippet uses muted italic styling.
- ✅ **PO4** fixed: `ChatController` now wires `viewModel.setErrorHandler(UiFeedbackService::showError)`.

### Verification run

- ✅ Targeted tests passed:
  - `ChatControllerTest`
  - `ChatViewModelTest`
  - `MatchesControllerTest`
- ✅ Full quality gate passed: `mvn spotless:apply verify`
  - Build success
  - Tests: `1094` run, `0` failures, `0` errors, `2` skipped
  - Spotless, Checkstyle, PMD, JaCoCo all passing

## Context

The chat screen is now functional (seeded matches + conversation confirmed working),
but a first live test revealed a range of layout, logic, and UX issues. This plan
captures every identified problem and the concrete fix for each, grouped by priority.

---

## Confirmed bugs from live test

### B1 — Character counter shows `1000/1000` on empty composer
**File:** `ChatController.java:169`
**Problem:** Initial label text is hard-coded to `MAX_LENGTH + "/" + MAX_LENGTH`.
The correct initial state is `"0/" + MAX_LENGTH`.
```java
// Wrong
messageLengthLabel.setText(Message.MAX_LENGTH + "/" + Message.MAX_LENGTH);
// Fix
messageLengthLabel.setText("0/" + Message.MAX_LENGTH);
```

### B2 — Private Note section sits above the message list
**File:** `chat.fxml` lines 72–80
**Problem:** The `PRIVATE NOTE` card is the **first** child of `chatContainer`, above the
`messageListView`. This pushes the message thread ~150 px down, crops the bottom of the
conversation, and makes the screen feel like a notes app with a chat bolted on.

**Fix options (choose one per P1 section below):**
- Move the note card below the message list (simplest)
- Collapse it into a drawer/accordion opened by a button in the header
- Relocate it to the left sidebar under the conversation list

### B3 — Conversation does not scroll to bottom on open
**File:** `ChatController.java` — `handleConversationSelection()`
**Problem:** When a conversation is selected, `messageListView` does not scroll to the
most recent message. The scroll-to-bottom logic only runs in `handleSendSuccess()`.
```java
// Add to handleConversationSelection(), after chatContainer becomes visible:
Platform.runLater(() -> {
    if (!viewModel.getActiveMessages().isEmpty()) {
        messageListView.scrollTo(viewModel.getActiveMessages().size() - 1);
    }
});
```
Also needs to fire whenever `activeMessages` changes (new poll arrives):
```java
// In initialize(), add a list change listener:
viewModel.getActiveMessages().addListener((javafx.collections.ListChangeListener<Message>) c -> {
    if (c.next()) {
        messageListView.scrollTo(viewModel.getActiveMessages().size() - 1);
    }
});
```

---

## P1 — Layout (immediately visible problems)

### L1 — Relocate the Private Note panel
**Recommended approach:** Move the note panel to the **left sidebar**, below the
conversation list. It only makes sense when a conversation is selected, so bind its
`visible`/`managed` to `selectedConversationProperty().isNotNull()`.

FXML structure change in `<left>`:
```xml
<VBox prefWidth="320.0" styleClass="sidebar">
  <Label text="CONVERSATIONS" styleClass="..."/>
  <ListView fx:id="conversationListView" VBox.vgrow="ALWAYS"/>

  <!-- NOTE PANEL — only visible when a conversation is selected -->
  <VBox fx:id="notePanelContainer" spacing="8.0" styleClass="card-info-container"
        managed="false" visible="false">
    <Label text="PRIVATE NOTE" styleClass="text-secondary conversation-section-label"/>
    <TextArea fx:id="profileNoteArea" prefRowCount="3" .../>
    <HBox spacing="10.0">
      <Button fx:id="saveProfileNoteButton" text="Save note" .../>
      <Button fx:id="deleteProfileNoteButton" text="Delete" .../>
      <Label fx:id="profileNoteStatusLabel" .../>
    </HBox>
  </VBox>
</VBox>
```
Wire visibility in `ChatController.initialize()`:
```java
notePanelContainer.visibleProperty().bind(viewModel.selectedConversationProperty().isNotNull());
notePanelContainer.managedProperty().bind(viewModel.selectedConversationProperty().isNotNull());
```

### L2 — Replace text action buttons with icon+tooltip buttons
**File:** `chat.fxml` lines 52–54
**Problem:** `"Friend zone"`, `"Graceful exit"`, `"Unmatch"` are plain text buttons in a
crowded header HBox, causing them to truncate to `"Fr..."`, `"Gr..."`.
All other action buttons (Block, Report) already use icon+tooltip correctly.

**Fix:** Convert all three to icon-only buttons with tooltips:
```xml
<Button fx:id="friendZoneButton" onAction="#handleRequestFriendZone" styleClass="button-transparent">
  <graphic><FontIcon iconLiteral="mdi2h-heart-circle" iconSize="20" iconColor="white"/></graphic>
  <tooltip><Tooltip text="Request Friend Zone"/></tooltip>
</Button>
<Button fx:id="gracefulExitButton" onAction="#handleGracefulExit" styleClass="button-transparent">
  <graphic><FontIcon iconLiteral="mdi2d-door-open" iconSize="20" iconColor="white"/></graphic>
  <tooltip><Tooltip text="Graceful Exit"/></tooltip>
</Button>
<Button fx:id="unmatchButton" onAction="#handleUnmatch" styleClass="button-transparent">
  <graphic><FontIcon iconLiteral="mdi2h-heart-broken" iconSize="20" iconColor="#f59e0b"/></graphic>
  <tooltip><Tooltip text="Unmatch"/></tooltip>
</Button>
```
Also remove the `text` property from these buttons in the controller's `configureSendButtonState` binding —
the buttons should be sized by icon only.

### L3 — Presence status label clutters the header
**File:** `chat.fxml` line 42
**Problem:** `chatPresenceStatusLabel` showing `"Presence indicators are disabled in ..."` is
rendered inline in the header HBox, squeezing the name label.

**Fix:** Replace the inline label with a tooltip on the presence dot itself:
```java
// In updatePresenceIndicator(), set a Tooltip on chatPresenceDot instead of a sibling label.
Tooltip.install(chatPresenceDot, new Tooltip(viewModel.presenceUnavailableMessageProperty().get()));
```
Remove `chatPresenceStatusLabel` from the FXML. The dot is already hidden when presence
is `UNKNOWN`, so there is nothing to display when it is disabled.

---

## P2 — UX improvements

### U1 — Add last-message timestamp to conversation list cells
**File:** `ChatController.java` — `ConversationListCell`
**Problem:** The cell shows name + snippet but no time. Users cannot tell if a
conversation is from today or a week ago.

**Implementation:** Add a `Label timeLabel` to `topRow` (right-aligned):
```java
private final Label timeLabel = new Label();
// In constructor:
timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-secondary;");
topRow.getChildren().addAll(nameLabel, timeLabel, unreadBadge); // timeLabel after name, before badge
// In updateItem():
String time = item.lastMessage()
    .map(m -> m.createdAt().atZone(ZoneId.systemDefault()))
    .map(zdt -> {
        LocalDate today = LocalDate.now();
        if (zdt.toLocalDate().equals(today)) return zdt.format(DateTimeFormatter.ofPattern("HH:mm"));
        return zdt.format(DateTimeFormatter.ofPattern("dd MMM"));
    })
    .orElse("");
timeLabel.setText(time);
```

### U2 — Prefix snippet with sender indicator
**File:** `ChatController.java` — `ConversationListCell.updateItem()`
**Problem:** The last-message snippet shows raw content. Users can't tell if it was sent
by them or the other person.

**Implementation:**
```java
String senderPrefix = item.lastMessage()
    .map(m -> m.senderId().equals(currentUserId) ? "You: " : "")
    .orElse("");
snippetLabel.setText(senderPrefix + snippet);
```
`currentUserId` needs to be passed into the cell or resolved via the ViewModel.
Simplest approach: pass a `Supplier<UUID>` into `ConversationListCell` constructor.

### U3 — Date separators in the message thread
**Problem:** A long conversation shows all messages in one undivided stream.
Adding date group headers ("Today", "Yesterday", "12 Mar") significantly improves readability.

**Implementation options:**
- **Option A (simple):** Pre-process the message list before setting it on `messageListView`,
  inserting marker objects. Requires a union type (sealed interface with `MessageItem` and
  `DateSeparatorItem` variants) and a combined cell factory.
- **Option B (lightweight):** In `MessageListCell.updateItem()`, compare `msg.createdAt().toLocalDate()`
  with the previous item's date. If different, render a date header above the bubble inside
  the same cell. Works without changing the data model.

Recommended: **Option B** — no data-model changes, no extra observable list manipulation.

### U4 — Responsive message bubble width
**File:** `ChatController.java:414`
**Problem:** `bubble.setMaxWidth(300)` is hardcoded and does not adapt to window size.
On wide windows bubbles are too narrow; on narrow windows they may overflow.

**Fix:** Bind bubble max width to a fraction of the available list width:
```java
// In MessageListCell constructor, after messageListView is available:
bubble.maxWidthProperty().bind(messageListView.widthProperty().multiply(0.65));
```
This requires passing a reference to `messageListView` into the cell, or using a
`ChangeListener` on the list width. The 0.65 ratio matches common messaging app conventions.

### U5 — Composer send-on-Enter already works, but needs Shift+Enter hint
**File:** `chat.fxml`
**Problem:** There is no visible hint that Shift+Enter inserts a newline.
Add a prompt or subtitle below the `TextArea`:
```xml
<Label text="Enter to send · Shift+Enter for new line"
       styleClass="text-secondary" style="-fx-font-size: 10px;"/>
```

### U6 — Empty state for matched-but-no-conversation
**Problem:** Batel Oron has an active match with Adam but no conversation. There is no
UI path to start a fresh conversation with her (the chat screen only shows conversations,
not all matches).

**Options:**
- Add a `"New Chat"` button in the left sidebar header that opens a match picker
- Show matched users without conversations as greyed-out items in the conversation list with
  a `"Say hello"` button
- Rely on the Matches screen → tap profile → tap "Message" to open a conversation (this
  flow already exists via `openConversationWithUser`)

**Simplest fix:** Ensure the Matches screen has a visible "Message" action on each match card
and that it navigates to the Chat screen with the right `NavigationContext`.

---

## P3 — Polish

### PO1 — Loading indicator while messages fetch
When switching conversations, show a `ProgressIndicator` in the message area until the
async load completes. Bind to `viewModel.loadingProperty()`.

### PO2 — Unread badge resets on open
Confirm that `markAsRead` is called (via `LoadConversationQuery.markAsRead = true`) when
a conversation is opened. Currently the unread badge may persist until the next poll cycle
refreshes the conversation list.

### PO3 — Conversation list "No messages yet" styling
The cell renders `"No messages yet"` in the same style as a real snippet. Differentiate
with italic or muted style so it is visually clear the conversation is fresh.

### PO4 — Error feedback wired on send failure
**File:** `ChatController.java`
**Problem:** `viewModel.setErrorHandler(...)` is never called in `initialize()`, so send
failures silently log a warning instead of displaying anything to the user.
```java
// Add to initialize():
viewModel.setErrorHandler(errorMessage ->
    UiFeedbackService.showError(errorMessage));
```

---

## Implementation order

| Step | Issues                                                   | Effort    |
|------|----------------------------------------------------------|-----------|
| 1    | B1 (counter), B3 (scroll-to-bottom), PO4 (error handler) | < 1 hour  |
| 2    | B2 + L1 (private note relocation)                        | 1–2 hours |
| 3    | L2 (icon buttons), L3 (presence label)                   | < 1 hour  |
| 4    | U1 (timestamps), U2 (sender prefix), U5 (hint label)     | 1 hour    |
| 5    | U4 (responsive bubbles)                                  | 30 min    |
| 6    | U3 (date separators)                                     | 1–2 hours |
| 7    | U6 (new conversation entry point)                        | 2–3 hours |
| 8    | PO1–PO3                                                  | 1 hour    |

---

## Files to change

| File                  | Changes                                                                                                                        |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `chat.fxml`           | Relocate private note, convert buttons to icons, remove presence label                                                         |
| `ChatController.java` | Fix counter init, add scroll-to-bottom, wire error handler, responsive bubbles, timestamp/sender in list cell, date separators |
| `ChatViewModel.java`  | No changes needed for P0/P1                                                                                                    |
