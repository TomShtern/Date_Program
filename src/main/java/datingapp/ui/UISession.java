package datingapp.ui;

import datingapp.core.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Singleton session manager for the JavaFX UI.
 * Tracks the currently logged-in user and provides observable access for data
 * binding.
 */
public final class UISession {
    private static final UISession INSTANCE = new UISession();

    private final ObjectProperty<User> currentUser = new SimpleObjectProperty<>();

    private UISession() {}

    public static UISession getInstance() {
        return INSTANCE;
    }

    public User getCurrentUser() {
        return currentUser.get();
    }

    public void setCurrentUser(User user) {
        this.currentUser.set(user);
    }

    public ObjectProperty<User> currentUserProperty() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser.get() != null;
    }

    public boolean isActive() {
        User user = currentUser.get();
        return user != null && user.getState() == User.State.ACTIVE;
    }

    public void logout() {
        currentUser.set(null);
    }
}
