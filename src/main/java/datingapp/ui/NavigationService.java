package datingapp.ui;

import datingapp.ui.screen.BaseController;
import datingapp.ui.viewmodel.ViewModelFactory;
import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton service handling navigation between different screens in the
 * application.
 * Manages the primary stage and root layout.
 */
public final class NavigationService {
    private static final Logger logger = LoggerFactory.getLogger(NavigationService.class);

    private static final class Holder {
        private static final NavigationService INSTANCE = new NavigationService();
    }

    private Stage primaryStage;
    private BorderPane rootLayout;
    private StackPane rootStack;
    private ViewModelFactory viewModelFactory;

    private final java.util.concurrent.atomic.AtomicReference<NavigationContextEnvelope> navigationContext =
            new java.util.concurrent.atomic.AtomicReference<>();

    private static final class NavigationContextEnvelope {
        private final ViewType targetView;
        private final Object payload;

        private NavigationContextEnvelope(ViewType targetView, Object payload) {
            this.targetView = targetView;
            this.payload = payload;
        }
    }

    /** Navigation history stack for back navigation. */
    private final Deque<ViewType> navigationHistory = new ConcurrentLinkedDeque<>();

    /** Current controller for cleanup when navigating away. */
    private Object currentController;

    /** Maximum history size to prevent memory leaks. */
    private static final int MAX_HISTORY_SIZE = 20;

    /** Enum defining available views and their FXML resource paths. */
    public static enum ViewType {
        LOGIN("/fxml/login.fxml"),
        DASHBOARD("/fxml/dashboard.fxml"),
        PROFILE("/fxml/profile.fxml"),
        MATCHING("/fxml/matching.fxml"),
        MATCHES("/fxml/matches.fxml"),
        CHAT("/fxml/chat.fxml"),
        STATS("/fxml/stats.fxml"),
        PREFERENCES("/fxml/MatchPreferences.fxml"),
        STANDOUTS("/fxml/standouts.fxml"),
        SOCIAL("/fxml/social.fxml");

        private final String fxmlPath;

        ViewType(String fxmlPath) {
            this.fxmlPath = fxmlPath;
        }

        public String getFxmlPath() {
            return fxmlPath;
        }
    }

    /** Types of screen transition animations. */
    public static enum TransitionType {
        /** Fade out old screen, fade in new screen */
        FADE,
        /** Slide new screen in from right, old screen slides left */
        SLIDE_LEFT,
        /** Slide new screen in from left, old screen slides right */
        SLIDE_RIGHT,
        /** No animation, instant switch */
        NONE
    }

    private NavigationService() {}

    public static NavigationService getInstance() {
        return Holder.INSTANCE;
    }

    public void setViewModelFactory(ViewModelFactory viewModelFactory) {
        this.viewModelFactory = viewModelFactory;
    }

    /**
     * Initializes the service with the primary stage.
     * Sets up the root layout (BorderPane) which acts as a wrapper for all screens.
     */
    public void initialize(Stage stage) {
        this.primaryStage = stage;
        this.rootLayout = new BorderPane();

        this.rootStack = new StackPane(rootLayout);

        Scene scene = new Scene(rootStack, 900, 760);
        // Load CSS theme
        String css =
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm();
        scene.getStylesheets().add(css);

        UiFeedbackService.setContainer(rootStack);

        primaryStage.setScene(scene);
    }

    /**
     * Handles transitions to different views by loading FXML and injecting
     * ViewModels. Uses no animation (instant switch).
     */
    public void navigateTo(ViewType viewType) {
        // UI-03: Warn if navigation context was set but never consumed (debugging aid)
        NavigationContextEnvelope unconsumed = navigationContext.get();
        if (unconsumed != null && logger.isDebugEnabled()) {
            logger.debug(
                    "Navigation context for {} was not consumed before navigating to {}",
                    unconsumed.targetView,
                    viewType);
        }
        navigateWithTransition(viewType, TransitionType.NONE);
    }

    /**
     * Navigates to a view with the specified transition animation.
     *
     * @param viewType The view to navigate to
     * @param type     The type of transition animation
     */
    public void navigateWithTransition(ViewType viewType, TransitionType type) {
        navigateWithTransition(viewType, type, true);
    }

    /**
     * Navigates to a view with the specified transition animation, optionally
     * tracking history.
     *
     * @param viewType     The view to navigate to
     * @param type         The type of transition animation
     * @param addToHistory Whether to add this navigation to the history stack
     */
    private void navigateWithTransition(ViewType viewType, TransitionType type, boolean addToHistory) {
        runOnFx(() -> navigateInternal(viewType, type, addToHistory));
    }

    private boolean navigateInternal(ViewType viewType, TransitionType type, boolean addToHistory) {
        if (rootLayout == null) {
            logError("NavigationService not initialized; cannot navigate to {}", viewType);
            UiFeedbackService.showError("Navigation not ready yet.");
            return false;
        }
        if (viewModelFactory == null) {
            logError("ViewModelFactory is null; cannot navigate to {}", viewType);
            UiFeedbackService.showError("Navigation unavailable. Please restart the app.");
            return false;
        }

        logInfo("Navigating to: {} with transition: {}", viewType, type);

        java.net.URL fxmlUrl = getClass().getResource(viewType.getFxmlPath());
        if (fxmlUrl == null) {
            logError("FXML resource not found for view: {}", viewType);
            UiFeedbackService.showError("Unable to load screen: " + viewType);
            return false;
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        loader.setControllerFactory(viewModelFactory::createController);

        Parent newView;
        try {
            newView = loader.load();
        } catch (IOException | RuntimeException e) {
            logError("Failed to navigate to {}: {}", viewType, e.getMessage(), e);
            UiFeedbackService.showError("Failed to load screen. Please try again.");
            return false;
        }

        // Cleanup old controller before switching
        if (currentController instanceof BaseController bc) {
            bc.cleanup();
        }

        // Store new controller for future cleanup
        currentController = loader.getController();

        if (addToHistory) {
            recordNavigation(viewType);
        }

        if (type == TransitionType.NONE) {
            rootLayout.setCenter(newView);
            return true;
        }

        Parent oldView = (Parent) rootLayout.getCenter();
        if (oldView == null) {
            rootLayout.setCenter(newView);
            return true;
        }

        // Create transition container
        StackPane transitionPane = new StackPane(oldView, newView);
        rootLayout.setCenter(transitionPane);

        switch (type) {
            case FADE -> playFadeTransition(oldView, newView);
            case SLIDE_LEFT -> playSlideTransition(oldView, newView, true);
            case SLIDE_RIGHT -> playSlideTransition(oldView, newView, false);
            default -> rootLayout.setCenter(newView);
        }

        return true;
    }

    private void recordNavigation(ViewType viewType) {
        if (navigationHistory.isEmpty() || navigationHistory.peek() != viewType) {
            navigationHistory.push(viewType);

            while (navigationHistory.size() > MAX_HISTORY_SIZE) {
                navigationHistory.removeLast();
            }
        }
    }

    private void playFadeTransition(Parent oldView, Parent newView) {
        newView.setOpacity(0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), oldView);
        fadeOut.setToValue(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(150), newView);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setDelay(Duration.millis(100));

        ParallelTransition parallel = new ParallelTransition(fadeOut, fadeIn);
        parallel.setOnFinished(e -> {
            e.consume();
            rootLayout.setCenter(newView);
        });
        parallel.play();
    }

    private void playSlideTransition(Parent oldView, Parent newView, boolean slideLeft) {
        double width = primaryStage.getScene().getWidth();

        if (slideLeft) {
            newView.setTranslateX(width);
        } else {
            newView.setTranslateX(-width);
        }

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), oldView);
        slideOut.setToX(slideLeft ? -width * 0.3 : width * 0.3);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), newView);
        slideIn.setToX(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition parallel = new ParallelTransition(slideOut, slideIn);
        parallel.setOnFinished(e -> {
            e.consume();
            newView.setTranslateX(0);
            rootLayout.setCenter(newView);
        });
        parallel.play();
    }

    /**
     * Navigates back to the previous screen using history stack.
     * If no history exists, defaults to DASHBOARD.
     */
    public void goBack() {
        runOnFx(this::doGoBack);
    }

    private void doGoBack() {
        ViewType previousView = resolvePreviousView();
        boolean navigated = navigateInternal(previousView, TransitionType.SLIDE_RIGHT, false);
        if (navigated && !navigationHistory.isEmpty()) {
            navigationHistory.pop();
        }
    }

    private ViewType resolvePreviousView() {
        if (navigationHistory.size() < 2) {
            return ViewType.DASHBOARD;
        }
        Iterator<ViewType> iterator = navigationHistory.iterator();
        if (iterator.hasNext()) {
            iterator.next();
        }
        return iterator.hasNext() ? iterator.next() : ViewType.DASHBOARD;
    }

    /**
     * Clears navigation history. Useful when logging out or resetting state.
     */
    public void clearHistory() {
        navigationHistory.clear();
    }

    /**
     * Checks if there is navigation history to go back to.
     *
     * @return true if back navigation is possible
     */
    public boolean canGoBack() {
        return navigationHistory.size() > 1;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public ViewModelFactory getViewModelFactory() {
        return viewModelFactory;
    }

    public StackPane getRootStack() {
        return rootStack;
    }

    /**
     * @deprecated Prefer {@link #setNavigationContext(ViewType, Object)} to scope context by target
     *     view and prevent accidental cross-screen consumption.
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(forRemoval = false)
    public void setNavigationContext(Object context) {
        setNavigationContext(null, context);
    }

    public void setNavigationContext(ViewType targetView, Object context) {
        this.navigationContext.set(new NavigationContextEnvelope(targetView, context));
    }

    /**
     * @deprecated Prefer {@link #consumeNavigationContext(ViewType, Class)} for type-safe,
     *     target-scoped context consumption.
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(forRemoval = false)
    public Object consumeNavigationContext() {
        NavigationContextEnvelope envelope = navigationContext.getAndSet(null);
        return envelope != null ? envelope.payload : null;
    }

    public <T> Optional<T> consumeNavigationContext(ViewType consumerView, Class<T> expectedType) {
        Objects.requireNonNull(consumerView, "consumerView cannot be null");
        Objects.requireNonNull(expectedType, "expectedType cannot be null");

        NavigationContextEnvelope envelope = navigationContext.getAndSet(null);
        if (envelope == null) {
            return Optional.empty();
        }
        if (envelope.targetView != null && envelope.targetView != consumerView) {
            if (logger.isDebugEnabled()) {
                logger.debug("Discarding navigation context for {} consumed by {}", envelope.targetView, consumerView);
            }
            return Optional.empty();
        }
        if (!expectedType.isInstance(envelope.payload)) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Navigation context type mismatch for {}: expected {}, actual {}",
                        consumerView,
                        expectedType.getName(),
                        envelope.payload == null
                                ? "null"
                                : envelope.payload.getClass().getName());
            }
            return Optional.empty();
        }
        return Optional.of(expectedType.cast(envelope.payload));
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logError(String message, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(message, args);
        }
    }
}
