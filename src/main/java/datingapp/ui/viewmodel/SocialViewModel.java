package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestsQuery;
import datingapp.app.usecase.social.SocialUseCases.MarkNotificationReadCommand;
import datingapp.app.usecase.social.SocialUseCases.NotificationsQuery;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.User;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import datingapp.ui.viewmodel.UiDataAdapters.UiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final SocialUseCases socialUseCases;
    private final AppSession session;
    private final ViewModelAsyncScope asyncScope;

    private final ObservableList<Notification> notifications = FXCollections.observableArrayList();
    private final ObservableList<FriendRequestEntry> pendingRequests = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private User currentUser;
    private ViewModelErrorSink errorHandler;

    public SocialViewModel(
            ConnectionService connectionService,
            UiSocialDataAccess socialDataAccess,
            UiUserStore userStore,
            AppSession session) {
        this(connectionService, socialDataAccess, userStore, null, session, new JavaFxUiThreadDispatcher());
    }

    public SocialViewModel(
            ConnectionService connectionService,
            UiSocialDataAccess socialDataAccess,
            UiUserStore userStore,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this(connectionService, socialDataAccess, userStore, null, session, uiDispatcher);
    }

    public SocialViewModel(
            ConnectionService connectionService,
            UiSocialDataAccess socialDataAccess,
            UiUserStore userStore,
            SocialUseCases socialUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.socialDataAccess = Objects.requireNonNull(socialDataAccess, "socialDataAccess cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.socialUseCases =
                socialUseCases != null ? socialUseCases : new SocialUseCases(this.connectionService, null);
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);
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
        if (asyncScope.isDisposed() || user == null) {
            return;
        }

        asyncScope.runLatest("social-refresh", "refresh social data", () -> loadSocialData(user), data -> {
            notifications.setAll(data.notifications());
            pendingRequests.setAll(data.pendingRequests());
        });
    }

    private SocialData loadSocialData(User user) {
        try {
            var notificationsResult =
                    socialUseCases.notifications(new NotificationsQuery(UserContext.ui(user.getId()), false));
            List<Notification> notifs = notificationsResult.success()
                    ? notificationsResult.data()
                    : socialDataAccess.getNotifications(user.getId(), false);

            var requestsResult =
                    socialUseCases.pendingFriendRequests(new FriendRequestsQuery(UserContext.ui(user.getId())));
            List<FriendRequest> requests = requestsResult.success()
                    ? requestsResult.data()
                    : connectionService.getPendingRequestsFor(user.getId());
            List<FriendRequestEntry> entries = resolveRequestEntries(requests);
            return new SocialData(notifs, entries);
        } catch (Exception e) {
            logError("Failed to load social data", e);
            notifyError("Could not load social data. Please try again.");
            return new SocialData(List.of(), List.of());
        }
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
        asyncScope.runFireAndForget("accept friend request", () -> {
            try {
                var result = socialUseCases.respondToFriendRequest(new RespondFriendRequestCommand(
                        UserContext.ui(user.getId()), entry.requestId(), FriendRequestAction.ACCEPT));
                if (!result.success()) {
                    notifyError("Could not accept request: " + result.error().message());
                }
            } catch (Exception e) {
                logError("Failed to accept friend request", e);
                notifyError("Failed to accept request.");
            }
            asyncScope.dispatchToUi(this::refresh);
        });
    }

    /** Declines a friend zone request from another user. */
    public void declineRequest(FriendRequestEntry entry) {
        User user = ensureCurrentUser();
        if (user == null || entry == null) {
            return;
        }
        asyncScope.runFireAndForget("decline friend request", () -> {
            try {
                var result = socialUseCases.respondToFriendRequest(new RespondFriendRequestCommand(
                        UserContext.ui(user.getId()), entry.requestId(), FriendRequestAction.DECLINE));
                if (!result.success()) {
                    notifyError("Could not decline request: " + result.error().message());
                }
            } catch (Exception e) {
                logError("Failed to decline friend request", e);
                notifyError("Failed to decline request.");
            }
            asyncScope.dispatchToUi(this::refresh);
        });
    }

    /** Marks a notification as read and refreshes the list. */
    public void markNotificationRead(Notification notification) {
        User user = ensureCurrentUser();
        if (user == null || notification == null || notification.isRead()) {
            return;
        }
        asyncScope.runFireAndForget("mark notification read", () -> {
            try {
                socialUseCases.markNotificationRead(
                        new MarkNotificationReadCommand(UserContext.ui(user.getId()), notification.id()));
                asyncScope.dispatchToUi(this::refresh);
            } catch (Exception e) {
                logWarn("Failed to mark notification as read", e);
            }
        });
    }

    /** Disposes resources. Should be called when the ViewModel is no longer needed. */
    public void dispose() {
        asyncScope.dispose();
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
            asyncScope.dispatchToUi(() -> errorHandler.onError(message));
        }
    }

    private void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
        }
    }

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "social", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorHandler));
        scope.setLoadingStateConsumer(this::setLoadingState);
        return scope;
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

    private record SocialData(List<Notification> notifications, List<FriendRequestEntry> pendingRequests) {}
}
