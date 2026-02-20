package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ChatViewModel Logic and Thread-Safety Test")
class ChatViewModelTest {

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.Communications communications;
    private TestStorages.TrustSafety trustSafety;

    private ChatViewModel viewModel;
    private ConnectionService connectionService;
    private User currentUser;
    private User otherUser;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized
        }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        communications = new TestStorages.Communications();
        trustSafety = new TestStorages.TrustSafety();

        TestClock.setFixed(FIXED_INSTANT);

        AppConfig config = AppConfig.defaults();

        connectionService = new ConnectionService(config, communications, interactions, users);
        TrustSafetyService trustSafetyService = new TrustSafetyService(trustSafety, interactions, users, config);

        viewModel = new ChatViewModel(connectionService, trustSafetyService, AppSession.getInstance());

        currentUser = createActiveUser("CurrentUser");
        otherUser = createActiveUser("OtherUser");

        users.save(currentUser);
        users.save(otherUser);

        // Users must be matched to message
        interactions.save(Match.create(currentUser.getId(), otherUser.getId()));

        AppSession.getInstance().setCurrentUser(currentUser);
        viewModel.setCurrentUser(currentUser);

        // setCurrentUser triggers an async refreshConversations (Thread A). Drain it to
        // completion before each test body starts, so it cannot post a stale
        // updateConversations([]) callback after the test's own refresh has run.
        Thread.sleep(300);
        waitForFxEvents();
    }

    @AfterEach
    void tearDown() {
        if (viewModel != null) {
            viewModel.dispose();
        }
        TestClock.reset();
        AppSession.getInstance().reset();
    }

    private void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for FX thread");
        }
    }

    @Test
    @DisplayName("refreshConversations updates conversation list on FX thread")
    void shouldRefreshConversations() throws InterruptedException {
        // Send a message to create a conversation
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

        viewModel.refreshConversations();

        Thread.sleep(500); // Allow virtual thread to fetch and dispatch
        waitForFxEvents();

        assertEquals(1, viewModel.getConversations().size());
        assertEquals(
                "Hello!",
                viewModel.getConversations().get(0).lastMessage().get().content());
        assertEquals(1, viewModel.totalUnreadCountProperty().get(), "Total unread should be calculated");
    }

    @Test
    @DisplayName("selecting a conversation concurrently fetches messages and tracks token")
    void shouldFetchMessagesConcurrentlyForSelectedConversation() throws InterruptedException {
        connectionService.getOrCreateConversation(currentUser.getId(), otherUser.getId());
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Message 1");
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Message 2");

        viewModel.refreshConversations();
        Thread.sleep(500);
        waitForFxEvents();

        // Trigger loading of messages by setting property
        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(2, viewModel.getActiveMessages().size());

        // And it should mark the conversation as read, which triggers another fetch for
        // unreads
        assertEquals(0, viewModel.totalUnreadCountProperty().get());
    }

    @Test
    @DisplayName("rapid selection skips stale background loads")
    void shouldSkipStaleBackgroundLoads() throws InterruptedException {
        User user3 = createActiveUser("User3");
        users.save(user3);
        interactions.save(Match.create(currentUser.getId(), user3.getId()));

        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Msg from Other");
        connectionService.sendMessage(user3.getId(), currentUser.getId(), "Msg from User3");

        viewModel.refreshConversations();
        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(2, viewModel.getConversations().size());

        // Resolve conversations by user identity, not by list index.
        // Conversations are sorted by lastMessageAt: both timestamps are equal under the
        // fixed clock, so HashMap iteration order is non-deterministic.
        final UUID user3Id = user3.getId();
        ConversationPreview otherUserConv = viewModel.getConversations().stream()
                .filter(p -> !p.otherUser().getId().equals(user3Id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("otherUser conversation not found"));
        ConversationPreview user3Conv = viewModel.getConversations().stream()
                .filter(p -> p.otherUser().getId().equals(user3Id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("user3 conversation not found"));

        // Select otherUser first, then immediately select User3.
        // The stale load (token 1) should be skipped; only the User3 load (token 2) completes.
        viewModel.selectedConversationProperty().set(otherUserConv);
        viewModel.selectedConversationProperty().set(user3Conv);

        Thread.sleep(500);
        waitForFxEvents();

        // The active messages should reflect the 2nd (User3) conversation only
        assertEquals(1, viewModel.getActiveMessages().size());
        assertEquals("Msg from User3", viewModel.getActiveMessages().get(0).content());
        assertFalse(viewModel.loadingProperty().get(), "Loading should complete and finish");
    }

    @Test
    @DisplayName("sending a message updates active messages")
    void shouldSendMessageAndUpdate() throws InterruptedException {
        connectionService.getOrCreateConversation(currentUser.getId(), otherUser.getId());
        viewModel.refreshConversations();
        Thread.sleep(500);
        waitForFxEvents();

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));
        Thread.sleep(500);
        waitForFxEvents();

        // Call send message
        boolean accepted = viewModel.sendMessage("Hey there!");
        assertTrue(accepted);

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(1, viewModel.getActiveMessages().size());
        Message sentMessage = viewModel.getActiveMessages().get(0);
        assertTrue(viewModel.isMessageFromCurrentUser(sentMessage));
        assertEquals("Hey there!", sentMessage.content());
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(18, 60);
        user.setMaxDistanceKm(50);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/photo.jpg");
        user.setBio("Bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
