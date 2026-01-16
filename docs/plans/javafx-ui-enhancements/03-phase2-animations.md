# Phase 2: Animation Infrastructure

> **Enhancements**: E02 (Screen Transitions), E04 (Avatar Glow), E19 (Parallax Effects)
> **Duration**: ~3 hours
> **Status**: [ ] Not Started
> **Prerequisites**: Phase 1 Complete

---

## Goal

Add smooth transitions and animations throughout the app to create a polished, professional feel.

---

## Deliverables

| Type | File | Description |
|------|------|-------------|
| [NEW] | `ui/util/AnimationHelper.java` | Reusable animation utilities |
| [MOD] | `NavigationService.java` | Screen fade/slide transitions |
| [MOD] | `theme.css` | Pulsing avatar glow styles |
| [MOD] | `ProfileController.java` | Avatar animation trigger |

---

## E02: Screen Transition Animations

**Description**: Add smooth fade and slide transitions when navigating between screens.

### Implementation Steps

1. Create `TransitionType` enum in NavigationService
2. Modify navigation methods to accept transition type
3. Implement fade and slide animations using `ParallelTransition`
4. Update all controller navigation calls to use transitions

### Code: Modified `NavigationService.java`

```java
public enum TransitionType {
    FADE,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    NONE
}

public void navigateWithTransition(String fxmlPath, TransitionType type) {
    Parent newRoot = loadFxml(fxmlPath);
    Parent oldRoot = scene.getRoot();

    if (type == TransitionType.NONE) {
        scene.setRoot(newRoot);
        return;
    }

    StackPane transitionPane = new StackPane(oldRoot, newRoot);
    scene.setRoot(transitionPane);

    switch (type) {
        case FADE -> {
            newRoot.setOpacity(0);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), oldRoot);
            fadeOut.setToValue(0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), newRoot);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.setDelay(Duration.millis(100));

            ParallelTransition parallel = new ParallelTransition(fadeOut, fadeIn);
            parallel.setOnFinished(e -> scene.setRoot(newRoot));
            parallel.play();
        }
        case SLIDE_LEFT -> {
            newRoot.setTranslateX(scene.getWidth());

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), oldRoot);
            slideOut.setToX(-scene.getWidth() * 0.3);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), newRoot);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition parallel = new ParallelTransition(slideOut, slideIn);
            parallel.setOnFinished(e -> scene.setRoot(newRoot));
            parallel.play();
        }
        case SLIDE_RIGHT -> {
            newRoot.setTranslateX(-scene.getWidth());

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), oldRoot);
            slideOut.setToX(scene.getWidth() * 0.3);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), newRoot);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition parallel = new ParallelTransition(slideOut, slideIn);
            parallel.setOnFinished(e -> scene.setRoot(newRoot));
            parallel.play();
        }
    }
}
```

### Controller Updates

```java
// When navigating forward (e.g., Dashboard → Profile)
navigationService.navigateWithTransition("/fxml/profile.fxml", TransitionType.SLIDE_LEFT);

// When navigating back
navigationService.navigateWithTransition("/fxml/dashboard.fxml", TransitionType.SLIDE_RIGHT);

// For modals or quick transitions
navigationService.navigateWithTransition("/fxml/settings.fxml", TransitionType.FADE);
```

---

## E04: Animated Avatar Glow Ring

**Description**: Add a pulsing glow effect around profile avatars using JavaFX animations.

### Code: `AnimationHelper.java`

```java
package datingapp.ui.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class AnimationHelper {

    /**
     * Adds a continuous pulsing glow effect to a node.
     * @return The Timeline so it can be stopped if needed
     */
    public static Timeline addPulsingGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow();
        glow.setColor(glowColor);
        glow.setRadius(15);
        glow.setSpread(0.3);

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(glow.radiusProperty(), 15),
                new KeyValue(glow.spreadProperty(), 0.2)
            ),
            new KeyFrame(Duration.millis(1000),
                new KeyValue(glow.radiusProperty(), 25),
                new KeyValue(glow.spreadProperty(), 0.4)
            ),
            new KeyFrame(Duration.millis(2000),
                new KeyValue(glow.radiusProperty(), 15),
                new KeyValue(glow.spreadProperty(), 0.2)
            )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);

        node.setEffect(glow);
        timeline.play();

        return timeline;
    }

    /**
     * Creates a fade-in animation.
     */
    public static FadeTransition createFadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        return fade;
    }

    /**
     * Creates a scale pulse animation (for buttons, icons, etc.)
     */
    public static ScaleTransition createPulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.1);
        pulse.setToY(1.1);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        return pulse;
    }

    /**
     * Plays a shake animation for validation errors.
     */
    public static void playShake(Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(-10);
        shake.setToX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    /**
     * Bounce animation for elements entering the screen.
     */
    public static void playBounceIn(Node node) {
        node.setScaleX(0);
        node.setScaleY(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(400), node);
        scale.setFromX(0);
        scale.setFromY(0);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        // Overshoot effect
        ScaleTransition overshoot = new ScaleTransition(Duration.millis(100), node);
        overshoot.setFromX(1);
        overshoot.setFromY(1);
        overshoot.setToX(1.05);
        overshoot.setToY(1.05);
        overshoot.setAutoReverse(true);
        overshoot.setCycleCount(2);

        scale.setOnFinished(e -> overshoot.play());
        scale.play();
    }
}
```

### ProfileController Integration

```java
@Override
public void initialize(URL location, ResourceBundle resources) {
    // ... existing code ...

    // Add pulsing glow to avatar container
    StackPane avatarContainer = (StackPane) avatarPane.lookup(".profile-avatar-container");
    if (avatarContainer != null) {
        AnimationHelper.addPulsingGlow(avatarContainer, Color.web("#667eea"));
    }
}
```

---

## E19: Parallax/Depth Effects

**Description**: Subtle parallax movement on mouse move for background elements.

### Implementation

```java
public static void addParallaxEffect(Node node, double intensity) {
    Scene scene = node.getScene();
    if (scene == null) return;

    scene.setOnMouseMoved(e -> {
        double centerX = scene.getWidth() / 2;
        double centerY = scene.getHeight() / 2;

        double offsetX = (e.getSceneX() - centerX) / centerX * intensity;
        double offsetY = (e.getSceneY() - centerY) / centerY * intensity;

        // Smooth transition
        TranslateTransition move = new TranslateTransition(Duration.millis(100), node);
        move.setToX(offsetX);
        move.setToY(offsetY);
        move.play();
    });
}
```

---

## Verification Checklist

- [ ] `AnimationHelper.java` compiles without errors
- [ ] Screen transitions use smooth fade effect when navigating
- [ ] Slide transitions work correctly (left for forward, right for back)
- [ ] Profile avatar has subtle pulsing glow animation
- [ ] Glow animation runs continuously without performance issues
- [ ] Navigation feels smooth and professional
- [ ] No UI flickering during transitions
- [ ] `mvn clean compile` succeeds
- [ ] `mvn test` passes

---

## Performance Notes

- Animations use `Interpolator.EASE_OUT` for natural feel
- Transition durations kept short (150-300ms) for responsiveness
- Timeline animations properly managed to prevent memory leaks
- Consider adding `timeline.stop()` calls when navigating away
