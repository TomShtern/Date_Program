# JavaFX UI Enhancement Plan — Premium Polish & Animations

> **Plan Created**: 2026-01-16
> **Target**: Enhance existing JavaFX UI with animations, micro-interactions, and premium features
> **Status**: Awaiting Approval
> **Prerequisite**: Existing JavaFX 25 UI is functional

---

## Executive Summary

This comprehensive plan outlines **20 enhancements** to transform the existing dating app JavaFX UI from functional to **premium-grade**. Enhancements are organized into 6 implementation phases, prioritized by impact and complexity.

**Current State Analysis:**
- ✅ 8 functional FXML screens (login, dashboard, matching, matches, chat, profile, stats, preferences)
- ✅ ~1,500 lines of polished CSS with dark theme and glassmorphism
- ✅ Ikonli Material Design icons integrated
- ✅ Color-coded navigation system
- ⚠️ No programmatic animations (CSS @keyframes not supported in JavaFX)
- ⚠️ ~30 inline styles that should be extracted to CSS
- ⚠️ No toast/notification system
- ⚠️ Static profile placeholders (no real photos)

---

## Enhancement Catalog

### TIER 1: High-Impact Visual Enhancements

| ID | Enhancement | Complexity | Impact | Files Affected |
|----|-------------|------------|--------|----------------|
| E01 | "It's a Match!" Celebration Animation | Medium | ⭐⭐⭐ | MatchingController, new MatchPopup.fxml, theme.css |
| E02 | Screen Transition Animations | Medium | ⭐⭐⭐ | NavigationService.java, all Controllers |
| E03 | Card Stack Effect (Matching) | High | ⭐⭐⭐ | matching.fxml, MatchingController, theme.css |
| E04 | Animated Avatar Glow Ring | Low | ⭐⭐ | theme.css, ProfileController |
| E05 | Skeleton Loading Screens | Medium | ⭐⭐ | New SkeletonLoader.java, theme.css |

### TIER 2: UX Functionality Improvements

| ID | Enhancement | Complexity | Impact | Files Affected |
|----|-------------|------------|--------|----------------|
| E06 | Toast/Snackbar Notification System | Low | ⭐⭐⭐ | New ToastService.java, toast.css |
| E07 | Swipe Gestures (Matching) | High | ⭐⭐⭐ | MatchingController, matching.fxml |
| E08 | Undo Animation | Medium | ⭐⭐ | MatchingController |
| E09 | Real-time Form Validation | Low | ⭐⭐ | ProfileController, LoginController, theme.css |
| E10 | Keyboard Navigation | Low | ⭐ | All Controllers |

### TIER 3: Feature Additions

| ID | Enhancement | Complexity | Impact | Files Affected |
|----|-------------|------------|--------|----------------|
| E11 | Dark/Light Theme Toggle | High | ⭐⭐⭐ | New light-theme.css, PreferencesController |
| E12 | Profile Photo Support | High | ⭐⭐⭐ | ProfileController, ProfileViewModel, storage layer |
| E13 | Typing Indicator (Chat) | Medium | ⭐⭐ | ChatController, chat.fxml, theme.css |
| E14 | Online/Offline Status Indicators | Low | ⭐⭐ | theme.css, MatchesController, ChatController |
| E15 | Achievement Unlock Celebration | Medium | ⭐⭐ | StatsController, new AchievementPopup.fxml |

### TIER 4: Polish & Code Quality

| ID | Enhancement | Complexity | Impact | Files Affected |
|----|-------------|------------|--------|----------------|
| E16 | Extract Inline Styles to CSS | Low | ⭐ | All FXML files, theme.css |
| E17 | Responsive Window Resizing | Medium | ⭐⭐ | All FXML files, DatingApp.java |
| E18 | Animated Progress Rings | Medium | ⭐⭐ | New ProgressRing.java, profile.fxml, dashboard.fxml |
| E19 | Parallax/Depth Effects | Low | ⭐ | theme.css, various controllers |
| E20 | Custom Styled Tooltips | Low | ⭐ | theme.css |

---

## Implementation Phases

### Phase 1: Foundation & Infrastructure (E06, E16, E20)
**Duration**: ~2 hours
**Goal**: Create reusable utility classes and clean up code

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 1 DELIVERABLES                        │
├─────────────────────────────────────────────────────────────────┤
│  [NEW] ui/util/ToastService.java       Toast notification system │
│  [NEW] resources/css/toast.css         Toast styling             │
│  [MOD] All .fxml files                 Extract inline styles     │
│  [MOD] theme.css                       Custom tooltips + styles  │
└─────────────────────────────────────────────────────────────────┘
```

---

### Phase 2: Animation Infrastructure (E02, E04, E19)
**Duration**: ~3 hours
**Goal**: Add smooth transitions throughout the app

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 2 DELIVERABLES                        │
├─────────────────────────────────────────────────────────────────┤
│  [NEW] ui/util/AnimationHelper.java    Reusable animation utils  │
│  [MOD] NavigationService.java          Screen fade transitions   │
│  [MOD] theme.css                       Pulsing avatar glow       │
│  [MOD] ProfileController.java          Avatar animation trigger  │
└─────────────────────────────────────────────────────────────────┘
```

---

### Phase 3: Quick Wins (E09, E10, E14)
**Duration**: ~2 hours
**Goal**: Small but noticeable UX improvements

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 3 DELIVERABLES                        │
├─────────────────────────────────────────────────────────────────┤
│  [MOD] ProfileController.java          Validation shake/red      │
│  [MOD] LoginController.java            Scene keyboard shortcuts  │
│  [MOD] theme.css                       Online/offline dot styles │
│  [MOD] matches.fxml                    Status indicator DOMs     │
│  [MOD] chat.fxml                       Status indicator DOMs     │
└─────────────────────────────────────────────────────────────────┘
```

---

### Phase 4: Major Features — Matching Experience (E01, E03, E07, E08)
**Duration**: ~5 hours
**Goal**: Premium Tinder-like matching experience

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 4 DELIVERABLES                        │
├─────────────────────────────────────────────────────────────────┤
│  [NEW] fxml/match_popup.fxml           "It's a Match!" dialog    │
│  [NEW] ui/util/ConfettiAnimation.java  Confetti particle effect  │
│  [MOD] matching.fxml                   Card stack structure      │
│  [MOD] MatchingController.java         Swipe gestures + undo     │
│  [MOD] theme.css                       Card stack styles         │
└─────────────────────────────────────────────────────────────────┘
```

---

### Phase 5: Advanced Features (E05, E11, E12, E13, E15, E18)
**Duration**: ~6 hours
**Goal**: Premium polish and feature depth

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 5 DELIVERABLES                        │
├─────────────────────────────────────────────────────────────────┤
│  [NEW] ui/util/SkeletonLoader.java     Shimmer loading effect    │
│  [NEW] css/light-theme.css             Full light theme          │
│  [NEW] ui/component/ProgressRing.java  Animated circular prog    │
│  [NEW] fxml/achievement_popup.fxml     Achievement unlock modal  │
│  [MOD] PreferencesController.java      Theme toggle switch       │
│  [MOD] ProfileController.java          Photo upload support      │
│  [MOD] ChatController.java             Typing indicator          │
│  [MOD] StatsController.java            Achievement animations    │
└─────────────────────────────────────────────────────────────────┘
```

---

### Phase 6: Final Polish (E17)
**Duration**: ~2 hours
**Goal**: Responsive design and edge cases

```
┌─────────────────────────────────────────────────────────────────┐
│                     PHASE 6 DELIVERABLES                        │
├─────────────────────────────────────────────────────────────────┤
│  [MOD] DatingApp.java                  Min/max window constraints│
│  [MOD] All .fxml files                 Responsive breakpoints    │
│  [MOD] dashboard.fxml                  Responsive grid layout    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Detailed Technical Specifications

### E01: "It's a Match!" Celebration Animation

**Description**: When a mutual like is detected, display an animated modal with the matched users' avatars flying in from opposite sides, confetti particles, and a celebratory message.

**Implementation Approach**:
1. Create new `match_popup.fxml` as a modal overlay
2. Use `Timeline` and `TranslateTransition` for avatar fly-in
3. Implement simple confetti using `Canvas` with particle physics
4. Add pulse animation on the heart icon using `ScaleTransition`

**File Changes**:

#### [NEW] `src/main/resources/fxml/match_popup.fxml`
```xml
<!-- Modal overlay with two avatar circles, animated heart, and confetti canvas -->
<StackPane styleClass="match-popup-overlay">
    <VBox alignment="CENTER" spacing="30">
        <HBox alignment="CENTER" spacing="40">
            <ImageView fx:id="leftAvatar" styleClass="match-avatar-left"/>
            <FontIcon iconLiteral="mdi2h-heart" iconSize="64" styleClass="match-heart"/>
            <ImageView fx:id="rightAvatar" styleClass="match-avatar-right"/>
        </HBox>
        <Label text="It's a Match!" styleClass="match-title"/>
        <Label fx:id="matchMessage" styleClass="match-subtitle"/>
        <HBox spacing="20">
            <Button text="Send Message" onAction="#handleMessage"/>
            <Button text="Keep Browsing" onAction="#handleContinue"/>
        </HBox>
    </VBox>
    <Canvas fx:id="confettiCanvas"/>
</StackPane>
```

#### [NEW] `src/main/java/datingapp/ui/util/ConfettiAnimation.java`
```java
public class ConfettiAnimation {
    private static final int PARTICLE_COUNT = 100;
    private AnimationTimer timer;
    private List<Particle> particles;

    public void play(Canvas canvas) {
        initParticles(canvas.getWidth(), canvas.getHeight());
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                update(canvas);
            }
        };
        timer.start();
    }

    private record Particle(double x, double y, double vx, double vy, Color color) {}
}
```

#### [MOD] `src/main/java/datingapp/ui/controller/MatchingController.java`
```java
// Add method to show match popup
private void showMatchPopup(User matchedUser) {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/match_popup.fxml"));
        Parent popup = loader.load();
        MatchPopupController controller = loader.getController();
        controller.setMatchedUser(matchedUser);

        // Add as overlay
        rootPane.getChildren().add(popup);

        // Play entrance animations
        AnimationHelper.playMatchAnimation(popup);
    } catch (IOException e) {
        logger.error("Failed to load match popup", e);
    }
}
```

#### [MOD] `src/main/resources/css/theme.css`
```css
/* Add to theme.css */
.match-popup-overlay {
    -fx-background-color: rgba(0, 0, 0, 0.85);
}

.match-title {
    -fx-font-size: 42px;
    -fx-font-weight: bold;
    -fx-text-fill: white;
    -fx-effect: dropshadow(gaussian, rgba(244, 63, 94, 0.8), 20, 0.5, 0, 0);
}

.match-heart {
    -fx-fill: #f43f5e;
    -fx-effect: dropshadow(gaussian, rgba(244, 63, 94, 0.8), 30, 0.6, 0, 0);
}
```

---

### E02: Screen Transition Animations

**Description**: Add smooth fade and slide transitions when navigating between screens.

**Implementation Approach**:
1. Modify `NavigationService.java` to wrap scene changes in `FadeTransition`
2. Use `TranslateTransition` for slide effects
3. Support both "push" (slide left) and "pop" (slide right) navigation styles

**File Changes**:

#### [MOD] `src/main/java/datingapp/ui/NavigationService.java`
```java
public void navigateWithTransition(String fxmlPath, TransitionType type) {
    Parent newRoot = loadFxml(fxmlPath);
    Parent oldRoot = scene.getRoot();

    StackPane transitionPane = new StackPane(oldRoot, newRoot);
    scene.setRoot(transitionPane);

    switch (type) {
        case FADE -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), oldRoot);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), newRoot);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            ParallelTransition parallel = new ParallelTransition(fadeOut, fadeIn);
            parallel.setOnFinished(e -> scene.setRoot(newRoot));
            parallel.play();
        }
        case SLIDE_LEFT -> {
            // Slide new screen in from right
            newRoot.setTranslateX(scene.getWidth());
            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), newRoot);
            slideIn.setToX(0);
            slideIn.play();
        }
    }
}

public enum TransitionType { FADE, SLIDE_LEFT, SLIDE_RIGHT, NONE }
```

---

### E03: Card Stack Effect (Matching)

**Description**: Show 2-3 cards stacked behind the current candidate card, creating depth and anticipation.

**Implementation Approach**:
1. Pre-load next 2-3 candidates in background
2. Position cards with offset transforms (`-fx-translate-x`, `scale`)
3. When swiping, animate current card out and shift stack forward

**File Changes**:

#### [MOD] `src/main/resources/fxml/matching.fxml`
```xml
<StackPane fx:id="cardStack">
    <!-- Background cards (pre-loaded) -->
    <VBox fx:id="card3" styleClass="candidate-card, card-stack-3"/>
    <VBox fx:id="card2" styleClass="candidate-card, card-stack-2"/>
    <!-- Active card (front) -->
    <VBox fx:id="candidateCard" styleClass="candidate-card, card-stack-1"/>
</StackPane>
```

#### [ADD] `src/main/resources/css/theme.css`
```css
.card-stack-3 {
    -fx-scale-x: 0.9;
    -fx-scale-y: 0.9;
    -fx-translate-y: -20px;
    -fx-opacity: 0.4;
}

.card-stack-2 {
    -fx-scale-x: 0.95;
    -fx-scale-y: 0.95;
    -fx-translate-y: -10px;
    -fx-opacity: 0.7;
}

.card-stack-1 {
    -fx-scale-x: 1.0;
    -fx-scale-y: 1.0;
    -fx-translate-y: 0px;
    -fx-opacity: 1.0;
}
```

---

### E04: Animated Avatar Glow Ring

**Description**: Add a pulsing glow effect around profile avatars using JavaFX animations.

**Implementation Approach**:
1. Use `Timeline` with `KeyFrame` to animate `-fx-effect` property
2. Create utility method for reusable glow animation
3. Trigger on hover or always-on for important UI elements

**File Changes**:

#### [NEW] `src/main/java/datingapp/ui/util/AnimationHelper.java`
```java
public class AnimationHelper {

    public static void addPulsingGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow();
        glow.setColor(glowColor);
        glow.setRadius(15);

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 15)),
            new KeyFrame(Duration.millis(1000), new KeyValue(glow.radiusProperty(), 25)),
            new KeyFrame(Duration.millis(2000), new KeyValue(glow.radiusProperty(), 15))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);

        node.setEffect(glow);
        timeline.play();
    }

    public static FadeTransition createFadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        return fade;
    }
}
```

---

### E05: Skeleton Loading Screens

**Description**: Display animated shimmer placeholders while content loads.

**Implementation Approach**:
1. Create `SkeletonPane` custom component with gradient animation
2. Use `LinearGradient` with animated offset
3. Replace actual content nodes when data arrives

**File Changes**:

#### [NEW] `src/main/java/datingapp/ui/component/SkeletonLoader.java`
```java
public class SkeletonLoader extends Region {
    private final Rectangle skeleton;
    private final Timeline shimmerAnimation;

    public SkeletonLoader(double width, double height) {
        skeleton = new Rectangle(width, height);
        skeleton.getStyleClass().add("skeleton-rect");

        LinearGradient shimmer = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(30, 41, 59, 0.6)),
            new Stop(0.5, Color.rgb(51, 65, 85, 0.8)),
            new Stop(1, Color.rgb(30, 41, 59, 0.6))
        );

        shimmerAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(/* animate gradient offset */)),
            new KeyFrame(Duration.seconds(1.5), new KeyValue(/* final position */))
        );
        shimmerAnimation.setCycleCount(Timeline.INDEFINITE);
        shimmerAnimation.play();

        getChildren().add(skeleton);
    }
}
```

#### [ADD] `src/main/resources/css/theme.css`
```css
.skeleton-rect {
    -fx-fill: linear-gradient(to right, #1e293b 0%, #334155 50%, #1e293b 100%);
    -fx-background-radius: 8;
}
```

---

### E06: Toast/Snackbar Notification System

**Description**: Display non-blocking success/error/info messages that auto-dismiss.

**Implementation Approach**:
1. Create singleton `ToastService` accessible from any controller
2. Position toasts at bottom-center of screen
3. Support multiple toast levels: SUCCESS, ERROR, WARNING, INFO
4. Auto-dismiss after configurable duration with fade-out

**File Changes**:

#### [NEW] `src/main/java/datingapp/ui/util/ToastService.java`
```java
public class ToastService {
    private static ToastService instance;
    private StackPane toastContainer;

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

    private void show(String message, ToastLevel level, Duration duration) {
        HBox toast = createToast(message, level);

        toastContainer.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 30, 0));

        // Slide in
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), toast);
        slideIn.setFromY(50);
        slideIn.setToY(0);
        slideIn.play();

        // Auto-dismiss
        PauseTransition pause = new PauseTransition(duration);
        pause.setOnFinished(e -> dismiss(toast));
        pause.play();
    }

    private HBox createToast(String message, ToastLevel level) {
        HBox toast = new HBox(12);
        toast.getStyleClass().addAll("toast", "toast-" + level.name().toLowerCase());

        FontIcon icon = new FontIcon(level.getIcon());
        Label label = new Label(message);

        toast.getChildren().addAll(icon, label);
        toast.setMaxWidth(400);
        return toast;
    }

    public enum ToastLevel {
        SUCCESS("mdi2c-check-circle", "#10b981"),
        ERROR("mdi2a-alert-circle", "#ef4444"),
        WARNING("mdi2a-alert", "#f59e0b"),
        INFO("mdi2i-information", "#3b82f6");

        private final String icon;
        private final String color;

        ToastLevel(String icon, String color) {
            this.icon = icon;
            this.color = color;
        }

        public String getIcon() { return icon; }
    }
}
```

#### [ADD] `src/main/resources/css/theme.css`
```css
.toast {
    -fx-background-color: rgba(30, 41, 59, 0.95);
    -fx-background-radius: 12;
    -fx-padding: 16 24;
    -fx-alignment: center-left;
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
}

.toast-error .ikonli-font-icon {
    -fx-icon-color: #ef4444;
}
```

---

### E07: Swipe Gestures (Matching)

**Description**: Allow users to drag candidate cards left (pass) or right (like) using mouse.

**Implementation Approach**:
1. Add mouse event handlers to candidate card
2. Track drag distance and direction
3. Rotate card slightly based on drag direction
4. Show "LIKE" or "PASS" overlay based on threshold
5. On release, either snap back or animate off-screen

**File Changes**:

#### [MOD] `src/main/java/datingapp/ui/controller/MatchingController.java`
```java
private double startX, startY;
private double dragThreshold = 150; // pixels to trigger action

private void setupSwipeGestures() {
    candidateCard.setOnMousePressed(e -> {
        startX = e.getSceneX();
        startY = e.getSceneY();
    });

    candidateCard.setOnMouseDragged(e -> {
        double deltaX = e.getSceneX() - startX;
        double rotation = deltaX * 0.05; // Slight rotation

        candidateCard.setTranslateX(deltaX);
        candidateCard.setRotate(rotation);

        // Show overlay based on direction
        if (deltaX > dragThreshold / 2) {
            showLikeOverlay();
        } else if (deltaX < -dragThreshold / 2) {
            showPassOverlay();
        } else {
            hideOverlays();
        }
    });

    candidateCard.setOnMouseReleased(e -> {
        double deltaX = e.getSceneX() - startX;

        if (Math.abs(deltaX) > dragThreshold) {
            // Trigger action
            if (deltaX > 0) {
                animateCardOffScreen(true, () -> handleLike());
            } else {
                animateCardOffScreen(false, () -> handlePass());
            }
        } else {
            // Snap back
            animateSnapBack();
        }
    });
}

private void animateCardOffScreen(boolean toRight, Runnable onComplete) {
    double targetX = toRight ? 800 : -800;
    double targetRotation = toRight ? 30 : -30;

    ParallelTransition exit = new ParallelTransition(
        new TranslateTransition(Duration.millis(300), candidateCard) {{
            setToX(targetX);
        }},
        new RotateTransition(Duration.millis(300), candidateCard) {{
            setToAngle(targetRotation);
        }},
        new FadeTransition(Duration.millis(300), candidateCard) {{
            setToValue(0);
        }}
    );

    exit.setOnFinished(e -> {
        resetCardPosition();
        onComplete.run();
    });
    exit.play();
}
```

---

### E08: Undo Animation

**Description**: Animate the previous card flying back when the undo button is pressed.

**Implementation Approach**:
1. Store reference to last swiped card data
2. On undo, create new card at off-screen position
3. Animate it flying back to center

**File Changes**:

#### [MOD] `src/main/java/datingapp/ui/controller/MatchingController.java`
```java
private Candidate lastSwipedCandidate;
private boolean lastWasLike;

@FXML
private void handleUndo() {
    if (lastSwipedCandidate == null) {
        ToastService.getInstance().showWarning("Nothing to undo");
        return;
    }

    viewModel.undo();

    // Animate card return
    double startX = lastWasLike ? 800 : -800;
    candidateCard.setTranslateX(startX);
    candidateCard.setRotate(lastWasLike ? 30 : -30);
    candidateCard.setOpacity(0);

    populateCard(lastSwipedCandidate);

    ParallelTransition returnAnim = new ParallelTransition(
        new TranslateTransition(Duration.millis(400), candidateCard) {{
            setToX(0);
            setInterpolator(Interpolator.EASE_OUT);
        }},
        new RotateTransition(Duration.millis(400), candidateCard) {{
            setToAngle(0);
        }},
        new FadeTransition(Duration.millis(400), candidateCard) {{
            setToValue(1);
        }}
    );
    returnAnim.play();

    lastSwipedCandidate = null;
    ToastService.getInstance().showSuccess("Undo successful!");
}
```

---

### E09: Real-time Form Validation

**Description**: Show validation errors with red borders and shake animations.

**Implementation Approach**:
1. Add change listeners to text fields
2. Apply `.error` CSS class on invalid input
3. Play shake animation on submit with errors

**File Changes**:

#### [ADD] `src/main/resources/css/theme.css`
```css
.text-field.error,
.text-area.error {
    -fx-border-color: #ef4444;
    -fx-effect: dropshadow(gaussian, rgba(239, 68, 68, 0.3), 8, 0, 0, 0);
}

.validation-error-label {
    -fx-font-size: 11px;
    -fx-text-fill: #ef4444;
}
```

#### [MOD] `src/main/java/datingapp/ui/util/AnimationHelper.java`
```java
public static void playShake(Node node) {
    TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
    shake.setFromX(-10);
    shake.setToX(10);
    shake.setCycleCount(6);
    shake.setAutoReverse(true);
    shake.setOnFinished(e -> node.setTranslateX(0));
    shake.play();
}
```

---

### E10: Keyboard Navigation

**Description**: Support keyboard shortcuts for common actions.

**Shortcuts**:
- `←` / `→` — Pass / Like in matching screen
- `Ctrl+Z` — Undo last action
- `Enter` — Confirm/submit
- `Escape` — Go back / close modal

**File Changes**:

#### [MOD] `src/main/java/datingapp/ui/controller/MatchingController.java`
```java
@Override
public void initialize(URL location, ResourceBundle resources) {
    // ... existing code ...

    rootPane.setOnKeyPressed(e -> {
        switch (e.getCode()) {
            case LEFT -> handlePass();
            case RIGHT -> handleLike();
            case Z -> {
                if (e.isControlDown()) handleUndo();
            }
            case UP -> handleSuperLike();
            case ESCAPE -> handleBack();
        }
    });

    // Ensure root pane is focusable
    rootPane.setFocusTraversable(true);
}
```

---

### E11: Dark/Light Theme Toggle

**Description**: Full light theme with runtime toggle in preferences.

**Implementation Approach**:
1. Create `light-theme.css` with inverted colors
2. Add theme preference to `AppConfig` or local storage
3. Add toggle switch in Preferences screen
4. Swap stylesheets at runtime

**File Changes**:

#### [NEW] `src/main/resources/css/light-theme.css`
```css
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
    -fx-text-base-color: #0f172a;
}

.button {
    -fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2);
    -fx-text-fill: white;
}

.card, .sidebar-card, .section-glass-card {
    -fx-background-color: white;
    -fx-border-color: #e2e8f0;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 10, 0, 0, 4);
}
```

#### [MOD] `src/main/java/datingapp/ui/PreferencesController.java`
```java
@FXML
private ToggleButton darkModeToggle;

@FXML
private void handleThemeToggle() {
    Scene scene = rootPane.getScene();
    ObservableList<String> stylesheets = scene.getStylesheets();

    String darkTheme = getClass().getResource("/css/theme.css").toExternalForm();
    String lightTheme = getClass().getResource("/css/light-theme.css").toExternalForm();

    if (darkModeToggle.isSelected()) {
        stylesheets.remove(lightTheme);
        stylesheets.add(darkTheme);
    } else {
        stylesheets.remove(darkTheme);
        stylesheets.add(lightTheme);
    }

    // Persist preference
    Preferences.userRoot().put("theme", darkModeToggle.isSelected() ? "dark" : "light");
}
```

---

### E12: Profile Photo Support

**Description**: Allow users to upload and display actual profile photos.

**Implementation Approach**:
1. Use `FileChooser` for image selection
2. Store images as Base64 in database or file system
3. Display using `ImageView` with circular clip

**File Changes**:

#### [MOD] `src/main/java/datingapp/ui/controller/ProfileController.java`
```java
@FXML
private ImageView avatarImage;

@FXML
private void handleUploadPhoto() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Profile Photo");
    fileChooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
    );

    File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
    if (file != null) {
        try {
            Image image = new Image(file.toURI().toString(), 200, 200, true, true);
            avatarImage.setImage(image);

            // Apply circular clip
            Circle clip = new Circle(60, 60, 60);
            avatarImage.setClip(clip);

            // Save to storage (Base64 or file path)
            viewModel.setProfilePhoto(file);
            ToastService.getInstance().showSuccess("Photo updated!");
        } catch (Exception e) {
            ToastService.getInstance().showError("Failed to load image");
        }
    }
}
```

---

### E13: Typing Indicator (Chat)

**Description**: Show animated typing dots when simulating other user typing.

**Implementation Approach**:
1. Create `TypingIndicator` component with 3 animated dots
2. Use `Timeline` with staggered animations
3. Show/hide based on chat state

**File Changes**:

#### [NEW] `src/main/java/datingapp/ui/component/TypingIndicator.java`
```java
public class TypingIndicator extends HBox {
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
    }
}
```

#### [ADD] `src/main/resources/css/theme.css`
```css
.typing-indicator {
    -fx-padding: 12 18;
    -fx-background-color: #334155;
    -fx-background-radius: 20 20 20 4;
}

.typing-dot {
    -fx-fill: #94a3b8;
}
```

---

### E14: Online/Offline Status Indicators

**Description**: Show green/gray dots on avatars to indicate user status.

**Implementation Approach**:
1. Add small `Circle` overlay on avatar containers
2. Style with `.status-online` / `.status-offline` classes
3. Bind visibility to user status property

**File Changes**:

#### [ADD] `src/main/resources/css/theme.css`
```css
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
```

---

### E15: Achievement Unlock Celebration

**Description**: Animated modal when user unlocks a new achievement.

**Implementation Approach**:
1. Create `achievement_popup.fxml` similar to match popup
2. Show achievement icon with glow and scale animation
3. Play sound effect (optional)

---

### E16: Extract Inline Styles to CSS

**Description**: Move all ~30 inline `style=""` attributes in FXML files to CSS classes.

**Files to Modify**:
- `dashboard.fxml` — 8 inline styles
- `matching.fxml` — 6 inline styles
- `chat.fxml` — 4 inline styles
- `stats.fxml` — 12 inline styles
- `preferences.fxml` — 5 inline styles

**Example Transformation**:
```xml
<!-- BEFORE -->
<Label style="-fx-font-size: 18px; -fx-font-weight: bold;" text="Discover"/>

<!-- AFTER -->
<Label styleClass="screen-title" text="Discover"/>
```

```css
/* theme.css */
.screen-title {
    -fx-font-size: 18px;
    -fx-font-weight: bold;
}
```

---

### E17: Responsive Window Resizing

**Description**: Support different window sizes with proper constraints.

**Implementation Approach**:
1. Set `minWidth` / `maxWidth` on `Stage`
2. Use `HBox.hgrow` and `VBox.vgrow` properly
3. Add media query-like breakpoints using listeners

**File Changes**:

#### [MOD] `src/main/java/datingapp/ui/DatingApp.java`
```java
@Override
public void start(Stage stage) {
    // ... existing code ...

    stage.setMinWidth(800);
    stage.setMinHeight(600);
    stage.setMaxWidth(1400);

    // Responsive listener
    scene.widthProperty().addListener((obs, oldVal, newVal) -> {
        double width = newVal.doubleValue();
        if (width < 900) {
            // Compact mode - hide sidebar
            dashboardController.setCompactMode(true);
        } else {
            dashboardController.setCompactMode(false);
        }
    });
}
```

---

### E18: Animated Progress Rings

**Description**: Replace static progress indicators with animated circular progress.

**Implementation Approach**:
1. Use `Arc` shapes with animated `length` property
2. Create reusable `ProgressRing` component
3. Show percentage text in center

**File Changes**:

#### [NEW] `src/main/java/datingapp/ui/component/ProgressRing.java`
```java
public class ProgressRing extends StackPane {
    private final Arc progressArc;
    private final Label percentLabel;
    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    public ProgressRing(double radius) {
        // Background track
        Arc track = new Arc(radius, radius, radius - 5, radius - 5, 90, -360);
        track.setType(ArcType.OPEN);
        track.getStyleClass().add("progress-ring-track");

        // Progress arc
        progressArc = new Arc(radius, radius, radius - 5, radius - 5, 90, 0);
        progressArc.setType(ArcType.OPEN);
        progressArc.getStyleClass().add("progress-ring-fill");

        // Bind arc length to progress
        progressArc.lengthProperty().bind(progress.multiply(-360));

        // Center label
        percentLabel = new Label("0%");
        percentLabel.getStyleClass().add("progress-ring-label");

        progress.addListener((obs, old, newVal) -> {
            percentLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
        });

        getChildren().addAll(track, progressArc, percentLabel);
    }

    public void animateTo(double value) {
        Timeline animation = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(progress, progress.get())),
            new KeyFrame(Duration.millis(800), new KeyValue(progress, value, Interpolator.EASE_BOTH))
        );
        animation.play();
    }
}
```

---

### E19: Parallax/Depth Effects

**Description**: Subtle parallax movement on scroll or mouse move.

**Implementation Approach**:
1. Add mouse move listener to background elements
2. Apply small `translateX/Y` based on cursor position
3. Use smooth interpolation

---

### E20: Custom Styled Tooltips

**Description**: Style tooltips to match dark theme.

**File Changes**:

#### [ADD] `src/main/resources/css/theme.css`
```css
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

## Verification Plan

### Build Verification

```shell
# 1. Clean build
mvn clean compile

# 2. Run existing tests (must still pass)
mvn test

# 3. Launch application
mvn javafx:run
```

### Manual Verification Checklist

#### Phase 1 Verification
- [ ] Toast notifications appear when actions complete (save profile, etc.)
- [ ] Toast slides in from bottom, auto-dismisses after 3 seconds
- [ ] All inline styles have been moved to CSS classes
- [ ] Tooltips display with dark styling on hover

#### Phase 2 Verification
- [ ] Screen transitions use smooth fade effect
- [ ] Profile avatar has subtle pulsing glow
- [ ] Navigation feels smooth and professional

#### Phase 3 Verification
- [ ] Invalid form fields show red border
- [ ] Form submission with errors triggers shake animation
- [ ] Keyboard shortcuts work in matching screen (← → keys)
- [ ] Online status dots visible on match avatars

#### Phase 4 Verification
- [ ] Card stack shows 2-3 cards behind current candidate
- [ ] Drag card left → shows "PASS" overlay, releases off-screen
- [ ] Drag card right → shows "LIKE" overlay, releases off-screen
- [ ] Mutual like triggers "It's a Match!" popup with confetti
- [ ] Undo button animates card flying back

#### Phase 5 Verification
- [ ] Theme toggle in preferences switches between dark/light
- [ ] Profile photo upload works with file chooser
- [ ] Photo displays with circular mask
- [ ] Typing indicator shows animated dots in chat
- [ ] Progress ring animates in profile/stats screens

#### Phase 6 Verification
- [ ] Window resizes smoothly with minimum 800x600
- [ ] UI adapts gracefully at different sizes
- [ ] No layout breaks at edge cases

---

## File Summary

### New Files (~15 files)

| File | Purpose |
|------|---------|
| `ui/util/ToastService.java` | Toast notification system |
| `ui/util/AnimationHelper.java` | Reusable animation utilities |
| `ui/util/ConfettiAnimation.java` | Confetti particle effect |
| `ui/component/SkeletonLoader.java` | Loading shimmer effect |
| `ui/component/ProgressRing.java` | Animated circular progress |
| `ui/component/TypingIndicator.java` | Chat typing animation |
| `fxml/match_popup.fxml` | "It's a Match!" modal |
| `fxml/achievement_popup.fxml` | Achievement unlock modal |
| `css/light-theme.css` | Light theme variant |

### Modified Files (~20 files)

| File | Changes |
|------|---------|
| `NavigationService.java` | Add transition animations |
| `MatchingController.java` | Swipe gestures, undo, card stack |
| `ProfileController.java` | Photo upload, avatar animation |
| `ChatController.java` | Typing indicator |
| `PreferencesController.java` | Theme toggle |
| `StatsController.java` | Achievement animations |
| `DatingApp.java` | Responsive constraints |
| `theme.css` | ~200 new lines of styles |
| All FXML files | Extract inline styles |

---

## Timeline Estimate

| Phase | Duration | Cumulative |
|-------|----------|------------|
| Phase 1: Foundation | 2 hours | 2 hours |
| Phase 2: Animation Infrastructure | 3 hours | 5 hours |
| Phase 3: Quick Wins | 2 hours | 7 hours |
| Phase 4: Matching Experience | 5 hours | 12 hours |
| Phase 5: Advanced Features | 6 hours | 18 hours |
| Phase 6: Final Polish | 2 hours | **20 hours** |

---

## Decision Points

> [!IMPORTANT]
> Please confirm before implementation:

1. **Phase Priority**: Start with Phase 1 (Foundation) or jump to Phase 4 (most visible impact)?
2. **Theme**: Implement light theme (E11) or defer?
3. **Photo Storage**: Store as Base64 in DB or file paths in `data/` folder?
4. **Sound Effects**: Add audio feedback for actions (match popup, achievements)?

---

## Next Steps After Approval

1. ✅ Plan approved
2. 🔨 Implement Phase 1 — Toast system, CSS cleanup, tooltips
3. 🧪 Verify Phase 1 with manual checklist
4. 📝 Update this plan with any learnings
5. 🔄 Proceed to Phase 2
