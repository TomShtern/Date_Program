package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.TransitionResult;
import datingapp.core.model.User;
import datingapp.ui.viewmodel.UiDataAdapters.UiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Social screen.
 * Displays notifications and pending friend requests for the current user.
 * Friend request accept/decline is handled via {@link ConnectionService}.
 * Notifications are fetched via the {@link UiSocialDataAccess} adapter.
 */
public class SocialViewModel {

    private static final Logger logger = LoggerFactory.getLogger(SocialViewModel.class);

    /**
     * Combines a {@link FriendRequest} with the resolved display name of the sender
     * so the UI can show the user's name instead of a raw UUID.
     */
    public record FriendRequestEntry(FriendRequest request, String fromUserName) {

        public UUID requestId() {
            return request.id();
        }
    }

    private final ConnectionService connectionService;
    private final UiSocialDataAccess socialDataAccess;
    private final UiUserStore userStore;
    private final AppSession session;

    private final ObservableList<Notification> notifications = FXCollections.observableArrayList();
    private final ObservableList<FriendRequestEntry> pendingRequests = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private User currentUser;
    private ViewModelErrorSink errorHandler;

    public SocialViewModel(
            ConnectionService connectionService,
            UiSocialDataAccess socialDataAccess,
            UiUserStore userStore,
            AppSession session) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.socialDataAccess = Objects.requireNonNull(socialDataAccess, "socialDataAccess cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
    }

    /** Initializes the ViewModel by loading the current user and fetching social data. */
    public void initialize() {
        if (ensureCurrentUser() != null) {
            refresh();
        }
    }

    /** Loads both notifications and pending friend requests from the background. */
    public void refresh() {
        User user = ensureCurrentUser();
        if (disposed.get() || user == null) {
            return;
        }

        setLoadingState(true);
        Thread.ofVirtual().name("social-refresh").start(() -> {
            List<Notification> notifs = List.of();
            List<FriendRequestEntry> entries = List.of();
            try {
                notifs = socialDataAccess.getNotifications(user.getId(), false);
                List<FriendRequest> requests = connectionService.getPendingRequestsFor(user.getId());
                entries = resolveRequestEntries(requests);
            } catch (Exception e) {
                logError("Failed to load social data", e);
                notifyError("Could not load social data. Please try again.");
            }

            List<Notification> finalNotifs = notifs;
            List<FriendRequestEntry> finalEntries = entries;
            runOnFx(() -> {
                if (!disposed.get()) {
                    notifications.setAll(finalNotifs);
                    pendingRequests.setAll(finalEntries);
                }
                setLoadingState(false);
            });
        });
    }

    private List<FriendRequestEntry> resolveRequestEntries(List<FriendRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<UUID> fromIds = requests.stream().map(FriendRequest::fromUserId).collect(Collectors.toSet());
        Map<UUID, User> resolved = userStore.findByIds(fromIds);
        return requests.stream()
                .map(r -> {
                    User sender = resolved.get(r.fromUserId());
                    String name =
                            sender != null ? sender.getName() : r.fromUserId().toString();
                    return new FriendRequestEntry(r, name);
                })
                .toList();
    }

    /** Accepts a friend zone request from another user. */
    public void acceptRequest(FriendRequestEntry entry) {
        User user = ensureCurrentUser();
        if (user == null || entry == null) {
            return;
        }
        Thread.ofVirtual().name("social-accept").start(() -> {
            try {
                TransitionResult result = connectionService.acceptFriendZone(entry.requestId(), user.getId());
                if (!result.success()) {
                    notifyError("Could not accept request: " + result.errorMessage());
                }
            } catch (Exception e) {
                logError("Failed to accept friend request", e);
                notifyError("Failed to accept request.");
            }
            runOnFx(this::refresh);
        });
    }

    /** Declines a friend zone request from another user. */
    public void declineRequest(FriendRequestEntry entry) {
        User user = ensureCurrentUser();
        if (user == null || entry == null) {
            return;
        }
        Thread.ofVirtual().name("social-decline").start(() -> {
            try {
                TransitionResult result = connectionService.declineFriendZone(entry.requestId(), user.getId());
                if (!result.success()) {
                    notifyError("Could not decline request: " + result.errorMessage());
                }
            } catch (Exception e) {
                logError("Failed to decline friend request", e);
                notifyError("Failed to decline request.");
            }
            runOnFx(this::refresh);
        });
    }

    /** Marks a notification as read and refreshes the list. */
    public void markNotificationRead(Notification notification) {
        if (notification == null || notification.isRead()) {
            return;
        }
        Thread.ofVirtual().name("social-mark-read").start(() -> {
            try {
                socialDataAccess.markNotificationRead(notification.id());
                runOnFx(this::refresh);
            } catch (Exception e) {
                logWarn("Failed to mark notification as read", e);
            }
        });
    }

    /** Disposes resources. Should be called when the ViewModel is no longer needed. */
    public void dispose() {
        disposed.set(true);
        notifications.clear();
        pendingRequests.clear();
        setLoadingState(false);
    }

    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = session.getCurrentUser();
        }
        return currentUser;
    }

    private void notifyError(String message) {
        if (errorHandler != null) {
            runOnFx(() -> errorHandler.onError(message));
        }
    }

    private void setLoadingState(boolean isLoading) {
        runOnFx(() -> {
            if (loading.get() != isLoading) {
                loading.set(isLoading);
            }
        });
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void logError(String message, Throwable error) {
        if (logger.isErrorEnabled()) {
            logger.error(message, error);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    // --- Properties ---

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    public ObservableList<FriendRequestEntry> getPendingRequests() {
        return pendingRequests;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }
}
