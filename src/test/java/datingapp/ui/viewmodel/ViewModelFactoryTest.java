package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.testutil.TestServiceRegistryBuilder;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.screen.ChatController;
import datingapp.ui.screen.DashboardController;
import datingapp.ui.screen.LoginController;
import datingapp.ui.screen.MatchingController;
import datingapp.ui.screen.ProfileController;
import datingapp.ui.screen.StatsController;
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
@DisplayName("ViewModelFactory")
class ViewModelFactoryTest {

    private ServiceRegistry services;
    private AppSession session;
    private ViewModelFactory factory;

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized
        }
    }

    @BeforeEach
    void setUp() {
        session = AppSession.getInstance();
        session.reset();
        services = buildTestServiceRegistry();
        factory = new ViewModelFactory(services, session);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.dispose();
        }
        session.reset();
    }

    @Test
    @DisplayName("getChatViewModel returns the same cached instance on repeated calls")
    void getChatViewModelReturnsCachedInstance() {
        ChatViewModel first = factory.getChatViewModel();
        ChatViewModel second = factory.getChatViewModel();

        assertNotNull(first);
        assertSame(first, second, "ViewModel should be cached within factory lifetime");
    }

    @Test
    @DisplayName("getMatchingViewModel returns the same cached instance on repeated calls")
    void getMatchingViewModelReturnsCachedInstance() {
        MatchingViewModel first = factory.getMatchingViewModel();
        MatchingViewModel second = factory.getMatchingViewModel();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("getStatsViewModel returns the same cached instance on repeated calls")
    void getStatsViewModelReturnsCachedInstance() {
        StatsViewModel first = factory.getStatsViewModel();
        StatsViewModel second = factory.getStatsViewModel();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("getProfileViewModel returns the same cached instance on repeated calls")
    void getProfileViewModelReturnsCachedInstance() {
        ProfileViewModel first = factory.getProfileViewModel();
        ProfileViewModel second = factory.getProfileViewModel();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("reset clears cache and disposes ViewModels; new instances are distinct")
    void resetClearsCacheAndDisposesViewModels() throws InterruptedException {
        ChatViewModel original = factory.getChatViewModel();
        assertNotNull(original);

        factory.reset();
        drainFxEvents();

        assertTrue(original.isDisposed(), "Original ViewModel should be disposed after reset");

        ChatViewModel afterReset = factory.getChatViewModel();
        assertNotNull(afterReset);
        assertNotSame(original, afterReset, "New ViewModel should be a fresh instance after reset");
    }

    @Test
    @DisplayName("dispose clears cache and disposes ViewModels permanently")
    void disposeClearsCacheAndDisposesViewModelsPermanently() throws InterruptedException {
        ChatViewModel vm = factory.getChatViewModel();
        assertNotNull(vm);

        factory.dispose();
        drainFxEvents();

        assertTrue(vm.isDisposed(), "ViewModel should be disposed after factory dispose");
    }

    @Test
    @DisplayName("reset creates new ViewModel instances for multiple types")
    void resetCreatesNewViewModelInstancesForMultipleTypes() throws InterruptedException {
        // Get ViewModels that share cached adapters (profileNotes and presence)
        ChatViewModel chatBefore = factory.getChatViewModel();
        ProfileViewModel profileBefore = factory.getProfileViewModel();
        assertNotNull(chatBefore);
        assertNotNull(profileBefore);

        factory.reset();
        drainFxEvents();

        // Get new ViewModel instances after reset
        ChatViewModel chatAfter = factory.getChatViewModel();
        ProfileViewModel profileAfter = factory.getProfileViewModel();
        assertNotNull(chatAfter);
        assertNotNull(profileAfter);

        // Verify that new ViewModel instances were created after reset
        assertNotSame(chatBefore, chatAfter, "reset() should create new ChatViewModel instance");
        assertNotSame(profileBefore, profileAfter, "reset() should create new ProfileViewModel instance");
    }

    @Test
    @DisplayName("currentUserProperty synchronizes with AppSession")
    void currentUserPropertySynchronizesWithAppSession() throws InterruptedException {
        User testUser = createMinimalActiveUser("TestUser");

        assertNotNull(factory.currentUserProperty());
        assertNull(factory.currentUserProperty().get());

        session.setCurrentUser(testUser);
        drainFxEvents();

        assertEquals(testUser, factory.currentUserProperty().get());

        session.setCurrentUser(null);
        drainFxEvents();

        assertNull(factory.currentUserProperty().get());
    }

    @Test
    @DisplayName("createController returns the correct controller type for known classes")
    void createControllerReturnsCorrectTypeForKnownClasses() {
        Object chat = factory.createController(ChatController.class);
        Object dashboard = factory.createController(DashboardController.class);
        Object login = factory.createController(LoginController.class);
        Object matching = factory.createController(MatchingController.class);
        Object profile = factory.createController(ProfileController.class);
        Object stats = factory.createController(StatsController.class);

        assertInstanceOf(ChatController.class, chat);
        assertInstanceOf(DashboardController.class, dashboard);
        assertInstanceOf(LoginController.class, login);
        assertInstanceOf(MatchingController.class, matching);
        assertInstanceOf(ProfileController.class, profile);
        assertInstanceOf(StatsController.class, stats);
    }

    @Test
    @DisplayName("createController fails fast for unregistered controller types")
    void createControllerFailsFastForUnregisteredControllerTypes() {
        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> factory.createController(UnregisteredController.class));

        assertTrue(error.getMessage().contains(UnregisteredController.class.getName()));
    }

    @Test
    @DisplayName("getPreferencesStore returns a non-null store")
    void getPreferencesStoreReturnsNonNull() {
        assertNotNull(factory.getPreferencesStore());
    }

    private void drainFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for FX thread");
        }
    }

    private static ServiceRegistry buildTestServiceRegistry() {
        return TestServiceRegistryBuilder.build();
    }

    private static User createMinimalActiveUser(String name) {
        return TestUserFactory.createActiveUser(name);
    }

    private static final class UnregisteredController {}
}
