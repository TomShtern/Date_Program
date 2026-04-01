package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DevDataSeeder")
class DevDataSeederTest {

    private static final UUID SEED_SENTINEL_ID = UUID.fromString("11111111-1111-1111-1111-000000000001");
    private static final UUID AVITAL_ID = UUID.fromString("11111111-0000-0000-0000-000000000011");
    private static final UUID BATEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000012");

    @Test
    @DisplayName("seed repairs the full user dataset when only the sentinel user exists")
    void seedRepairsFullUserDatasetWhenOnlySentinelExists() {
        TestStorages.Users users = new TestStorages.Users();
        users.save(new User(SEED_SENTINEL_ID, "Partial Sentinel"));

        DevDataSeeder.seed(users);

        assertEquals(30, users.findAll().size());
        assertEquals("Adam Cohen", users.get(SEED_SENTINEL_ID).orElseThrow().getName());
    }

    @Test
    @DisplayName("seed repairs matches and conversation when user seed is only partially present")
    void seedRepairsMatchesAndConversationWhenUserSeedIsOnlyPartiallyPresent() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(communications);
        users.save(new User(SEED_SENTINEL_ID, "Partial Sentinel"));

        DevDataSeeder.seed(users, interactions, communications);

        assertEquals(30, users.findAll().size());
        assertEquals(2, interactions.countMatchesFor(SEED_SENTINEL_ID));
        String conversationId = Conversation.generateId(SEED_SENTINEL_ID, AVITAL_ID);
        assertTrue(communications.getConversation(conversationId).isPresent());
        assertEquals(3, communications.countMessages(conversationId));
        assertTrue(interactions.getByUsers(SEED_SENTINEL_ID, BATEL_ID).isPresent());
    }
}
