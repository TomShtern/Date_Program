package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.LoadConversationQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.OpenConversationCommand;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.model.User;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Loads conversation and message data for the chat ViewModel.
 */
final class ConversationLoader {
    private final MessagingUseCases messagingUseCases;

    ConversationLoader(MessagingUseCases messagingUseCases) {
        this.messagingUseCases = Objects.requireNonNull(messagingUseCases, "messagingUseCases cannot be null");
    }

    ConversationRefreshData refreshConversations(User user, int unreadCount) {
        var result =
                messagingUseCases.listConversations(new ListConversationsQuery(UserContext.ui(user.getId()), 50, 0));
        if (!result.success()) {
            return new ConversationRefreshData(null, unreadCount);
        }
        return new ConversationRefreshData(
                result.data().conversations(), result.data().totalUnreadCount());
    }

    OpenConversationData openConversation(User user, UUID otherUserId) {
        var result = messagingUseCases.openConversation(
                new OpenConversationCommand(UserContext.ui(user.getId()), otherUserId));
        if (!result.success()) {
            return new OpenConversationData(false, null);
        }
        return new OpenConversationData(true, result.data().preview());
    }

    MessageLoadData loadMessages(User user, UUID otherUserId, String conversationId) {
        var result = messagingUseCases.loadConversation(
                new LoadConversationQuery(UserContext.ui(user.getId()), otherUserId, 100, 0, true));
        if (!result.success()) {
            return new MessageLoadData(conversationId, null, null, null);
        }

        List<Message> messages = result.data().messages();
        Integer unread = null;
        List<ConversationPreview> previews = null;
        var conversationsResult =
                messagingUseCases.listConversations(new ListConversationsQuery(UserContext.ui(user.getId()), 50, 0));
        if (conversationsResult.success()) {
            unread = conversationsResult.data().totalUnreadCount();
            previews = conversationsResult.data().conversations();
        }

        return new MessageLoadData(conversationId, messages, unread, previews);
    }

    record ConversationRefreshData(@Nullable List<ConversationPreview> previews, int unreadCount) {}

    record OpenConversationData(boolean loaded, ConversationPreview preview) {}

    record MessageLoadData(
            String conversationId, List<Message> messages, Integer unreadCount, List<ConversationPreview> previews) {}
}
