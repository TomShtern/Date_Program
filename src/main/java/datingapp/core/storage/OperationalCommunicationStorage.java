package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import java.util.UUID;

/** Production/runtime communication-storage contract with required operational capabilities. */
public interface OperationalCommunicationStorage extends CommunicationStorage {

    @Override
    void saveMessageAndUpdateConversationLastMessageAt(Message message);

    @Override
    void deleteConversationWithMessages(String conversationId);

    @Override
    void saveFriendRequestWithNotification(FriendRequest request, Notification notification);

    @Override
    int countPendingFriendRequestsForUser(UUID userId);
}
