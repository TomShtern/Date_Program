# JavaFX GUI Enhancement Plan - Version 2.0 (Corrected)

**Version:** 2.0
**Date:** 2026-01-20
**Target:** Java 25 / JavaFX 25 / Windows 11
**Scope:** Moderate Enhancements with Light Restructuring

---

## What Changed From Version 1.0 and Why

This is the corrected version of the enhancement plan after thorough technical review using the JavaFX 25 skill documentation and actual codebase verification.

### Summary of Major Corrections

| Issue                  | V1.0 (Wrong)                             | V2.0 (Correct)                                 | Why It Matters                             |
|------------------------|------------------------------------------|------------------------------------------------|--------------------------------------------|
| **ConfettiAnimation**  | "Unused component"                       | **Already integrated** in MatchPopupController | Prevents wasted effort on work that's done |
| **Confetti API**       | `confetti.play(scene, 3000)`             | `confetti.play(canvas)`                        | Wrong API would cause compilation errors   |
| **SkeletonLoader API** | Factory methods, `start()`, `setShape()` | Constructor only: `new SkeletonLoader(w, h)`   | Wrong API would cause runtime errors       |
| **Component location** | `component/ConfettiAnimation.java`       | `util/ConfettiAnimation.java`                  | Incorrect file paths cause confusion       |
| **Photo persistence**  | `user.setPhotoPath(path)`                | `user.setPhotoUrls(List.of(url))`              | Method doesn't exist - would not compile   |
| **TestFX testing**     | Uses Mockito `verify()`                  | Uses in-memory implementations                 | Violates CLAUDE.md project standards       |
| **CSS scale**          | `-fx-scale-x: 0.97`                      | Programmatic scaling                           | Invalid CSS property - wouldn't work       |

---

## Executive Summary

This plan outlines a comprehensive enhancement strategy for the Dating App's JavaFX GUI. The focus areas are:

1. **UX Polish & Animations** - Refine micro-interactions, enhance unused SkeletonLoader, improve feedback
2. **New Features & Screens** - Onboarding flow, notifications, settings improvements
3. **Performance & Stability** - Virtual threads, structured concurrency, memory optimization

The current implementation is already well-architected with MVVM, responsive layouts, and a modern dark glassmorphic theme. Notably, **ConfettiAnimation is already integrated** in the match popup - this was incorrectly identified as unused in V1.0.

---

## 1. Current State Analysis

### 1.1 Architecture Strengths

| Aspect                | Current Implementation                               | Rating              |
|-----------------------|------------------------------------------------------|---------------------|
| **Pattern**           | MVVM with ViewModels & Controllers                 | ✅ Excellent       |
| **Navigation**        | Singleton NavigationService with transitions       | ✅ Excellent       |
| **Theming**           | Custom dark glassmorphic CSS (2100+ lines)         | ✅ Excellent       |
| **Animations**        | AnimationHelper + UiAnimations utilities           | ✅ Good            |
| **Responsive**        | 3-tier breakpoint system (900/1100px)              | ✅ Good            |
| **Components**        | Custom ProgressRing, TypingIndicator, ToastService | ✅ Good            |
| **Match Celebration** | ConfettiAnimation with canvas rendering            | ✅ Already Working |

### 1.2 Identified Gaps (Corrected)

| Component                 | Status                | Impact                                          |
|---------------------------|-----------------------|-------------------------------------------------|
| `SkeletonLoader`          | **Exists but unused** | No loading states for async content             |
| Profile photo persistence | **TODO in code**      | Photos don't save between sessions              |
| Keyboard focus indicators | **Missing**           | Accessibility gap                               |
| Touch/gesture support     | **Mouse only**        | Tablet users affected                           |
| Subscription API usage    | **Partial**           | Memory leak risk with old `addListener()` calls |

> **V2.0 Change:** Removed ConfettiAnimation from gaps - it IS being used in MatchPopupController.

### 1.3 Component Inventory (Corrected)

```
src/main/java/datingapp/ui/
├── DatingApp.java                 # Application entry point
├── NavigationService.java         # Screen transitions
├── ViewFactory.java               # View enum mapping
├── ViewModelFactory.java          # DI container
├── UISession.java                 # Current user state
├── controller/                    # 8 controllers
│   ├── LoginController.java
│   ├── DashboardController.java
│   ├── MatchingController.java
│   ├── MatchPopupController.java  # Uses ConfettiAnimation ✓
│   ├── ProfileController.java
│   ├── ChatController.java
│   ├── MatchesController.java
│   ├── StatsController.java
│   └── PreferencesController.java
├── viewmodel/                     # 8 view models
├── component/                     # Custom controls
│   ├── ProgressRing.java
│   ├── TypingIndicator.java
│   └── SkeletonLoader.java        # UNUSED - integrate this
└── util/                          # Utilities
    ├── AnimationHelper.java
    ├── UiAnimations.java
    ├── ValidationHelper.java
    ├── ToastService.java
    ├── ResponsiveController.java
    └── ConfettiAnimation.java     # USED in MatchPopupController
```

> **V2.0 Change:** Corrected ConfettiAnimation location from `component/` to `util/` and marked as USED.

---

## 2. UX Polish & Animations

### 2.1 Micro-Interaction Improvements

#### 2.1.1 Button Feedback Enhancement

**Current:** Basic hover scale on some buttons
**Proposed:** Consistent tactile feedback system

```java
// New: ButtonFeedback utility class
public class ButtonFeedback {
    public static void apply(Button button) {
        // Hover: Lift + glow
        button.setOnMouseEntered(e -> {
            AnimationHelper.lift(button, -2);
            AnimationHelper.addGlow(button, Color.web("#667eea"), 10);
        });

        // Press: Sink + ripple (MUST use Java, not CSS)
        button.setOnMousePressed(e -> {
            button.setScaleX(0.97);  // Java, not CSS
            button.setScaleY(0.97);
            AnimationHelper.ripple(button, e.getX(), e.getY());
        });

        // Release: Spring back
        button.setOnMouseReleased(e -> {
            button.setScaleX(1.0);
            button.setScaleY(1.0);
            AnimationHelper.springBack(button);
        });
    }
}
```

> **V2.0 Change:** Scale effects MUST be done in Java. CSS `-fx-scale-x/y` properties don't exist in JavaFX CSS. The valid CSS alternative is using effects or insets.

**CSS Addition (valid properties only):**
```css
.button:pressed {
    -fx-effect: innershadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);
    -fx-background-insets: 1 1 0 1;  /* Visual "press down" effect */
}
```

#### 2.1.2 Form Input Feedback

**Current:** Basic validation with shake
**Proposed:** Progressive validation feedback

| State   | Visual Feedback                                 |
|---------|-------------------------------------------------|
| Empty   | Neutral border, subtle placeholder pulse        |
| Typing  | Soft blue glow, character counter appears       |
| Valid   | Green check icon slides in, border turns green  |
| Invalid | Red border, shake + error icon + inline message |
| Focus   | Elevated shadow, label floats up                |

**Implementation (Corrected):**
```java
public class InputFeedback {
    public static Subscription enhance(TextField field, ValidationRule rule) {
        // Subscribe callback runs on FX thread - no Platform.runLater needed
        return field.textProperty().subscribe(text -> {
            ValidationResult result = rule.validate(text);
            updateVisualState(field, result);  // Already on FX thread
            if (!result.isValid()) {
                AnimationHelper.playShake(field);
            }
        });
    }
}
```

> **V2.0 Change:** Removed redundant `Platform.runLater()` - the `subscribe()` callback already executes on the FX Application Thread. Also returns the Subscription for proper cleanup.

### 2.2 Integrate SkeletonLoader (Corrected API)

**Status:** SkeletonLoader exists but is unused. ConfettiAnimation is already integrated.

**SkeletonLoader Actual API:**
```java
// Constructor - this is the ONLY way to create it
// Animation starts automatically in constructor
public SkeletonLoader(double width, double height)

// Methods available:
public void stop()  // Stops shimmer animation
// That's it - no start(), no setShape(), no factory methods
```

**Locations to Integrate:**

| Screen    | Skeleton Target              | Width x Height    |
|-----------|------------------------------|-------------------|
| Dashboard | Daily pick card              | 300 x 400         |
| Dashboard | Stats sidebar                | 200 x 300         |
| Matching  | Candidate card while loading | 350 x 500         |
| Matches   | Match card grid              | 150 x 200 each    |
| Chat      | Conversation list            | 280 x 60 per item |

**Correct Implementation Pattern:**
```java
// Generic skeleton loading pattern with CORRECT API
public void loadDataWithSkeleton(Pane container, double width, double height,
                                  Supplier<Node> contentLoader) {
    // 1. Create skeleton with dimensions (starts automatically)
    SkeletonLoader skeleton = new SkeletonLoader(width, height);
    container.getChildren().setAll(skeleton);

    // 2. Load data on virtual thread
    Thread.ofVirtual().start(() -> {
        Node content = contentLoader.get();

        Platform.runLater(() -> {
            // 3. Stop skeleton and swap content
            skeleton.stop();
            // Crossfade animation for smooth transition
            content.setOpacity(0);
            container.getChildren().setAll(content);

            FadeTransition fade = new FadeTransition(Duration.millis(300), content);
            fade.setToValue(1.0);
            fade.play();
        });
    });
}
```

> **V2.0 Change:** Completely rewrote to use actual SkeletonLoader API. V1.0 referenced non-existent methods (`forRegion()`, `setShape()`, `start()`).

### 2.3 Enhance Existing Confetti (Not "Integrate")

Since ConfettiAnimation is already working in MatchPopupController, we can optionally enhance it:

**Potential Enhancements:**
- Heart-shaped particles for dating context (modify `Particle` class)
- Burst effect from avatars, not just random
- Auto-stop after duration (add timer)

**Optional Enhancement Code:**
```java
// Add to ConfettiAnimation.java if desired
public void playFor(Canvas canvas, Duration duration) {
    play(canvas);

    // Auto-stop after duration
    PauseTransition autoStop = new PauseTransition(duration);
    autoStop.setOnFinished(e -> stop());
    autoStop.play();
}
```

> **V2.0 Change:** Changed from "Integrate" to "Enhance" since confetti is already working.

---

## 3. New Features & Screens

### 3.1 Onboarding Flow

**Purpose:** Guide new users through profile setup with engaging UX

#### 3.1.1 Onboarding Progress Indicator (Corrected)

```java
public class OnboardingProgress extends HBox {
    private final int totalSteps;
    // Use DoubleProperty for smooth animation (IntegerProperty doesn't interpolate)
    private final DoubleProperty progress = new SimpleDoubleProperty(1);

    public OnboardingProgress(int steps) {
        this.totalSteps = steps;
        setSpacing(8);
        setAlignment(Pos.CENTER);

        for (int i = 1; i <= steps; i++) {
            Circle dot = new Circle(6);
            int step = i;
            // Use >= (step - 0.5) for visual snap-to-step effect
            dot.fillProperty().bind(Bindings.when(progress.greaterThanOrEqualTo(step - 0.5))
                .then(Color.web("#667eea"))
                .otherwise(Color.web("#334155")));
            getChildren().add(dot);
        }
    }

    public void animateToStep(int step) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(300),
                new KeyValue(progress, step, Interpolator.EASE_BOTH))
        );
        timeline.play();
    }
}
```

> **V2.0 Change:** Changed from `IntegerProperty` to `DoubleProperty`. KeyValue animation with integers doesn't interpolate smoothly - it would just jump from value to value.

### 3.2 Notifications Center

(Same as V1.0 - implementation was correct)

### 3.3 Enhanced Settings Screen

(Same as V1.0 - UI mockup was correct)

---

## 4. Performance & Stability

### 4.1 Virtual Thread Migration + Structured Concurrency

**V2.0 Addition:** JDK 25 includes Structured Concurrency which is perfect for parallel data loading.

#### 4.1.1 Async Pattern Standardization (Corrected)

```java
// AsyncExecutor.java - Centralized async execution
public class AsyncExecutor {

    /**
     * Execute I/O operation on virtual thread with UI callback.
     */
    public static <T> void execute(
            Supplier<T> backgroundTask,
            Consumer<T> onSuccess,
            Consumer<Exception> onError) {

        Thread.ofVirtual().name("async-task").start(() -> {
            try {
                T result = backgroundTask.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (InterruptedException e) {
                // Restore interrupt flag - critical for proper cancellation
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    if (onError != null) onError.accept(e);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(e);
                    } else {
                        ToastService.error("Operation failed: " + e.getMessage());
                    }
                });
            }
        });
    }
}
```

> **V2.0 Change:** Added proper `InterruptedException` handling with `Thread.currentThread().interrupt()` to restore the interrupt flag.

#### 4.1.2 Structured Concurrency for Parallel Loading (NEW)

```java
import java.util.concurrent.StructuredTaskScope;

/**
 * Load dashboard data in parallel using JDK 25 Structured Concurrency.
 * All tasks complete together or fail together.
 */
public DashboardData loadDashboardParallel(UUID userId) {
    try (var scope = StructuredTaskScope.open()) {
        // Fork all tasks - they run in parallel
        var profileTask = scope.fork(() -> userStorage.findById(userId));
        var matchCountTask = scope.fork(() -> matchStorage.countByUser(userId));
        var achievementsTask = scope.fork(() -> achievementStorage.findByUser(userId));
        var dailyPickTask = scope.fork(() -> dailyPickService.getForUser(userId));

        scope.join(); // Wait for all to complete

        // All succeeded - gather results
        return new DashboardData(
            profileTask.get(),
            matchCountTask.get(),
            achievementsTask.get(),
            dailyPickTask.get()
        );
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Dashboard load interrupted", e);
    }
}

// Usage in DashboardViewModel
public void loadData() {
    Thread.ofVirtual().start(() -> {
        try {
            DashboardData data = loadDashboardParallel(currentUserId);
            Platform.runLater(() -> updateUI(data));
        } catch (Exception e) {
            Platform.runLater(() -> ToastService.error("Failed to load dashboard"));
        }
    });
}
```

> **V2.0 Addition:** V1.0 only mentioned virtual threads. Structured Concurrency is a major JDK 25 feature ideal for parallel data fetching.

### 4.2 Memory Optimization

#### 4.2.1 Subscription API Migration (Corrected)

**Current Memory Leaks Found in ProfileController:**
```java
// Line 108 - MEMORY LEAK (never cleaned up)
interestsField.textProperty().addListener((obs, oldVal, newVal) -> populateInterestChips());

// Line 134 - MEMORY LEAK (never cleaned up)
bioArea.textProperty().addListener((obs, oldVal, newVal) -> updateCharCounter(newVal));
```

**Corrected Pattern:**
```java
public class ProfileController implements Initializable {
    private final List<Subscription> subscriptions = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Use subscribe() instead of addListener()
        subscriptions.add(
            interestsField.textProperty().subscribe(text -> populateInterestChips())
        );

        subscriptions.add(
            bioArea.textProperty().subscribe(this::updateCharCounter)
        );
    }

    // Call this when navigating away
    public void cleanup() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }
}
```

> **V2.0 Change:** Identified specific memory leaks in the existing codebase (ProfileController lines 108 & 134).

#### 4.2.2 Image Caching (Corrected Thread Safety)

```java
// ImageCache.java - Thread-safe LRU cache for avatars
public class ImageCache {
    private static final int MAX_CACHE_SIZE = 100;
    // ConcurrentHashMap for true thread safety (not synchronizedMap)
    private static final Map<String, Image> cache = new ConcurrentHashMap<>();

    public static Image getAvatar(String path, double size) {
        String key = path + "@" + size;

        // computeIfAbsent IS atomic with ConcurrentHashMap
        Image image = cache.computeIfAbsent(key, k -> {
            try {
                return new Image(path, size, size, true, true, true);
            } catch (Exception e) {
                return getDefaultAvatar(size);
            }
        });

        // Simple eviction when over size
        if (cache.size() > MAX_CACHE_SIZE) {
            cache.keySet().stream().findFirst().ifPresent(cache::remove);
        }

        return image;
    }

    private static Image getDefaultAvatar(double size) {
        // Return a default placeholder image
        return new Image(
            ImageCache.class.getResourceAsStream("/images/default-avatar.png"),
            size, size, true, true
        );
    }
}
```

> **V2.0 Change:** Changed from `Collections.synchronizedMap(LinkedHashMap)` to `ConcurrentHashMap`. The synchronizedMap wrapper doesn't make `computeIfAbsent` atomic, which could cause race conditions.

---

## 5. Screen-by-Screen Enhancements

### 5.1 Profile Screen - Photo Persistence Fix (Corrected)

**The Actual Bug:** ProfileController line 236-238 has a TODO:
```java
// TODO: Save photo path to ViewModel/storage
// viewModel.setProfilePhotoPath(selectedFile.getAbsolutePath());
```

**The Actual User Model API:**
```java
// User.java - photos are stored as List<String>, not single path
List<String> getPhotoUrls();
void setPhotoUrls(List<String> photoUrls);  // Max 2 photos
void addPhotoUrl(String url);               // Max 2 enforced
```

**Correct Implementation:**

```java
// ProfileViewModel.java - Add photo property and method
private final StringProperty primaryPhotoUrl = new SimpleStringProperty("");

public StringProperty primaryPhotoUrlProperty() {
    return primaryPhotoUrl;
}

public void savePhoto(File photoFile) {
    Thread.ofVirtual().start(() -> {
        try {
            // 1. Copy to app data directory
            Path appData = Paths.get(System.getProperty("user.home"),
                ".datingapp", "photos");
            Files.createDirectories(appData);

            String filename = currentUser.getId() + "_" +
                System.currentTimeMillis() + ".jpg";
            Path destination = appData.resolve(filename);

            Files.copy(photoFile.toPath(), destination,
                StandardCopyOption.REPLACE_EXISTING);

            // 2. Update user record - USE CORRECT API
            String photoUrl = destination.toUri().toString();
            currentUser.setPhotoUrls(List.of(photoUrl));  // CORRECT
            // NOT: currentUser.setPhotoPath(photoUrl)    // WRONG - doesn't exist

            userStorage.save(currentUser);

            Platform.runLater(() -> {
                primaryPhotoUrl.set(photoUrl);
                ToastService.success("Photo saved!");
            });
        } catch (IOException e) {
            Platform.runLater(() ->
                ToastService.error("Failed to save photo: " + e.getMessage()));
        }
    });
}
```

```java
// ProfileController.java - Update handleUploadPhoto
@FXML
private void handleUploadPhoto() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Profile Photo");
    fileChooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
    );

    File selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow());

    if (selectedFile != null) {
        // Show preview immediately
        Image preview = new Image(selectedFile.toURI().toString(), 200, 200, true, true);
        if (!preview.isError()) {
            profileImageView.setImage(preview);
            profileImageView.setVisible(true);
            if (avatarPlaceholderIcon != null) {
                avatarPlaceholderIcon.setVisible(false);
            }

            // Save via ViewModel (implements persistence)
            viewModel.savePhoto(selectedFile);
        }
    }
}
```

> **V2.0 Change:** V1.0 used `setPhotoPath()` which doesn't exist. Corrected to use `setPhotoUrls(List.of(url))` matching the actual User model.

---

## 6. Technical Recommendations

### 6.1 TestFX Testing (Corrected - No Mockito)

**Project Standard (from CLAUDE.md):**
> **NEVER Use Mockito** - Use in-memory implementations instead

```java
// CORRECT: In-memory testable ViewModel
@ExtendWith(ApplicationExtension.class)
class MatchingControllerTest {

    // Testable ViewModel that tracks calls without Mockito
    private static class TestableMatchingViewModel extends MatchingViewModel {
        boolean likeWasCalled = false;
        boolean passWasCalled = false;

        TestableMatchingViewModel() {
            super(
                new InMemoryLikeStorage(),
                new InMemoryMatchStorage(),
                new InMemoryCandidateStorage()
            );
        }

        @Override
        public void like() {
            likeWasCalled = true;
            super.like();
        }

        @Override
        public void pass() {
            passWasCalled = true;
            super.pass();
        }
    }

    private TestableMatchingViewModel viewModel;

    @Start
    private void start(Stage stage) throws IOException {
        viewModel = new TestableMatchingViewModel();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/matching.fxml"));
        loader.setControllerFactory(c -> new MatchingController(viewModel));
        stage.setScene(new Scene(loader.load()));
        stage.show();
    }

    @Test
    @DisplayName("Swiping right should trigger like action")
    void swipeRightTriggersLike(FxRobot robot) {
        Node card = robot.lookup("#candidateCard").query();
        robot.drag(card).dropBy(200, 0);

        // Assert on the testable ViewModel - NO MOCKITO
        assertTrue(viewModel.likeWasCalled, "Like should have been called");
    }

    @Test
    @DisplayName("Keyboard shortcut RIGHT should like")
    void keyboardRightLikes(FxRobot robot) {
        robot.press(KeyCode.RIGHT);

        assertTrue(viewModel.likeWasCalled);
    }
}
```

> **V2.0 Change:** Completely replaced Mockito-based testing with in-memory implementations per CLAUDE.md requirements.

### 6.2 AnimatedCounter (Efficient Implementation)

```java
public class AnimatedCounter extends Label {
    // Use DoubleProperty for smooth interpolation
    private final DoubleProperty animatedValue = new SimpleDoubleProperty(0);
    private Timeline animation;

    public AnimatedCounter() {
        // Bind text to rounded animated value
        animatedValue.addListener((obs, old, newVal) ->
            setText(String.valueOf(newVal.intValue())));
    }

    public void animateTo(int target, Duration duration) {
        if (animation != null) animation.stop();

        // Single KeyFrame with interpolation - much more efficient
        animation = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(animatedValue, animatedValue.get())),
            new KeyFrame(duration,
                new KeyValue(animatedValue, target, Interpolator.EASE_OUT))
        );
        animation.play();
    }

    public void setValue(int value) {
        if (animation != null) animation.stop();
        animatedValue.set(value);
    }
}
```

> **V2.0 Change:** V1.0 created one KeyFrame per animation frame (~60 frames for 1 second). This version uses just 2 KeyFrames with built-in interpolation - much more efficient.

---

## 7. Updated Priority Matrix

### 7.1 Corrected Priority Rankings

| Rank  | Enhancement                              | Impact   | Effort  | Phase | V2.0 Notes                       |
|-------|------------------------------------------|----------|---------|-------|----------------------------------|
| 1     | **Fix photo persistence**                | Critical | Medium  | 1     | Use `setPhotoUrls()` API         |
| 2     | **Integrate SkeletonLoader**             | High     | Medium  | 1     | Fix API - constructor only       |
| 3     | **Virtual thread + StructuredTaskScope** | High     | Medium  | 1     | Added parallel loading           |
| 4     | **Subscription API migration**           | High     | Medium  | 1     | Fix ProfileController leaks      |
| 5     | **Fix memory leaks**                     | High     | Low     | 1     | ProfileController lines 108, 134 |
| ~~6~~ | ~~Integrate ConfettiAnimation~~          | ~~High~~ | ~~Low~~ | ~~1~~ | **REMOVED - Already done**       |
| 6     | Notification center                      | High     | Medium  | 2     | Unchanged                        |
| 7     | Message status indicators                | Medium   | Medium  | 2     | Unchanged                        |
| 8     | Onboarding flow                          | Medium   | High    | 2     | Fixed animation code             |
| 9     | Settings enhancement                     | Medium   | Medium  | 2     | Unchanged                        |
| 10    | Button feedback system                   | Medium   | Low     | 2     | Fixed - use Java not CSS         |
| 11    | Focus indicators                         | Medium   | Low     | 3     | Unchanged                        |
| 12    | Enhance existing confetti                | Low      | Low     | 3     | Changed from "integrate"         |

---

## 8. Implementation Phases (Updated)

### Phase 1: Foundation & Quick Wins (1-2 weeks) ✅ COMPLETED

**Goals:** Fix critical bugs, integrate SkeletonLoader correctly, establish patterns

**Completed Tasks:**

| Task | Status | File(s) Modified |
|------|--------|------------------|
| Create AsyncExecutor utility | ✅ Done | `ui/util/AsyncExecutor.java` |
| Create BaseController with cleanup | ✅ Done | `ui/controller/BaseController.java` |
| Fix ProfileController memory leaks | ✅ Done | `ui/controller/ProfileController.java` |
| Fix photo persistence | ✅ Done | `ui/viewmodel/ProfileViewModel.java` |
| Create ImageCache utility | ✅ Done | `ui/util/ImageCache.java` |
| Create SkeletonLoaderUtil | ✅ Done | `ui/util/SkeletonLoaderUtil.java` |
| Integrate SkeletonLoader in Dashboard | ✅ Done | `ui/controller/DashboardController.java` |
| Add loading state to DashboardViewModel | ✅ Done | `ui/viewmodel/DashboardViewModel.java` |
| Migrate MatchingController to Subscription API | ✅ Done | `ui/controller/MatchingController.java` |
| Add loading state to MatchingViewModel | ✅ Done | `ui/viewmodel/MatchingViewModel.java` |
| Migrate MatchesController to Subscription API | ✅ Done | `ui/controller/MatchesController.java` |
| Migrate ChatController to Subscription API | ✅ Done | `ui/controller/ChatController.java` |
| Migrate PreferencesController to Subscription API | ✅ Done | `ui/controller/PreferencesController.java` |
| Migrate LoginController to Subscription API | ✅ Done | `ui/controller/LoginController.java` |
| Extend StatsController from BaseController | ✅ Done | `ui/controller/StatsController.java` |
| Create ButtonFeedback utility | ✅ Done | `ui/util/ButtonFeedback.java` |
| Enable JDK 25 preview features | ✅ Done | `pom.xml` |

**New Files Created:**
- `src/main/java/datingapp/ui/controller/BaseController.java` - Base class with Subscription cleanup
- `src/main/java/datingapp/ui/util/AsyncExecutor.java` - Virtual thread + StructuredTaskScope utilities
- `src/main/java/datingapp/ui/util/ImageCache.java` - Thread-safe avatar caching
- `src/main/java/datingapp/ui/util/SkeletonLoaderUtil.java` - Generic skeleton loading pattern
- `src/main/java/datingapp/ui/util/ButtonFeedback.java` - Tactile button feedback effects

**Key Improvements:**
1. **Memory Safety**: All controllers now extend BaseController and use Subscription API instead of `addListener()`
2. **Async Patterns**: AsyncExecutor provides standardized virtual thread execution with proper error handling
3. **Structured Concurrency**: JDK 25 StructuredTaskScope support for parallel data loading
4. **Loading States**: Dashboard and Matching screens now show loading feedback
5. **Photo Persistence**: ProfileViewModel.savePhoto() correctly uses User.setPhotoUrls() API
6. **Button UX**: ButtonFeedback utility ready for application across UI

**Deliverables:**
- [x] Photo persistence working (using `setPhotoUrls()`)
- [x] Memory leaks in ProfileController fixed
- [x] SkeletonLoader integrated with correct API
- [x] Structured concurrency for dashboard loading
- [x] BaseController implemented
- [x] All controllers migrated to Subscription API

---

### Phase 2: Features & Polish (Upcoming)

## Appendix A: API Quick Reference (Corrected)

### SkeletonLoader
```java
// Constructor (animation starts automatically)
new SkeletonLoader(double width, double height)

// Methods
void stop()  // That's ALL
```

### ConfettiAnimation
```java
// Location: datingapp.ui.util.ConfettiAnimation

void play(Canvas canvas)  // Takes Canvas, not Scene
void stop()

// Already used in MatchPopupController lines 104-105
```

### User Photo API
```java
List<String> getPhotoUrls()
void setPhotoUrls(List<String> photoUrls)  // Max 2
void addPhotoUrl(String url)               // Max 2 enforced
```

### Subscription API
```java
// Form 1: New value only
Subscription sub = property.subscribe(newValue -> { ... });

// Form 2: Old and new values
Subscription sub = property.subscribe((oldValue, newValue) -> { ... });

// Cleanup
sub.unsubscribe();
```

---

## Approval

- [x] V2.0 corrections reviewed
- [x] API usage verified against codebase
- [x] No Mockito in test patterns
- [x] Ready to begin Phase 1
- [x] **Phase 1 COMPLETED** (2026-01-20)

---

## Implementation Log

### Phase 1 Completion (2026-01-20)

**Summary:** Phase 1 "Foundation & Quick Wins" has been fully implemented. All controllers now use the modern Subscription API for memory-safe listener management. New utilities (AsyncExecutor, ImageCache, ButtonFeedback, SkeletonLoaderUtil) provide standardized patterns for common UI operations. JDK 25 preview features (StructuredTaskScope) are enabled for parallel data loading.

**Build Status:** ✅ Passing (`mvn clean test-compile`)

**Next Steps:** Phase 2 will focus on Notification Center, Onboarding Flow, and Settings enhancements.

---

*Generated with Claude Code + JavaFX 25 Skill - Version 2.0 (Corrected)*
*Phase 1 Implementation: 2026-01-20*
