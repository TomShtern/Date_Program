# Phase 3: Quick Wins

> **Enhancements**: E09 (Form Validation), E10 (Keyboard Navigation), E14 (Status Indicators)
> **Duration**: ~2 hours
> **Status**: [ ] Not Started
> **Prerequisites**: Phase 1, Phase 2 Complete

---

## Goal

Implement small but noticeable UX improvements that enhance usability with minimal effort.

---

## Deliverables

| Type | File | Description |
|------|------|-------------|
| [MOD] | `ProfileController.java` | Validation with shake/red border |
| [MOD] | `LoginController.java` | Keyboard shortcuts |
| [MOD] | `MatchingController.java` | Arrow key navigation |
| [MOD] | `theme.css` | Status dot styles, validation styles |
| [MOD] | `matches.fxml` | Status indicator elements |
| [MOD] | `chat.fxml` | Status indicator elements |

---

## E09: Real-time Form Validation

**Description**: Show validation errors with red borders and shake animations.

### CSS Styles

```css
/* ============================================
   Form Validation Styles
   ============================================ */
.text-field.error,
.text-area.error {
    -fx-border-color: #ef4444;
    -fx-border-width: 1.5;
    -fx-effect: dropshadow(gaussian, rgba(239, 68, 68, 0.3), 8, 0, 0, 0);
}

.text-field.success,
.text-area.success {
    -fx-border-color: #10b981;
    -fx-border-width: 1.5;
}

.validation-error-label {
    -fx-font-size: 11px;
    -fx-text-fill: #ef4444;
    -fx-padding: 4 0 0 4;
}

.validation-success-label {
    -fx-font-size: 11px;
    -fx-text-fill: #10b981;
}
```

### Validation Helper

```java
public class ValidationHelper {

    public static boolean validateRequired(TextField field, Label errorLabel) {
        if (field.getText() == null || field.getText().trim().isEmpty()) {
            markError(field, errorLabel, "This field is required");
            return false;
        }
        markSuccess(field, errorLabel);
        return true;
    }

    public static boolean validateEmail(TextField field, Label errorLabel) {
        String email = field.getText();
        if (email == null || !email.matches("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            markError(field, errorLabel, "Please enter a valid email");
            return false;
        }
        markSuccess(field, errorLabel);
        return true;
    }

    public static boolean validateMinLength(TextField field, Label errorLabel, int min) {
        if (field.getText() == null || field.getText().length() < min) {
            markError(field, errorLabel, "Minimum " + min + " characters required");
            return false;
        }
        markSuccess(field, errorLabel);
        return true;
    }

    private static void markError(TextField field, Label errorLabel, String message) {
        field.getStyleClass().removeAll("error", "success");
        field.getStyleClass().add("error");
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        }
        AnimationHelper.playShake(field);
    }

    private static void markSuccess(TextField field, Label errorLabel) {
        field.getStyleClass().removeAll("error", "success");
        field.getStyleClass().add("success");
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
    }

    public static void clearValidation(TextField field, Label errorLabel) {
        field.getStyleClass().removeAll("error", "success");
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
    }
}
```

### ProfileController Integration

```java
@FXML private TextField locationField;
@FXML private TextArea bioArea;
@FXML private Label locationError;
@FXML private Label bioError;

@FXML
private void handleSave() {
    boolean valid = true;

    // Validate location
    if (!ValidationHelper.validateRequired(locationField, locationError)) {
        valid = false;
    }

    // Validate bio minimum length
    if (bioArea.getText().length() < 10) {
        bioArea.getStyleClass().add("error");
        AnimationHelper.playShake(bioArea);
        valid = false;
    }

    if (!valid) {
        ToastService.getInstance().showError("Please fix the errors above");
        return;
    }

    // Proceed with save
    viewModel.save();
    ToastService.getInstance().showSuccess("Profile saved!");
}
```

---

## E10: Keyboard Navigation

**Description**: Support keyboard shortcuts for common actions.

### Keyboard Shortcut Map

| Key | Action | Screen |
|-----|--------|--------|
| `←` | Pass candidate | Matching |
| `→` | Like candidate | Matching |
| `↑` | Super Like | Matching |
| `Ctrl+Z` | Undo last action | Matching |
| `Enter` | Confirm/Submit | All forms |
| `Escape` | Go back / Close modal | All |
| `Ctrl+S` | Save | Profile, Preferences |

### MatchingController Implementation

```java
@Override
public void initialize(URL location, ResourceBundle resources) {
    // ... existing code ...

    setupKeyboardShortcuts();
}

private void setupKeyboardShortcuts() {
    rootPane.setOnKeyPressed(e -> {
        if (!candidateCard.isVisible()) return; // No candidate shown

        switch (e.getCode()) {
            case LEFT -> handlePass();
            case RIGHT -> handleLike();
            case UP -> handleSuperLike();
            case Z -> {
                if (e.isControlDown()) handleUndo();
            }
            case ESCAPE -> handleBack();
        }
    });

    // Ensure root pane can receive keyboard events
    rootPane.setFocusTraversable(true);
    Platform.runLater(() -> rootPane.requestFocus());
}
```

### Global Keyboard Handling (DatingApp.java)

```java
scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
    if (e.getCode() == KeyCode.ESCAPE) {
        // Check if modal is open, close it
        // Otherwise, navigate back if possible
    }

    if (e.isControlDown() && e.getCode() == KeyCode.S) {
        // Trigger save on current screen if applicable
        e.consume();
    }
});
```

---

## E14: Online/Offline Status Indicators

**Description**: Show green/gray dots on avatars to indicate user status.

### CSS Styles

```css
/* ============================================
   Status Indicators
   ============================================ */
.status-dot {
    -fx-min-width: 12;
    -fx-min-height: 12;
    -fx-max-width: 12;
    -fx-max-height: 12;
    -fx-background-radius: 6;
    -fx-border-color: #0f172a;
    -fx-border-width: 2;
    -fx-border-radius: 6;
}

.status-online {
    -fx-background-color: #10b981;
    -fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.6), 6, 0.4, 0, 0);
}

.status-offline {
    -fx-background-color: #64748b;
}

.status-away {
    -fx-background-color: #f59e0b;
}

/* Container for avatar + status dot */
.avatar-status-container {
    -fx-alignment: bottom-right;
}
```

### FXML Pattern for Status Dots

```xml
<!-- In matches.fxml or chat.fxml -->
<StackPane styleClass="avatar-status-container">
    <!-- Avatar -->
    <StackPane styleClass="match-avatar-container">
        <FontIcon iconLiteral="mdi2a-account" iconSize="32" iconColor="#10b981"/>
    </StackPane>
    <!-- Status Dot (positioned at bottom-right) -->
    <Region fx:id="statusDot" styleClass="status-dot, status-online">
        <StackPane.margin>
            <Insets right="2" bottom="2"/>
        </StackPane.margin>
    </Region>
</StackPane>
```

### Controller Logic

```java
private void updateStatusIndicator(Region statusDot, boolean isOnline) {
    statusDot.getStyleClass().removeAll("status-online", "status-offline", "status-away");

    if (isOnline) {
        statusDot.getStyleClass().add("status-online");
    } else {
        statusDot.getStyleClass().add("status-offline");
    }
}

// For simulated "away" status (user inactive)
private void setAwayStatus(Region statusDot) {
    statusDot.getStyleClass().removeAll("status-online", "status-offline", "status-away");
    statusDot.getStyleClass().add("status-away");
}
```

---

## Verification Checklist

- [ ] Invalid form fields show red border immediately
- [ ] Form submission with errors triggers shake animation
- [ ] Error messages display below invalid fields
- [ ] Valid fields show green success border
- [ ] Keyboard shortcuts work in matching screen (← → ↑ keys)
- [ ] `Ctrl+Z` undoes last like/pass action
- [ ] `Escape` navigates back or closes modals
- [ ] `Enter` submits forms when focused
- [ ] Online status dots visible on match avatars (green)
- [ ] Offline status dots display gray
- [ ] Status dots properly positioned at bottom-right of avatars
- [ ] `mvn clean compile` succeeds
- [ ] `mvn test` passes

---

## Accessibility Notes

- Keyboard navigation improves accessibility for non-mouse users
- Status indicators use both color AND border for colorblind users
- Consider adding `aria-label` equivalents via tooltips
- Tab order should follow logical flow through forms
