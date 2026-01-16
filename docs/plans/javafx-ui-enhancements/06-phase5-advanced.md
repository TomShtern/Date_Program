# Phase 5: Advanced Features

> **Enhancements**: E05 (Skeleton Loading), E11 (Theme Toggle), E12 (Profile Photos), E13 (Typing Indicator), E15 (Achievement Celebration), E18 (Progress Rings)
> **Duration**: ~6 hours
> **Status**: [ ] Not Started
> **Prerequisites**: Phases 1-4 Complete

---

## Goal

Add premium polish features including theme switching, real profile photos, animated components, and loading states.

---

## Deliverables

| Type | File | Description |
|------|------|-------------|
| [NEW] | `ui/util/SkeletonLoader.java` | Shimmer loading effect |
| [NEW] | `css/light-theme.css` | Full light theme variant |
| [NEW] | `ui/component/ProgressRing.java` | Animated circular progress |
| [NEW] | `ui/component/TypingIndicator.java` | Chat typing animation |
| [NEW] | `fxml/achievement_popup.fxml` | Achievement unlock modal |
| [MOD] | `PreferencesController.java` | Theme toggle switch |
| [MOD] | `ProfileController.java` | Photo upload support |
| [MOD] | `ChatController.java` | Typing indicator |
| [MOD] | `StatsController.java` | Achievement animations |

---

## E05: Skeleton Loading Screens

**Description**: Display animated shimmer placeholders while content loads.

### Component: `SkeletonLoader.java`

```java
package datingapp.ui.component;

import javafx.animation.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SkeletonLoader extends Region {
    private final Rectangle skeleton;
    private final Timeline shimmerAnimation;

    public SkeletonLoader(double width, double height) {
        skeleton = new Rectangle(width, height);
        skeleton.setArcWidth(8);
        skeleton.setArcHeight(8);

        // Initial gradient
        updateGradient(0);

        // Animate gradient position
        shimmerAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, e -> updateGradient(0)),
            new KeyFrame(Duration.seconds(1.5), e -> updateGradient(1))
        );
        shimmerAnimation.setCycleCount(Timeline.INDEFINITE);
        shimmerAnimation.play();

        getChildren().add(skeleton);
        setMinSize(width, height);
        setMaxSize(width, height);
    }

    private void updateGradient(double position) {
        double startX = -0.5 + position * 2;
        double endX = startX + 0.5;

        LinearGradient gradient = new LinearGradient(
            startX, 0, endX, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(30, 41, 59)),
            new Stop(0.5, Color.rgb(51, 65, 85)),
            new Stop(1, Color.rgb(30, 41, 59))
        );
        skeleton.setFill(gradient);
    }

    public void stop() {
        shimmerAnimation.stop();
    }
}
```

### Usage Pattern

```java
// Show skeleton while loading
VBox skeletonContainer = new VBox(12);
skeletonContainer.getChildren().addAll(
    new SkeletonLoader(300, 20),  // Title
    new SkeletonLoader(200, 16),  // Subtitle
    new SkeletonLoader(350, 100)  // Content area
);
contentPane.getChildren().add(skeletonContainer);

// Replace with real content when loaded
loadDataAsync().thenAccept(data -> {
    Platform.runLater(() -> {
        contentPane.getChildren().remove(skeletonContainer);
        contentPane.getChildren().add(createRealContent(data));
    });
});
```

---

## E11: Dark/Light Theme Toggle

**Description**: Full light theme with runtime toggle in preferences.

### Light Theme CSS: `light-theme.css`

```css
/* ============================================
   Light Theme Overrides
   ============================================ */
.root {
    -fx-background-dark: #f8fafc;
    -fx-surface-dark: #ffffff;
    -fx-card-background: #f1f5f9;
    -fx-text-primary: #0f172a;
    -fx-text-secondary: #64748b;
    -fx-text-muted: #94a3b8;

    -fx-base: #ffffff;
    -fx-background: #f8fafc;
    -fx-control-inner-background: #ffffff;
    -fx-control-inner-background-alt: #f1f5f9;
    -fx-text-base-color: #0f172a;
    -fx-text-inner-color: #0f172a;
}

.header-bar {
    -fx-background-color: rgba(255, 255, 255, 0.95);
    -fx-border-color: #e2e8f0;
}

.sidebar {
    -fx-background-color: #ffffff;
    -fx-border-color: #e2e8f0;
}

.card, .glass-card, .section-glass-card {
    -fx-background-color: white;
    -fx-border-color: #e2e8f0;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 10, 0, 0, 4);
}

.text-field, .text-area {
    -fx-background-color: #f8fafc;
    -fx-text-fill: #0f172a;
    -fx-border-color: #e2e8f0;
}

.text-field:focused, .text-area:focused {
    -fx-background-color: white;
    -fx-border-color: -fx-primary;
}

.list-cell:filled:selected {
    -fx-background-color: linear-gradient(to right, rgba(102, 126, 234, 0.1), transparent);
}

/* Keep buttons with gradient */
.button {
    -fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2);
    -fx-text-fill: white;
}

.button-secondary {
    -fx-background-color: white;
    -fx-text-fill: #0f172a;
    -fx-border-color: #e2e8f0;
}

.button-secondary:hover {
    -fx-background-color: #f8fafc;
    -fx-border-color: -fx-primary;
}
```

### PreferencesController Theme Toggle

```java
@FXML private ToggleButton themeToggle;

@FXML
public void initialize() {
    // Load saved preference
    String savedTheme = Preferences.userRoot().get("app_theme", "dark");
    themeToggle.setSelected("dark".equals(savedTheme));
    updateToggleText();

    themeToggle.selectedProperty().addListener((obs, old, isDark) -> {
        applyTheme(isDark);
        updateToggleText();
    });
}

private void updateToggleText() {
    themeToggle.setText(themeToggle.isSelected() ? "☀️ Light Mode" : "🌙 Dark Mode");
}

private void applyTheme(boolean isDark) {
    Scene scene = rootPane.getScene();
    ObservableList<String> sheets = scene.getStylesheets();

    String darkTheme = getClass().getResource("/css/theme.css").toExternalForm();
    String lightTheme = getClass().getResource("/css/light-theme.css").toExternalForm();

    sheets.clear();
    sheets.add(darkTheme); // Base theme always loaded

    if (!isDark) {
        sheets.add(lightTheme); // Light overrides on top
    }

    Preferences.userRoot().put("app_theme", isDark ? "dark" : "light");
}
```

---

## E12: Profile Photo Support

**Description**: Allow users to upload and display actual profile photos.

### ProfileController Photo Upload

```java
@FXML private ImageView avatarImage;
@FXML private StackPane avatarContainer;

@FXML
private void handleUploadPhoto() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Profile Photo");
    fileChooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
    );

    File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
    if (file != null) {
        loadProfilePhoto(file);
    }
}

private void loadProfilePhoto(File file) {
    try {
        // Load with size constraints
        Image image = new Image(file.toURI().toString(), 200, 200, true, true, true);

        image.progressProperty().addListener((obs, old, progress) -> {
            if (progress.doubleValue() >= 1.0) {
                Platform.runLater(() -> {
                    avatarImage.setImage(image);
                    applyCircularClip();
                    viewModel.setProfilePhotoPath(file.getAbsolutePath());
                    ToastService.getInstance().showSuccess("Photo updated!");
                });
            }
        });

        image.errorProperty().addListener((obs, old, hasError) -> {
            if (hasError) {
                ToastService.getInstance().showError("Failed to load image");
            }
        });

    } catch (Exception e) {
        ToastService.getInstance().showError("Invalid image file");
    }
}

private void applyCircularClip() {
    double radius = Math.min(avatarImage.getFitWidth(), avatarImage.getFitHeight()) / 2;
    Circle clip = new Circle(radius, radius, radius);
    avatarImage.setClip(clip);
}
```

---

## E13: Typing Indicator

**Description**: Show animated typing dots when simulating other user typing.

### Component: `TypingIndicator.java`

```java
package datingapp.ui.component;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class TypingIndicator extends HBox {
    private final Timeline animation;

    public TypingIndicator() {
        setSpacing(4);
        setAlignment(Pos.CENTER);
        getStyleClass().add("typing-indicator");

        for (int i = 0; i < 3; i++) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add("typing-dot");
            getChildren().add(dot);

            // Staggered bounce animation
            TranslateTransition bounce = new TranslateTransition(Duration.millis(400), dot);
            bounce.setFromY(0);
            bounce.setToY(-6);
            bounce.setAutoReverse(true);
            bounce.setCycleCount(Timeline.INDEFINITE);
            bounce.setDelay(Duration.millis(i * 150));
            bounce.play();
        }

        animation = null; // Individual dot animations
    }

    public void show() {
        setVisible(true);
        setManaged(true);
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }
}
```

### CSS

```css
.typing-indicator {
    -fx-padding: 12 18;
    -fx-background-color: #334155;
    -fx-background-radius: 20 20 20 4;
    -fx-max-width: 80;
}

.typing-dot {
    -fx-fill: #94a3b8;
}
```

---

## E18: Animated Progress Rings

**Description**: Replace static progress indicators with animated circular progress.

### Component: `ProgressRing.java`

```java
package datingapp.ui.component;

import javafx.animation.*;
import javafx.beans.property.*;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

public class ProgressRing extends StackPane {
    private final Arc progressArc;
    private final Label percentLabel;
    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    public ProgressRing(double radius) {
        double strokeWidth = 6;
        double innerRadius = radius - strokeWidth;

        // Background track
        Arc track = new Arc(radius, radius, innerRadius, innerRadius, 90, -360);
        track.setType(ArcType.OPEN);
        track.setStroke(Color.web("#1e293b"));
        track.setStrokeWidth(strokeWidth);
        track.setFill(Color.TRANSPARENT);
        track.setStrokeLineCap(StrokeLineCap.ROUND);

        // Progress arc
        progressArc = new Arc(radius, radius, innerRadius, innerRadius, 90, 0);
        progressArc.setType(ArcType.OPEN);
        progressArc.setStroke(Color.web("#667eea"));
        progressArc.setStrokeWidth(strokeWidth);
        progressArc.setFill(Color.TRANSPARENT);
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);

        // Bind arc length to progress
        progress.addListener((obs, old, newVal) -> {
            progressArc.setLength(-360 * newVal.doubleValue());
            percentLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
        });

        // Center label
        percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        getChildren().addAll(track, progressArc, percentLabel);
        setMinSize(radius * 2, radius * 2);
        setMaxSize(radius * 2, radius * 2);
    }

    public void animateTo(double value) {
        Timeline animation = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(progress, progress.get())),
            new KeyFrame(Duration.millis(800), new KeyValue(progress, value, Interpolator.EASE_BOTH))
        );
        animation.play();
    }

    public void setProgress(double value) {
        progress.set(value);
    }

    public DoubleProperty progressProperty() {
        return progress;
    }
}
```

---

## Verification Checklist

- [ ] Skeleton loaders show shimmer animation while loading
- [ ] Theme toggle switches between dark and light themes
- [ ] Theme preference persists across app restarts
- [ ] Light theme has proper contrast and readability
- [ ] Profile photo upload works with file chooser
- [ ] Photo displays with circular mask
- [ ] Invalid image files show error toast
- [ ] Typing indicator shows 3 bouncing dots
- [ ] Typing indicator can be shown/hidden programmatically
- [ ] Progress ring animates smoothly to target value
- [ ] Progress ring shows percentage label in center
- [ ] `mvn clean compile` succeeds
- [ ] `mvn test` passes
