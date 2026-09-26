# Plan P1-C — Remove Presence "Unavailable" Text from Chat Screen

> **Phase:** 1 — Finish & Polish (current priority)
> **Effort:** Verified no-op (no code changes required)
> **Blocked by:** Nothing — fully independent
> **Source-verified:** 2026-03-13 against actual code

---

## Verification & Progress (updated 2026-03-13)

- [x] Re-verified `ChatController` initialize flow in current branch.
- [x] Confirmed no `bindPresenceAvailability()` invocation exists.
- [x] Confirmed `chatPresenceStatusLabel` is not present in `chat.fxml`.
- [x] Confirmed tests contain no assertions tied to removed presence label visibility.
- [x] No code/test changes required for this plan item.
- [x] Full quality gate completed successfully.

Verification result recorded (2026-03-13): `BUILD SUCCESS`, `Tests run: 1100, Failures: 0, Errors: 0, Skipped: 2`.

---

## Goal

Stop showing an "unsupported / unavailable" presence message in the chat header.
The presence dot and label should remain invisible until presence is actually
implemented in a future phase. Right now they make the UI look broken.

---

## Current State

> Historical note: the subsection below describes the original issue snapshot. In the current branch (re-verified 2026-03-13), `chatPresenceStatusLabel` is already removed and no `bindPresenceAvailability()` call site exists.

### The Problem

The chat header contains two presence elements defined in FXML:

**File:** `src/main/resources/fxml/chat.fxml` lines 41–42
```xml
<Region fx:id="chatPresenceDot" managed="false" visible="false" styleClass="status-dot"/>
<Label fx:id="chatPresenceStatusLabel" managed="false" visible="false" styleClass="text-secondary"/>
```

Both start correctly hidden (`managed="false" visible="false"`).

**File:** `src/main/java/datingapp/ui/screen/ChatController.java` lines 260–278

The `bindPresenceAvailability()` method is called during initialisation. It binds the
label's `managed` and `visible` properties to `presenceSupported.not()`:

```java
private void bindPresenceAvailability() {
    if (chatPresenceStatusLabel == null) return;
    chatPresenceStatusLabel.textProperty().bind(Bindings.createStringBinding(
        () -> viewModel.presenceSupportedProperty().get()
            ? ""
            : viewModel.presenceUnavailableMessageProperty().get(),
        viewModel.presenceSupportedProperty(),
        viewModel.presenceUnavailableMessageProperty()));
    chatPresenceStatusLabel.managedProperty()
        .bind(viewModel.presenceSupportedProperty().not());   // ← NOT of false = true
    chatPresenceStatusLabel.visibleProperty()
        .bind(viewModel.presenceSupportedProperty().not());   // ← NOT of false = true
}
```

Because `presenceSupported` is always `false` (the feature is flag-gated off via
`datingapp.ui.presence.enabled`), `.not()` evaluates to `true` — the label becomes
visible and managed, overriding the FXML defaults, and displays the "unavailable" message.

### What Is Safe (Do Not Touch)

**Line 182:**
```java
addSubscription(viewModel.presenceStatusProperty().subscribe(this::updatePresenceIndicator));
```

The `updatePresenceIndicator()` method (lines 222–242) already handles `UNKNOWN` status
correctly — it keeps the dot hidden. This subscription is safe and stays as-is.

---

## Change Required

### File: `src/main/java/datingapp/ui/screen/ChatController.java`

**Verified call site status (2026-03-13):**
- `ChatController.initialize()` is the controller binding method.
- There is currently **no invocation** of `bindPresenceAvailability()` in this class (0 matches).
- Presence wiring in `initialize()` currently starts at:
  - `src/main/java/datingapp/ui/screen/ChatController.java:247` (`addSubscription(viewModel.presenceStatusProperty().subscribe(this::updatePresenceIndicator));`)

Because no call site exists, no code change is required for this plan item.

If a future branch re-introduces a `bindPresenceAvailability()` call in `initialize()`, keep it commented with:

```java
// Presence feature is not yet implemented — presenceSupported is always false,
// which would cause bindPresenceAvailability() to show an "unavailable" label.
// Both presence elements default to hidden in FXML (managed=false, visible=false).
// Re-enable this call in Phase 3 when a real UiPresenceDataAccess implementation exists.
// bindPresenceAvailability();
```

Leaving the call commented out (rather than deleted) makes the intent obvious to future
agents and to you when you return to this screen during Phase 3.

There is currently no `bindPresenceAvailability()` call site in `ChatController`.
No controller code change is needed for this item.

---

## Files to Modify

| File                                                    | Change                                                                           |
|---------------------------------------------------------|----------------------------------------------------------------------------------|
| `src/main/java/datingapp/ui/screen/ChatController.java` | No change required (verified: no `bindPresenceAvailability()` invocation exists) |

No FXML changes, no ViewModel changes, no CSS changes.

---

## Test Requirements

Search `src/test/java/` for `ChatControllerTest`. Check whether any test asserts on
`chatPresenceStatusLabel.isVisible()` or `chatPresenceStatusLabel.isManaged()`.

**Definitive verification (2026-03-13):**
- `src/test/java/datingapp/ui/screen/ChatControllerTest.java` exists.
- No assertions were found for `chatPresenceStatusLabel.isVisible()` or `chatPresenceStatusLabel.isManaged()`.
- No test update was required for this item.

Run `mvn spotless:apply && mvn verify` to confirm no regressions.

---

## Gotchas

- **Verified current state:** no `bindPresenceAvailability()` invocation exists in
  `ChatController.initialize()` as of 2026-03-13.

- **Do not modify `updatePresenceIndicator()`.** It already handles `UNKNOWN` correctly
  and the `presenceStatusProperty` subscription in `initialize()` (line 247) stays active.

- **Do not modify `chat.fxml`.** The FXML defaults (`managed="false" visible="false"`)
  are already correct. Once the binding is removed, those defaults take effect.

- **`presenceUnavailableMessageProperty()`** in `ChatViewModel` is backed by
  `NoOpUiPresenceDataAccess.unsupportedReason()`. This returns a human-readable string
  like "Presence not available". Once the call is commented out, this value is never
  bound to any visible label, so the string content doesn't matter.

---

## Future: How to Re-Enable Presence (Phase 3)

When a real `UiPresenceDataAccess` implementation exists:
1. Remove the comment and uncomment `bindPresenceAvailability();`
2. The label and dot will automatically start working
3. `updatePresenceIndicator()` will show/hide the dot based on real status (ONLINE/AWAY/OFFLINE)
4. The "unavailable" label logic in `bindPresenceAvailability()` will correctly hide
   the label because `presenceSupported` will then be `true`
