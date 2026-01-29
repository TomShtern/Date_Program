package datingapp.module;

import datingapp.core.MessagingService;
import datingapp.core.RelationshipTransitionService;
import java.util.Objects;

/**
 * Module containing messaging-related services. Handles conversations, messages, and relationship
 * lifecycle transitions (Friend Zone, Graceful Exit).
 */
public record MessagingModule(MessagingService messaging, RelationshipTransitionService transitions) implements Module {

    public MessagingModule {
        Objects.requireNonNull(messaging, "messaging cannot be null");
        Objects.requireNonNull(transitions, "transitions cannot be null");
    }

    /**
     * Creates a MessagingModule with all required services.
     *
     * @param storage The storage module providing data access
     * @return Fully configured MessagingModule
     */
    public static MessagingModule create(StorageModule storage) {
        Objects.requireNonNull(storage, "storage cannot be null");

        MessagingService messaging =
                new MessagingService(storage.conversations(), storage.messages(), storage.matches(), storage.users());

        RelationshipTransitionService transitions = new RelationshipTransitionService(
                storage.matches(), storage.friendRequests(), storage.conversations(), storage.notifications());

        return new MessagingModule(messaging, transitions);
    }
}
