package datingapp.ui;

import datingapp.ui.controller.BaseController;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
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
    private static NavigationService instance;

    private Stage primaryStage;
    private BorderPane rootLayout;
    private ViewModelFactory viewModelFactory;

    /** Navigation history stack for back navigation. */
    private final Deque<ViewType> navigationHistory = new ArrayDeque<>();

    /** Current controller for cleanup when navigating away. */
    private Object currentController;

    /** Maximum history size to prevent memory leaks. */
    private static final int MAX_HISTORY_SIZE = 20;

    /** Enum defining available views and their FXML resource paths. */
    public enum ViewType {
        LOGIN("/fxml/login.fxml"),
        DASHBOARD("/fxml/dashboard.fxml"),
        PROFILE("/fxml/profile.fxml"),
        MATCHING("/fxml/matching.fxml"),
        MATCHES("/fxml/matches.fxml"),
        CHAT("/fxml/chat.fxml"),
        STATS("/fxml/stats.fxml"),
        PREFERENCES("/fxml/preferences.fxml");

        private final String fxmlPath;

        ViewType(String fxmlPath) {
            this.fxmlPath = fxmlPath;
        }

        public String getFxmlPath() {
            return fxmlPath;
        }
    }

    /** Types of screen transition animations. */
    public enum TransitionType {
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

    public static synchronized NavigationService getInstance() {
        if (instance == null) {
            instance = new NavigationService();
        }
        return instance;
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

        Scene scene = new Scene(rootLayout, 900, 760);
        // Load CSS theme
        String css =
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setScene(scene);
    }

    /**
     * Handles transitions to different views by loading FXML and injecting
     * ViewModels. Uses no animation (instant switch).
     */
    public void navigateTo(ViewType viewType) {
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
     * Navigates to a view with the specified transition animation, optionally tracking history.
     *
     * @param viewType     The view to navigate to
     * @param type         The type of transition animation
     * @param addToHistory Whether to add this navigation to the history stack
     */
    private void navigateWithTransition(ViewType viewType, TransitionType type, boolean addToHistory) {
        try {
            logger.info("Navigating to: {} with transition: {}", viewType, type);

            // Track navigation history (skip for back navigation)
            if (addToHistory) {
                // Avoid duplicate consecutive entries
                if (navigationHistory.isEmpty() || navigationHistory.peek() != viewType) {
                    navigationHistory.push(viewType);

                    // Limit history size to prevent memory leaks
                    while (navigationHistory.size() > MAX_HISTORY_SIZE) {
                        navigationHistory.removeLast();
                    }
                }
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewType.getFxmlPath()));
            loader.setControllerFactory(viewModelFactory::createController);

            // Cleanup old controller before loading new one
            if (currentController instanceof BaseController bc) {
                bc.cleanup();
            }

            Parent newView = loader.load();

            // Store new controller for future cleanup
            currentController = loader.getController();

            if (type == TransitionType.NONE) {
                rootLayout.setCenter(newView);
                return;
            }

            Parent oldView = (Parent) rootLayout.getCenter();
            if (oldView == null) {
                rootLayout.setCenter(newView);
                return;
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

        } catch (IOException e) {
            logger.error("Failed to navigate to {}: {}", viewType, e.getMessage(), e);
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
        parallel.setOnFinished(e -> rootLayout.setCenter(newView));
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
        // Pop current view from history
        if (!navigationHistory.isEmpty()) {
            navigationHistory.pop();
        }

        // Navigate to previous view or default to DASHBOARD
        ViewType previousView = navigationHistory.isEmpty() ? ViewType.DASHBOARD : navigationHistory.peek();

        // Navigate without adding to history (we're going back, not forward)
        navigateWithTransition(previousView, TransitionType.SLIDE_RIGHT, false);
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
}
