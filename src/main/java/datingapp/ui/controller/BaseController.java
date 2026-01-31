package datingapp.ui.controller;

import java.util.ArrayList;
import java.util.List;
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
     * Cleans up all registered subscriptions.
     * Should be called when navigating away from this controller's view
     * or when the controller is being disposed.
     */
    public void cleanup() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
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
