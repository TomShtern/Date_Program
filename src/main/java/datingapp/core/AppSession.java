package datingapp.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Unified session management for both CLI and JavaFX interfaces.
 *
 * <p>This singleton replaces the separate CliUtilities.UserSession and ViewModelFactory.UISession
 * with a single source of truth. Supports both plain access (CLI) and listener-based binding
 * (JavaFX).
 *
 * <p>Thread-safe for concurrent access using CopyOnWriteArrayList for listeners.
 */
public final class AppSession {
    private static final AppSession INSTANCE = new AppSession();

    private User currentUser;
    private final List<Consumer<User>> listeners = new CopyOnWriteArrayList<>();

    private AppSession() {}

    public static AppSession getInstance() {
        return INSTANCE;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        notifyListeners(user);
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isActive() {
        return currentUser != null && currentUser.getState() == User.State.ACTIVE;
    }

    public void logout() {
        setCurrentUser(null);
    }

    public void addListener(Consumer<User> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<User> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(User user) {
        for (Consumer<User> listener : listeners) {
            listener.accept(user);
        }
    }

    /**
     * Resets the session state. Used primarily for testing.
     */
    public void reset() {
        currentUser = null;
        listeners.clear();
    }
}
