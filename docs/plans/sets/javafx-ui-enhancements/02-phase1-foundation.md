# Phase 1: Foundation & Infrastructure

> **Enhancements**: E06 (Toast System), E16 (Extract Inline Styles), E20 (Custom Tooltips)
> **Duration**: ~2 hours
> **Status**: [ ] Not Started

---

## Goal

Create reusable utility classes and clean up code to establish a solid foundation for subsequent phases.

---

## Deliverables

| Type | File | Description |
|------|------|-------------|
| [NEW] | `ui/util/ToastService.java` | Toast notification system |
| [NEW] | `resources/css/toast.css` | Toast styling (or add to theme.css) |
| [MOD] | All `.fxml` files | Extract inline `style=""` to CSS classes |
| [MOD] | `theme.css` | Custom tooltip styles + new extracted classes |

---

## E06: Toast/Snackbar Notification System

**Description**: Display non-blocking success/error/info messages that auto-dismiss.

### Implementation Steps

1. Create `ToastService.java` as a singleton
2. Add toast container to root StackPane of main scenes
3. Implement slide-in/fade-out animations
4. Support levels: SUCCESS, ERROR, WARNING, INFO

### Code: `ToastService.java`

```java
package datingapp.ui.util;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

public class ToastService {
    private static ToastService instance;
    private StackPane toastContainer;

    private ToastService() {}

    public static ToastService getInstance() {
        if (instance == null) instance = new ToastService();
        return instance;
    }

    public void setContainer(StackPane container) {
        this.toastContainer = container;
    }

    public void showSuccess(String message) {
        show(message, ToastLevel.SUCCESS, Duration.seconds(3));
    }

    public void showError(String message) {
        show(message, ToastLevel.ERROR, Duration.seconds(5));
    }

    public void showWarning(String message) {
        show(message, ToastLevel.WARNING, Duration.seconds(4));
    }

    public void showInfo(String message) {
        show(message, ToastLevel.INFO, Duration.seconds(3));
    }

    private void show(String message, ToastLevel level, Duration duration) {
        if (toastContainer == null) return;

        HBox toast = createToast(message, level);
        toastContainer.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 30, 0));

        // Entrance animation
        toast.setOpacity(0);
        toast.setTranslateY(50);

        ParallelTransition entrance = new ParallelTransition(
            new FadeTransition(Duration.millis(200), toast) {{
                setToValue(1);
            }},
            new TranslateTransition(Duration.millis(200), toast) {{
                setToY(0);
            }}
        );
        entrance.play();

        // Auto-dismiss
        PauseTransition pause = new PauseTransition(duration);
        pause.setOnFinished(e -> dismiss(toast));
        pause.play();
    }

    private void dismiss(HBox toast) {
        FadeTransition fade = new FadeTransition(Duration.millis(300), toast);
        fade.setToValue(0);
        fade.setOnFinished(e -> toastContainer.getChildren().remove(toast));
        fade.play();
    }

    private HBox createToast(String message, ToastLevel level) {
        HBox toast = new HBox(12);
        toast.getStyleClass().addAll("toast", "toast-" + level.name().toLowerCase());
        toast.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(level.getIcon());
        icon.setIconSize(20);
        Label label = new Label(message);
        label.setStyle("-fx-text-fill: white;");

        toast.getChildren().addAll(icon, label);
        toast.setMaxWidth(400);
        return toast;
    }

    public enum ToastLevel {
        SUCCESS("mdi2c-check-circle"),
        ERROR("mdi2a-alert-circle"),
        WARNING("mdi2a-alert"),
        INFO("mdi2i-information");

        private final String icon;

        ToastLevel(String icon) {
            this.icon = icon;
        }

        public String getIcon() { return icon; }
    }
}
```

### CSS: Toast Styles (add to theme.css)

```css
/* ============================================
   Toast Notification System
   ============================================ */
.toast {
    -fx-background-color: rgba(30, 41, 59, 0.95);
    -fx-background-radius: 12;
    -fx-padding: 16 24;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 15, 0, 0, 5);
}

.toast-success {
    -fx-border-color: #10b981;
    -fx-border-width: 0 0 0 4;
    -fx-border-radius: 12 0 0 12;
}

.toast-success .ikonli-font-icon {
    -fx-icon-color: #10b981;
}

.toast-error {
    -fx-border-color: #ef4444;
    -fx-border-width: 0 0 0 4;
    -fx-border-radius: 12 0 0 12;
}

.toast-error .ikonli-font-icon {
    -fx-icon-color: #ef4444;
}

.toast-warning {
    -fx-border-color: #f59e0b;
    -fx-border-width: 0 0 0 4;
    -fx-border-radius: 12 0 0 12;
}

.toast-warning .ikonli-font-icon {
    -fx-icon-color: #f59e0b;
}

.toast-info {
    -fx-border-color: #3b82f6;
    -fx-border-width: 0 0 0 4;
    -fx-border-radius: 12 0 0 12;
}

.toast-info .ikonli-font-icon {
    -fx-icon-color: #3b82f6;
}
```

---

## E16: Extract Inline Styles to CSS

**Description**: Move all ~30 inline `style=""` attributes in FXML files to CSS classes.

### Files to Modify

| File | Inline Styles | Priority |
|------|--------------|----------|
| `stats.fxml` | 12 | High |
| `dashboard.fxml` | 8 | High |
| `matching.fxml` | 6 | Medium |
| `preferences.fxml` | 5 | Medium |
| `chat.fxml` | 4 | Low |

### New CSS Classes to Create

```css
/* Screen Headers */
.screen-title {
    -fx-font-size: 18px;
    -fx-font-weight: bold;
}

.screen-subtitle {
    -fx-font-size: 14px;
    -fx-text-fill: -fx-text-secondary;
}

/* Stats Values */
.stat-label-primary {
    -fx-font-size: 15px;
    -fx-font-weight: bold;
    -fx-text-fill: white;
}

.stat-label-secondary {
    -fx-font-size: 12px;
    -fx-text-fill: -fx-text-secondary;
}

/* Preferences */
.preference-label {
    -fx-font-weight: bold;
    -fx-font-size: 14px;
}

.preference-hint {
    -fx-font-size: 11px;
    -fx-text-fill: -fx-text-secondary;
}
```

### Transformation Example

```xml
<!-- BEFORE -->
<Label style="-fx-font-size: 18px; -fx-font-weight: bold;" text="Discover"/>

<!-- AFTER -->
<Label styleClass="screen-title" text="Discover"/>
```

---

## E20: Custom Styled Tooltips

**Description**: Style tooltips to match dark theme.

### CSS (add to theme.css)

```css
/* ============================================
   Custom Tooltips
   ============================================ */
.tooltip {
    -fx-background-color: rgba(15, 23, 42, 0.95);
    -fx-text-fill: white;
    -fx-font-size: 12px;
    -fx-padding: 8 12;
    -fx-background-radius: 8;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 10, 0, 0, 4);
}

.tooltip > .text {
    -fx-text-fill: white;
}
```

---

## Verification Checklist

- [ ] `ToastService.java` compiles without errors
- [ ] Toast notifications appear when actions complete (save profile, etc.)
- [ ] Toast slides in from bottom with animation
- [ ] Toast auto-dismisses after 3 seconds (success) / 5 seconds (error)
- [ ] All inline styles have been moved to CSS classes
- [ ] No `style=""` attributes remain in FXML files (except valid exceptions)
- [ ] Tooltips display with dark styling on hover
- [ ] `mvn clean compile` succeeds
- [ ] `mvn test` passes
- [ ] Visual inspection of all screens shows no regressions

---

## Integration Points

After completing Phase 1, integrate `ToastService` into controllers:

```java
// In any controller's initialize() method or scene setup:
ToastService.getInstance().setContainer((StackPane) rootPane.getScene().getRoot());

// Usage anywhere:
ToastService.getInstance().showSuccess("Profile saved!");
ToastService.getInstance().showError("Failed to load data");
```
