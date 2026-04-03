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
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel for the Social screen.
 * Displays notifications and pending friend requests for the current user.
 * Friend request accept/decline is handled via {@link ConnectionService}.
 * Notifications are fetched via the {@link UiSocialDataAccess} adapter when the use-case path fails.
 */
public class SocialViewModel extends BaseViewModel {

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

    private final ObservableList<Notification> notifications = FXCollections.observableArrayList();
    private final ObservableList<FriendRequestEntry> pendingRequests = FXCollections.observableArrayList();

    private User currentUser;

    public SocialViewModel(
            ConnectionService connectionService,
            UiSocialDataAccess socialDataAccess,
            UiUserStore userStore,
            SocialUseCases socialUseCases,
            AppSession session) {
        this(connectionService, socialDataAccess, userStore, socialUseCases, session, new JavaFxUiThreadDispatcher());
    }

    public SocialViewModel(
            ConnectionService connectionService,
            UiSocialDataAccess socialDataAccess,
            UiUserStore userStore,
            SocialUseCases socialUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("social", uiDispatcher);
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.socialDataAccess = Objects.requireNonNull(socialDataAccess, "socialDataAccess cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.socialUseCases = Objects.requireNonNull(socialUseCases, "socialUseCases cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public final void setErrorHandler(ViewModelErrorSink handler) {
        setErrorSink(handler);
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
        } catch (Exception _) {
            logger.error("Failed to load social data");
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
        handleFriendRequest(entry, FriendRequestAction.ACCEPT, "accept");
    }

    /** Declines a friend zone request from another user. */
    public void declineRequest(FriendRequestEntry entry) {
        handleFriendRequest(entry, FriendRequestAction.DECLINE, "decline");
    }

    private void handleFriendRequest(FriendRequestEntry entry, FriendRequestAction action, String verb) {
        User user = ensureCurrentUser();
        if (user == null || entry == null) {
            return;
        }
        asyncScope.runFireAndForget(verb + " friend request", () -> {
            try {
                var result = socialUseCases.respondToFriendRequest(
                        new RespondFriendRequestCommand(UserContext.ui(user.getId()), entry.requestId(), action));
                if (!result.success()) {
                    notifyError(
                            "Could not " + verb + " request: " + result.error().message());
                }
            } catch (Exception _) {
                logger.error("Failed to {} friend request", verb);
                notifyError("Failed to " + verb + " request.");
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
            } catch (Exception _) {
                logger.warn("Failed to mark notification as read");
            }
        });
    }

    @Override
    protected void onDispose() {
        notifications.clear();
        pendingRequests.clear();
    }

    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = session.getCurrentUser();
        }
        return currentUser;
    }

    private void notifyError(String message) {
        notifyError(message, null);
    }

    // --- Properties ---

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    public ObservableList<FriendRequestEntry> getPendingRequests() {
        return pendingRequests;
    }

    private record SocialData(List<Notification> notifications, List<FriendRequestEntry> pendingRequests) {}
}
