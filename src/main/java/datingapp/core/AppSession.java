package datingapp.core;

import datingapp.core.model.User;
import datingapp.core.model.UserState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("java:S6548")
public final class AppSession {
    private static final AppSession INSTANCE = new AppSession();
    private static final Logger LOGGER = LoggerFactory.getLogger(AppSession.class);

    private User currentUser;
    private final List<Consumer<User>> listeners = new CopyOnWriteArrayList<>();

    private AppSession() {}

    public static AppSession getInstance() {
        return INSTANCE;
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        synchronized (this) {
            this.currentUser = user;
        }
        notifyListeners(user);
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public synchronized boolean isActive() {
        return currentUser != null && currentUser.getState() == UserState.ACTIVE;
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
            try {
                listener.accept(user);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled()) {
                    // Log full exception with stack trace for debugging (EH-002 fix)
                    LOGGER.warn("Session listener threw exception", e);
                }
            }
        }
    }

    /**
     * Resets the session state. Used primarily for testing.
     */
    public synchronized void reset() {
        currentUser = null;
        listeners.clear();
    }
}
