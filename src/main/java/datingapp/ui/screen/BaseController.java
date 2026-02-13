package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Subscription;

/**
 * Base class for all controllers providing common lifecycle management.
 *
 * <p>
 * Provides automatic cleanup of Subscription listeners to prevent memory leaks.
 * Controllers should register subscriptions via
 * {@link #addSubscription(Subscription)}
 * and call {@link #cleanup()} when navigating away.
 *
 * <p>
 * Usage:
 *
 * <pre>
 * public class MyController extends BaseController {
 *     &#64;Override
 *     public void initialize(URL location, ResourceBundle resources) {
 *         // Use subscribe() and register for cleanup
 *         addSubscription(nameField.textProperty().subscribe(this::onNameChanged));
 *     }
 * }
 * </pre>
 */
public abstract class BaseController {

    private final List<Subscription> subscriptions = new ArrayList<>();
    private final List<Node> overlays = new ArrayList<>();
    private final List<Animation> animations = new ArrayList<>();

    /**
     * Registers a subscription for automatic cleanup.
     * Call this for every property subscription created in the controller.
     *
     * @param subscription the subscription to manage
     */
    protected void addSubscription(Subscription subscription) {
        if (subscription != null) {
            subscriptions.add(subscription);
        }
    }

    /**
     * Registers an overlay node on the global root stack for cleanup.
     *
     * @param overlay the overlay node to add and track
     */
    protected void registerOverlay(Node overlay) {
        if (overlay == null) {
            return;
        }
        StackPane rootStack = NavigationService.getInstance().getRootStack();
        if (rootStack != null) {
            rootStack.getChildren().add(overlay);
            overlays.add(overlay);
        }
    }

    /**
     * Tracks an animation for automatic cleanup when the controller is disposed.
     * Use this for INDEFINITE animations that would otherwise leak CPU and memory.
     *
     * @param animation the animation to track (may be null)
     */
    protected void trackAnimation(Animation animation) {
        if (animation != null) {
            animations.add(animation);
        }
    }

    /**
     * Cleans up all registered subscriptions.
     * Should be called when navigating away from this controller's view
     * or when the controller is being disposed.
     */
    public void cleanup() {
        // Stop all tracked animations to prevent CPU waste and memory leaks
        animations.forEach(Animation::stop);
        animations.clear();

        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();

        StackPane rootStack = NavigationService.getInstance().getRootStack();
        for (Node overlay : overlays) {
            if (overlay != null) {
                overlay.visibleProperty().unbind();
                overlay.managedProperty().unbind();
                if (rootStack != null) {
                    rootStack.getChildren().remove(overlay);
                }
            }
        }
        overlays.clear();
    }

    /**
     * Default back navigation handler for controllers.
     * Uses history when available, otherwise routes to DASHBOARD.
     */
    @SuppressWarnings("unused")
    @FXML
    protected void handleBack() {
        NavigationService navigationService = NavigationService.getInstance();
        if (navigationService.canGoBack()) {
            navigationService.goBack();
            return;
        }
        navigationService.navigateTo(NavigationService.ViewType.DASHBOARD);
    }

    /**
     * Returns the number of active subscriptions.
     * Useful for debugging and testing.
     *
     * @return count of registered subscriptions
     */
    protected int getSubscriptionCount() {
        return subscriptions.size();
    }
}
