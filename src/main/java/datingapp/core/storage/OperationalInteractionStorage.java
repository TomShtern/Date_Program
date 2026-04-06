package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Production/runtime interaction-storage contract with required operational capabilities. */
public interface OperationalInteractionStorage extends InteractionStorage {

    @Override
    Optional<Like> getLikeById(UUID likeId);

    @Override
    Set<UUID> getMatchedCounterpartIds(UUID userId);

    @Override
    int purgeDeletedBefore(Instant threshold);

    @Override
    boolean acceptFriendZoneTransition(Match updatedMatch, FriendRequest acceptedRequest, Notification notification);

    @Override
    boolean gracefulExitTransition(
            Match updatedMatch, Optional<Conversation> archivedConversation, Notification notification);

    @Override
    boolean unmatchTransition(Match updatedMatch, Optional<Conversation> archivedConversation);

    @Override
    boolean blockTransition(
            UUID blockerId, UUID blockedId, Optional<Match> updatedMatch, Optional<Conversation> archivedConversation);
}
