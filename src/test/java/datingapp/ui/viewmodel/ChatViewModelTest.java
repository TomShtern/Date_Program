package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.ui.async.UiAsyncTestSupport;
import datingapp.ui.viewmodel.UiDataAdapters.NoOpUiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.NoOpUiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.PresenceStatus;
import datingapp.ui.viewmodel.UiDataAdapters.UiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
    private final AtomicReference<PresenceStatus> presenceStatus = new AtomicReference<>(PresenceStatus.UNKNOWN);
    private final AtomicBoolean remoteTyping = new AtomicBoolean(false);

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
        var eventBus = new InProcessAppEventBus();

        connectionService = new ConnectionService(config, communications, interactions, users);
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(trustSafety, interactions, users, config)
                .build();
        var noteUseCases = createProfileUseCases(users, config, eventBus);
        var messagingUseCases = new datingapp.app.usecase.messaging.MessagingUseCases(connectionService, eventBus);
        var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(connectionService, trustSafetyService);
        UiPresenceDataAccess presenceDataAccess = new UiPresenceDataAccess() {
            @Override
            public PresenceStatus getPresence(UUID userId) {
                return presenceStatus.get();
            }

            @Override
            public boolean isTyping(UUID userId) {
                return remoteTyping.get();
            }
        };

        viewModel = new ChatViewModel(
                messagingUseCases,
                socialUseCases,
                AppSession.getInstance(),
                new datingapp.ui.async.JavaFxUiThreadDispatcher(),
                Duration.ofMillis(75),
                Duration.ofMillis(75),
                new ChatViewModel.ChatUiDependencies(
                        new UseCaseUiProfileNoteDataAccess(noteUseCases.getProfileNotesUseCases()),
                        presenceDataAccess));

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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));
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

    private boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadlineNanos) {
            waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        waitForFxEvents();
        return condition.getAsBoolean();
    }

    private boolean waitUntil(
            UiAsyncTestSupport.QueuedUiThreadDispatcher dispatcher, BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            dispatcher.drainAll();
            if (condition.getAsBoolean()) {
                return true;
            }
            UiAsyncTestSupport.pauseMillis(20);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for queued UI condition");
            }
        }
        dispatcher.drainAll();
        return condition.getAsBoolean();
    }

    @Test
    @DisplayName("refreshConversations updates conversation list on FX thread")
    void shouldRefreshConversations() throws InterruptedException {
        // Send a message to create a conversation
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

        viewModel.refreshConversations();

        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

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
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        // Trigger loading of messages by setting property
        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));

        assertTrue(waitUntil(() -> viewModel.getActiveMessages().size() == 2, 5000));

        assertEquals(2, viewModel.getActiveMessages().size());

        // And it should mark the conversation as read, which triggers another fetch for
        // unreads
        assertEquals(0, viewModel.totalUnreadCountProperty().get());
    }

    @RepeatedTest(10)
    @DisplayName("rapid selection skips stale background loads")
    void shouldSkipStaleBackgroundLoads() throws InterruptedException {
        User user3 = createActiveUser("User3");
        users.save(user3);
        interactions.save(Match.create(currentUser.getId(), user3.getId()));

        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Msg from Other");
        connectionService.sendMessage(user3.getId(), currentUser.getId(), "Msg from User3");

        var eventBus = new InProcessAppEventBus();
        var isolatedMessagingUseCases =
                new datingapp.app.usecase.messaging.MessagingUseCases(connectionService, eventBus);
        var isolatedSocialUseCases = new datingapp.app.usecase.social.SocialUseCases(
                connectionService,
                TrustSafetyService.builder(trustSafety, interactions, users, AppConfig.defaults())
                        .build());
        var isolatedProfileUseCases = createProfileUseCases(users, AppConfig.defaults(), eventBus);
        UiAsyncTestSupport.QueuedUiThreadDispatcher queuedDispatcher =
                new UiAsyncTestSupport.QueuedUiThreadDispatcher();
        ChatViewModel isolatedViewModel = new ChatViewModel(
                isolatedMessagingUseCases,
                isolatedSocialUseCases,
                AppSession.getInstance(),
                queuedDispatcher,
                Duration.ofHours(1),
                Duration.ofHours(1),
                new ChatViewModel.ChatUiDependencies(
                        new UseCaseUiProfileNoteDataAccess(isolatedProfileUseCases.getProfileNotesUseCases()),
                        new UiPresenceDataAccess() {
                            @Override
                            public PresenceStatus getPresence(UUID userId) {
                                return presenceStatus.get();
                            }

                            @Override
                            public boolean isTyping(UUID userId) {
                                return remoteTyping.get();
                            }
                        }));

        try {
            isolatedViewModel.setCurrentUser(currentUser);
            assertTrue(waitUntil(
                    queuedDispatcher, () -> !isolatedViewModel.loadingProperty().get(), Duration.ofSeconds(5)));

            isolatedViewModel.refreshConversations();
            assertTrue(waitUntil(
                    queuedDispatcher, () -> isolatedViewModel.getConversations().size() == 2, Duration.ofSeconds(5)));

            assertEquals(2, isolatedViewModel.getConversations().size());

            // Resolve conversations by user identity, not by list index.
            // Conversations are sorted by lastMessageAt: both timestamps are equal under
            // the fixed clock, so iteration order is non-deterministic.
            UUID user3Id = user3.getId();
            ConversationPreview otherUserConv = isolatedViewModel.getConversations().stream()
                    .filter(p -> !p.otherUser().getId().equals(user3Id))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("otherUser conversation not found"));
            ConversationPreview user3Conv = isolatedViewModel.getConversations().stream()
                    .filter(p -> p.otherUser().getId().equals(user3Id))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("user3 conversation not found"));

            // Select otherUser first, then immediately select User3.
            // The stale load should be skipped; only the latest selection should win.
            isolatedViewModel.selectedConversationProperty().set(otherUserConv);
            isolatedViewModel.selectedConversationProperty().set(user3Conv);

            assertTrue(waitUntil(
                    queuedDispatcher,
                    () -> isolatedViewModel.selectedConversationProperty().get() != null
                            && isolatedViewModel
                                    .selectedConversationProperty()
                                    .get()
                                    .otherUser()
                                    .getId()
                                    .equals(user3Id)
                            && isolatedViewModel.getActiveMessages().size() == 1
                            && !isolatedViewModel.loadingProperty().get(),
                    Duration.ofSeconds(5)));

            assertEquals(1, isolatedViewModel.getActiveMessages().size());
            assertEquals(
                    "Msg from User3",
                    isolatedViewModel.getActiveMessages().get(0).content());
            assertFalse(isolatedViewModel.loadingProperty().get(), "Loading should complete and finish");
        } finally {
            isolatedViewModel.dispose();
        }
    }

    @Test
    @DisplayName("sending a message updates active messages")
    void shouldSendMessageAndUpdate() throws InterruptedException {
        connectionService.getOrCreateConversation(currentUser.getId(), otherUser.getId());
        viewModel.refreshConversations();
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));
        assertTrue(waitUntil(
                () -> !viewModel.getActiveMessages().isEmpty()
                        || !viewModel.loadingProperty().get(),
                5000));

        // Call send message
        boolean accepted = viewModel.sendMessage("Hey there!");
        assertTrue(accepted);

        assertTrue(waitUntil(() -> viewModel.getActiveMessages().size() == 1, 5000));

        assertEquals(1, viewModel.getActiveMessages().size());
        Message sentMessage = viewModel.getActiveMessages().get(0);
        assertTrue(viewModel.isMessageFromCurrentUser(sentMessage));
        assertEquals("Hey there!", sentMessage.content());
    }

    @Test
    @DisplayName("sending property tracks in-flight message sends")
    void sendingPropertyTracksInFlightMessageSends() throws InterruptedException {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        var eventBus = new InProcessAppEventBus();
        var noteUseCases = createProfileUseCases(users, AppConfig.defaults(), eventBus);
        var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(
                connectionService,
                TrustSafetyService.builder(trustSafety, interactions, users, AppConfig.defaults())
                        .build());
        UiPresenceDataAccess delayedPresenceDataAccess = new UiPresenceDataAccess() {
            @Override
            public PresenceStatus getPresence(UUID userId) {
                return presenceStatus.get();
            }

            @Override
            public boolean isTyping(UUID userId) {
                return remoteTyping.get();
            }
        };
        var delayedMessagingUseCases =
                new datingapp.app.usecase.messaging.MessagingUseCases(connectionService, eventBus) {
                    @Override
                    public UseCaseResult<ConnectionService.SendResult> sendMessage(SendMessageCommand command) {
                        sendStarted.countDown();
                        try {
                            assertTrue(releaseSend.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return UseCaseResult.failure(
                                    datingapp.app.usecase.common.UseCaseError.internal(e.getMessage()));
                        }
                        return super.sendMessage(command);
                    }
                };

        ChatViewModel delayedViewModel = new ChatViewModel(
                delayedMessagingUseCases,
                socialUseCases,
                AppSession.getInstance(),
                new datingapp.ui.async.JavaFxUiThreadDispatcher(),
                Duration.ofMillis(75),
                Duration.ofMillis(75),
                new ChatViewModel.ChatUiDependencies(
                        new UseCaseUiProfileNoteDataAccess(noteUseCases.getProfileNotesUseCases()),
                        delayedPresenceDataAccess));

        try {
            delayedViewModel.setCurrentUser(currentUser);
            assertTrue(waitUntil(() -> !delayedViewModel.loadingProperty().get(), 5000));
            connectionService.getOrCreateConversation(currentUser.getId(), otherUser.getId());
            delayedViewModel.refreshConversations();
            assertTrue(waitUntil(() -> delayedViewModel.getConversations().size() == 1, 5000));
            delayedViewModel
                    .selectedConversationProperty()
                    .set(delayedViewModel.getConversations().get(0));

            assertTrue(delayedViewModel.sendMessage("Delayed hello"));
            assertTrue(sendStarted.await(5, TimeUnit.SECONDS));
            assertTrue(waitUntil(delayedViewModel.sendingProperty()::get, 5000));

            releaseSend.countDown();

            assertTrue(waitUntil(() -> !delayedViewModel.sendingProperty().get(), 5000));
            assertTrue(waitUntil(() -> delayedViewModel.getActiveMessages().size() == 1, 5000));
        } finally {
            delayedViewModel.dispose();
        }
    }

    @Test
    @DisplayName("polling refreshes conversations and active messages without manual refresh")
    void shouldPollConversationsAndMessages() throws InterruptedException {
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Initial hello");

        assertTrue(waitUntil(() -> !viewModel.getConversations().isEmpty(), 5000));
        ConversationPreview preview = viewModel.getConversations().get(0);
        viewModel.selectedConversationProperty().set(preview);

        assertTrue(waitUntil(() -> viewModel.getActiveMessages().size() == 1, 5000));

        TestClock.setFixed(FIXED_INSTANT.plusSeconds(1));
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Polled follow-up");

        assertTrue(waitUntil(() -> viewModel.getActiveMessages().size() == 2, 5000));
        assertEquals(
                "Polled follow-up",
                viewModel
                        .getActiveMessages()
                        .get(viewModel.getActiveMessages().size() - 1)
                        .content());
        assertEquals(
                preview.conversation().getId(),
                viewModel.selectedConversationProperty().get().conversation().getId());
    }

    @Test
    @DisplayName("openConversationWithUser reuses openConversation result without reloading conversation list")
    void openConversationWithUserReusesOpenConversationResultWithoutReloadingConversationList()
            throws InterruptedException {
        AtomicInteger listCalls = new AtomicInteger();
        AtomicInteger openCalls = new AtomicInteger();
        var eventBus = new InProcessAppEventBus();
        var countingMessagingUseCases =
                new datingapp.app.usecase.messaging.MessagingUseCases(connectionService, eventBus) {
                    @Override
                    public UseCaseResult<ConversationListResult> listConversations(ListConversationsQuery query) {
                        listCalls.incrementAndGet();
                        return super.listConversations(query);
                    }

                    @Override
                    public UseCaseResult<OpenConversationResult> openConversation(OpenConversationCommand command) {
                        openCalls.incrementAndGet();
                        return super.openConversation(command);
                    }
                };
        var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(
                connectionService,
                TrustSafetyService.builder(trustSafety, interactions, users, AppConfig.defaults())
                        .build());
        var noteUseCases = createProfileUseCases(users, AppConfig.defaults(), eventBus);
        ChatViewModel localViewModel = new ChatViewModel(
                countingMessagingUseCases,
                socialUseCases,
                AppSession.getInstance(),
                new UiAsyncTestSupport.TestUiThreadDispatcher(),
                Duration.ofHours(1),
                Duration.ofHours(1),
                new ChatViewModel.ChatUiDependencies(
                        new UseCaseUiProfileNoteDataAccess(noteUseCases.getProfileNotesUseCases()),
                        new UiPresenceDataAccess() {
                            @Override
                            public PresenceStatus getPresence(UUID userId) {
                                return presenceStatus.get();
                            }

                            @Override
                            public boolean isTyping(UUID userId) {
                                return remoteTyping.get();
                            }
                        }));

        try {
            localViewModel.setCurrentUser(currentUser);
            assertTrue(waitUntil(() -> !localViewModel.loadingProperty().get(), 5000));
            listCalls.set(0);

            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<ConversationPreview> opened = new AtomicReference<>();

            localViewModel.openConversationWithUser(otherUser.getId(), preview -> {
                opened.set(preview);
                ready.countDown();
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            assertTrue(waitUntil(() -> localViewModel.getConversations().size() == 1, 5000));

            assertEquals(1, openCalls.get());
            assertEquals(0, listCalls.get());
            assertNotNull(opened.get());
            assertEquals(
                    opened.get().conversation().getId(),
                    localViewModel.getConversations().getFirst().conversation().getId());
        } finally {
            localViewModel.dispose();
        }
    }

    @Test
    @DisplayName("selecting a conversation loads existing private note")
    void shouldLoadPrivateNoteForSelectedConversation() throws InterruptedException {
        users.saveProfileNote(ProfileNote.create(currentUser.getId(), otherUser.getId(), "Known context"));
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

        viewModel.refreshConversations();
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));

        assertTrue(waitUntil(
                () -> "Known context"
                        .equals(viewModel.profileNoteContentProperty().get()),
                5000));
        assertEquals("Known context", viewModel.profileNoteContentProperty().get());
    }

    @Test
    @DisplayName("saving and deleting a private note updates storage")
    void shouldSaveAndDeletePrivateNote() throws InterruptedException {
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

        viewModel.refreshConversations();
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));
        assertTrue(waitUntil(() -> !viewModel.profileNoteBusyProperty().get(), 5000));

        viewModel.profileNoteContentProperty().set("Remember this detail");
        viewModel.saveSelectedProfileNote();

        assertTrue(waitUntil(
                () -> users.getProfileNote(currentUser.getId(), otherUser.getId())
                                .map(ProfileNote::content)
                                .filter("Remember this detail"::equals)
                                .isPresent()
                        && "Remember this detail"
                                .equals(viewModel.profileNoteContentProperty().get()),
                5000));
        assertEquals(
                "Remember this detail",
                users.getProfileNote(currentUser.getId(), otherUser.getId())
                        .map(ProfileNote::content)
                        .orElseThrow());

        viewModel.deleteSelectedProfileNote();

        assertTrue(waitUntil(
                () -> users.getProfileNote(currentUser.getId(), otherUser.getId())
                                .isEmpty()
                        && viewModel.profileNoteContentProperty().get().isEmpty(),
                5000));
        assertTrue(users.getProfileNote(currentUser.getId(), otherUser.getId()).isEmpty());
        assertEquals("", viewModel.profileNoteContentProperty().get());
    }

    @Test
    @DisplayName("disposing clears profile note state and cancels pending dismiss transition")
    void disposeClearsProfileNoteStateAndCancelsPendingDismissTransition() throws Exception {
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

        viewModel.refreshConversations();
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));
        assertTrue(waitUntil(() -> !viewModel.profileNoteBusyProperty().get(), 5000));

        viewModel.profileNoteContentProperty().set("Dispose me");
        viewModel.saveSelectedProfileNote();

        assertTrue(waitUntil(
                () -> "Private note saved."
                        .equals(viewModel.profileNoteStatusMessageProperty().get()),
                5000));

        viewModel.dispose();

        assertEquals("", viewModel.profileNoteContentProperty().get());
        assertNull(viewModel.profileNoteStatusMessageProperty().get());
        assertFalse(viewModel.profileNoteBusyProperty().get());

        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(4));
        waitForFxEvents();

        assertEquals("", viewModel.profileNoteContentProperty().get());
        assertNull(viewModel.profileNoteStatusMessageProperty().get());
        assertFalse(viewModel.profileNoteBusyProperty().get());
    }

    @Test
    @DisplayName("requesting friend zone from selected conversation creates a pending request")
    void shouldRequestFriendZoneFromSelectedConversation() throws InterruptedException {
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

        viewModel.refreshConversations();
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));
        assertTrue(waitUntil(() -> !viewModel.profileNoteBusyProperty().get(), 5000));

        viewModel.requestFriendZoneForSelectedConversation();

        assertTrue(waitUntil(
                () -> java.util.List.copyOf(communications.getPendingFriendRequestsForUser(otherUser.getId()))
                                .size()
                        == 1,
                5000));
    }

    @Test
    @DisplayName("selected conversation refreshes presence and typing state")
    void shouldRefreshPresenceAndTypingStateForSelectedConversation() throws InterruptedException {
        connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");
        presenceStatus.set(PresenceStatus.ONLINE);
        remoteTyping.set(true);

        viewModel.refreshConversations();
        assertTrue(waitUntil(() -> viewModel.getConversations().size() == 1, 5000));

        viewModel
                .selectedConversationProperty()
                .set(viewModel.getConversations().get(0));

        assertTrue(waitUntil(
                () -> viewModel.presenceStatusProperty().get() == PresenceStatus.ONLINE
                        && viewModel.remoteTypingProperty().get(),
                5000));

        remoteTyping.set(false);
        presenceStatus.set(PresenceStatus.AWAY);

        assertTrue(waitUntil(
                () -> viewModel.presenceStatusProperty().get() == PresenceStatus.AWAY
                        && !viewModel.remoteTypingProperty().get(),
                5000));

        viewModel.selectedConversationProperty().set(null);

        assertTrue(waitUntil(
                () -> viewModel.presenceStatusProperty().get() == PresenceStatus.UNKNOWN
                        && !viewModel.remoteTypingProperty().get(),
                5000));
    }

    @Test
    @DisplayName("refresh failure does not clear selected conversation")
    void refreshFailureDoesNotClearSelectedConversation() throws InterruptedException {
        AtomicBoolean failListConversations = new AtomicBoolean(false);
        var eventBus = new InProcessAppEventBus();
        var flakyMessagingUseCases =
                new datingapp.app.usecase.messaging.MessagingUseCases(connectionService, eventBus) {
                    @Override
                    public UseCaseResult<ConversationListResult> listConversations(ListConversationsQuery query) {
                        if (failListConversations.get()) {
                            throw new RuntimeException("simulated lock timeout");
                        }
                        return super.listConversations(query);
                    }
                };
        var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(
                connectionService,
                TrustSafetyService.builder(trustSafety, interactions, users, AppConfig.defaults())
                        .build());
        var noteUseCases = createProfileUseCases(users, AppConfig.defaults(), eventBus);
        ChatViewModel flakyViewModel = new ChatViewModel(
                flakyMessagingUseCases,
                socialUseCases,
                AppSession.getInstance(),
                new datingapp.ui.async.JavaFxUiThreadDispatcher(),
                Duration.ofMillis(75),
                Duration.ofMillis(75),
                new ChatViewModel.ChatUiDependencies(
                        new UseCaseUiProfileNoteDataAccess(noteUseCases.getProfileNotesUseCases()),
                        new UiPresenceDataAccess() {
                            @Override
                            public PresenceStatus getPresence(UUID userId) {
                                return presenceStatus.get();
                            }

                            @Override
                            public boolean isTyping(UUID userId) {
                                return remoteTyping.get();
                            }
                        }));

        try {
            flakyViewModel.setCurrentUser(currentUser);
            assertTrue(waitUntil(() -> !flakyViewModel.loadingProperty().get(), 5000));

            connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello!");

            flakyViewModel.refreshConversations();
            assertTrue(waitUntil(() -> flakyViewModel.getConversations().size() == 1, 5000));

            ConversationPreview selected = flakyViewModel.getConversations().get(0);
            flakyViewModel.selectedConversationProperty().set(selected);
            assertTrue(waitUntil(
                    () -> flakyViewModel.selectedConversationProperty().get() != null, 5000));

            failListConversations.set(true);
            flakyViewModel.refreshConversations();

            assertTrue(waitUntil(() -> !flakyViewModel.loadingProperty().get(), 5000));
            assertNotNull(flakyViewModel.selectedConversationProperty().get());
            assertEquals(1, flakyViewModel.getConversations().size());
        } finally {
            flakyViewModel.dispose();
        }
    }

    @Test
    @DisplayName("explicit no-op presence dependencies report unsupported state")
    void noOpPresenceDependenciesReportUnsupportedState() {
        ChatViewModel.ChatUiDependencies dependencies =
                new ChatViewModel.ChatUiDependencies(new NoOpUiProfileNoteDataAccess(), new NoOpUiPresenceDataAccess());

        assertFalse(dependencies.presenceDataAccess().isSupported());
        assertFalse(dependencies.presenceDataAccess().unsupportedReason().isBlank());
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(
                18,
                60,
                AppConfig.defaults().validation().minAge(),
                AppConfig.defaults().validation().maxAge());
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
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

    private static ProfileUseCases createProfileUseCases(
            TestStorages.Users users, AppConfig config, InProcessAppEventBus eventBus) {
        ValidationService validationService = new ValidationService(config);
        return new ProfileUseCases(
                users,
                new ProfileService(users),
                validationService,
                new ProfileMutationUseCases(
                        users,
                        validationService,
                        TestAchievementService.empty(),
                        config,
                        new ProfileActivationPolicy(),
                        eventBus),
                new ProfileNotesUseCases(users, validationService, config, eventBus),
                new ProfileInsightsUseCases(TestAchievementService.empty(), null));
    }
}
