package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DefaultCompatibilityCalculator;
import datingapp.core.matching.DefaultDailyLimitService;
import datingapp.core.matching.DefaultDailyPickService;
import datingapp.core.matching.DefaultStandoutService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.LoginViewModel;
import datingapp.ui.viewmodel.ViewModelFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("LoginController wiring and filter behavior")
class LoginControllerTest {

    private static final String LOGIN_FXML = "/fxml/login.fxml";
    private static final String LOGIN_BUTTON_SELECTOR = "#loginButton";

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @BeforeEach
    void setUpNavigation() throws Exception {
        NavigationService navigationService = NavigationService.getInstance();
        if (navigationService.getRootStack() == null) {
            ServiceRegistry services = buildTestServiceRegistry();
            JavaFxTestSupport.runOnFxAndWait(() -> {
                navigationService.setViewModelFactory(new ViewModelFactory(services, AppSession.getInstance()));
                navigationService.initialize(new Stage());
            });
        }
        navigationService.resetNavigationState();
    }

    @AfterEach
    void tearDown() {
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("successful login routes complete users to dashboard")
    void successfulLoginRoutesCompleteUsersToDashboard() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        User activeUser = createActiveUser("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(activeUser);

        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml(
                LOGIN_FXML,
                () -> new LoginController(
                        viewModel, profileService, config.safety().userTimeZone()));
        Parent root = loaded.root();
        Button loginButton = JavaFxTestSupport.lookup(root, LOGIN_BUTTON_SELECTOR, Button.class);

        JavaFxTestSupport.runOnFxAndWait(() -> viewModel.setSelectedUser(activeUser));
        NavigationService.getInstance().clearHistory();
        JavaFxTestSupport.runOnFxAndWait(loginButton::fire);

        assertEquals(activeUser, AppSession.getInstance().getCurrentUser());
        assertEquals(NavigationService.ViewType.DASHBOARD, peekNavigationHistory());

        viewModel.dispose();
    }

    @Test
    @DisplayName("successful login routes incomplete users to profile")
    void successfulLoginRoutesIncompleteUsersToProfile() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        User incompleteUser = TestUserFactory.createIncompleteUser("Jamie");
        users.save(incompleteUser);

        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml(
                LOGIN_FXML,
                () -> new LoginController(
                        viewModel, profileService, config.safety().userTimeZone()));
        Parent root = loaded.root();
        Button loginButton = JavaFxTestSupport.lookup(root, LOGIN_BUTTON_SELECTOR, Button.class);

        JavaFxTestSupport.runOnFxAndWait(() -> viewModel.setSelectedUser(incompleteUser));
        NavigationService.getInstance().clearHistory();
        JavaFxTestSupport.runOnFxAndWait(loginButton::fire);

        assertEquals(incompleteUser, AppSession.getInstance().getCurrentUser());
        assertEquals(NavigationService.ViewType.PROFILE, peekNavigationHistory());
        assertEquals(LoginViewModel.PostLoginDecision.START_ONBOARDING, viewModel.resolvePostLoginDecision());

        viewModel.dispose();
    }

    @Test
    @DisplayName("create account continues directly into onboarding on profile")
    void createAccountContinuesDirectlyIntoOnboardingOnProfile() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        LoginController controller =
                new LoginController(viewModel, profileService, config.safety().userTimeZone());
        User createdUser = viewModel.createUser("Taylor", 29, Gender.OTHER, Gender.FEMALE);

        JavaFxTestSupport.runOnFxAndWait(() -> controller.continueIntoOnboarding(createdUser));

        assertEquals(createdUser, AppSession.getInstance().getCurrentUser());
        assertEquals(NavigationService.ViewType.PROFILE, peekNavigationHistory());

        viewModel.dispose();
    }

    @Test
    @DisplayName("FXML filter narrows the list and selection enables login")
    void fxmlFilterNarrowsListAndSelectionEnablesLogin() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        users.save(createActiveUser("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE)));
        users.save(createActiveUser("Blair", Gender.FEMALE, EnumSet.of(Gender.MALE)));

        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml(
                LOGIN_FXML,
                () -> new LoginController(
                        viewModel, profileService, config.safety().userTimeZone()));
        Parent root = loaded.root();
        TextField filterField = JavaFxTestSupport.lookup(root, "#filterField", TextField.class);
        Button loginButton = JavaFxTestSupport.lookup(root, LOGIN_BUTTON_SELECTOR, Button.class);
        @SuppressWarnings("unchecked")
        ListView<User> userListView = JavaFxTestSupport.lookup(root, "#userListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                        () -> userListView.getItems().size())
                                == 2;
                    } catch (InterruptedException _) {
                        throw new IllegalStateException("Interrupted while awaiting login list");
                    }
                },
                5000));

        JavaFxTestSupport.runOnFxAndWait(() -> filterField.setText("alex"));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                                () -> userListView.getItems().size())
                                        == 1
                                && "Alex".equals(JavaFxTestSupport.callOnFxAndWait(() -> userListView
                                        .getItems()
                                        .getFirst()
                                        .getName()));
                    } catch (InterruptedException _) {
                        throw new IllegalStateException("Interrupted while awaiting filtered login list");
                    }
                },
                5000));

        assertFalse(JavaFxTestSupport.callOnFxAndWait(loginButton::isDisabled));

        viewModel.dispose();
    }

    @Test
    @DisplayName("user list cell renders verification and activity metadata")
    void userListCellRendersVerificationAndActivityMetadata() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User verifiedUser = createActiveUser("Morgan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        verifiedUser.markVerified();
        users.save(verifiedUser);

        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml(
                LOGIN_FXML,
                () -> new LoginController(
                        viewModel, profileService, config.safety().userTimeZone()));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<User> userListView = JavaFxTestSupport.lookup(root, "#userListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                        () -> userListView.getItems().size())
                                == 1;
                    } catch (InterruptedException _) {
                        throw new IllegalStateException("Interrupted while awaiting login list");
                    }
                },
                5000));

        ListCell<User> cell = JavaFxTestSupport.callOnFxAndWait(
                () -> userListView.getCellFactory().call(userListView));
        Method updateItem =
                javafx.scene.control.Cell.class.getDeclaredMethod("updateItem", Object.class, boolean.class);
        updateItem.setAccessible(true);
        JavaFxTestSupport.runOnFxAndWait(() -> {
            try {
                updateItem.invoke(cell, verifiedUser, false);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });

        HBox container = JavaFxTestSupport.callOnFxAndWait(() -> (HBox) cell.getGraphic());
        VBox textBox = JavaFxTestSupport.callOnFxAndWait(
                () -> (VBox) container.getChildren().get(1));
        Label nameLabel = JavaFxTestSupport.callOnFxAndWait(
                () -> (Label) textBox.getChildren().get(0));
        Label detailsLabel = JavaFxTestSupport.callOnFxAndWait(
                () -> (Label) textBox.getChildren().get(1));
        HBox badgeRow = JavaFxTestSupport.callOnFxAndWait(
                () -> (HBox) textBox.getChildren().get(2));
        Label completionBadge = JavaFxTestSupport.callOnFxAndWait(
                () -> (Label) badgeRow.getChildren().get(0));
        Label activityBadge = JavaFxTestSupport.callOnFxAndWait(
                () -> (Label) badgeRow.getChildren().get(1));

        assertEquals("Morgan, 25", JavaFxTestSupport.callOnFxAndWait(nameLabel::getText));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(detailsLabel::getText).contains("Verified"));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(completionBadge::getText).startsWith("Profile "));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(activityBadge::getText).startsWith("Active "));

        viewModel.dispose();
    }

    @Test
    @DisplayName("Empty user repository keeps login button disabled")
    void emptyUserRepositoryKeepsLoginButtonDisabled() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml(
                LOGIN_FXML,
                () -> new LoginController(
                        viewModel, profileService, config.safety().userTimeZone()));
        Parent root = loaded.root();
        Button loginButton = JavaFxTestSupport.lookup(root, LOGIN_BUTTON_SELECTOR, Button.class);
        @SuppressWarnings("unchecked")
        ListView<User> userListView = JavaFxTestSupport.lookup(root, "#userListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                        () -> userListView.getItems().size())
                                == 0;
                    } catch (InterruptedException _) {
                        throw new IllegalStateException("Interrupted while awaiting empty user list");
                    }
                },
                5000));

        assertTrue(JavaFxTestSupport.callOnFxAndWait(loginButton::isDisabled));

        viewModel.dispose();
    }

    @Test
    @DisplayName("create account dialog factory keeps create disabled until a name is entered")
    void createAccountDialogFactoryKeepsCreateDisabledUntilNameIsEntered() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                AppConfig.defaults(),
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        Dialog<User> dialog =
                JavaFxTestSupport.callOnFxAndWait(() -> CreateAccountDialogFactory.create(new StackPane(), viewModel));
        Button createButton = JavaFxTestSupport.callOnFxAndWait(() -> (Button) dialog.getDialogPane()
                .lookupButton(dialog.getDialogPane().getButtonTypes().getFirst()));
        TextField nameField = JavaFxTestSupport.callOnFxAndWait(
                () -> findFirstTextField((Parent) dialog.getDialogPane().getContent()));

        assertNotNull(nameField);
        assertTrue(JavaFxTestSupport.callOnFxAndWait(createButton::isDisabled));

        JavaFxTestSupport.runOnFxAndWait(() -> nameField.setText("Taylor"));

        assertFalse(JavaFxTestSupport.callOnFxAndWait(createButton::isDisabled));

        viewModel.dispose();
    }

    @Test
    @DisplayName("create account dialog clamps manual age edits into configured bounds")
    void createAccountDialogClampsManualAgeEditsIntoConfiguredBounds() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        LoginViewModel viewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                AppConfig.defaults(),
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        Dialog<User> dialog =
                JavaFxTestSupport.callOnFxAndWait(() -> CreateAccountDialogFactory.create(new StackPane(), viewModel));
        @SuppressWarnings("unchecked")
        Spinner<Integer> ageSpinner = JavaFxTestSupport.callOnFxAndWait(
                () -> JavaFxTestSupport.lookup(dialog.getDialogPane(), "#createAccountAgeSpinner", Spinner.class));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            ageSpinner.getEditor().setText(String.valueOf(viewModel.getMaxAge() + 50));
            ageSpinner.getEditor().fireEvent(new ActionEvent());
        });

        assertEquals(viewModel.getMaxAge(), JavaFxTestSupport.callOnFxAndWait(ageSpinner::getValue));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            ageSpinner.getEditor().setText("not-a-number");
            ageSpinner.getEditor().fireEvent(new ActionEvent());
        });

        assertEquals(25, JavaFxTestSupport.callOnFxAndWait(ageSpinner::getValue));

        viewModel.dispose();
    }

    @Test
    @DisplayName("findFirstTextField returns null for null roots")
    void findFirstTextFieldReturnsNullForNullRoot() {
        assertNull(findFirstTextField(null));
    }

    private static TextField findFirstTextField(Parent root) {
        if (root == null) {
            return null;
        }
        if (root instanceof TextField textField) {
            return textField;
        }
        for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof TextField textField) {
                return textField;
            }
            if (child instanceof Parent parent) {
                TextField nested = findFirstTextField(parent);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static NavigationService.ViewType peekNavigationHistory() throws Exception {
        Field field = NavigationService.class.getDeclaredField("navigationHistory");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Deque<NavigationService.ViewType> history =
                (Deque<NavigationService.ViewType>) field.get(NavigationService.getInstance());
        return history.peek();
    }

    private static ServiceRegistry buildTestServiceRegistry() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();

        AppConfig config = AppConfig.defaults();
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        ValidationService validationService = new ValidationService(config);

        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafety, java.time.ZoneId.of("UTC"));
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactions, trustSafety, analytics, config);
        ProfileService profileService = new ProfileService(users);
        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        datingapp.core.matching.DailyLimitService dailyLimitService =
                new DefaultDailyLimitService(interactions, config);
        datingapp.core.matching.DailyPickService dailyPickService =
                new DefaultDailyPickService(analytics, candidateFinder, config);
        datingapp.core.matching.StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator, users, candidateFinder, new TestStorages.Standouts(), profileService, config);
        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactions, new TestStorages.Undos(), config);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactions)
                .trustSafetyStorage(trustSafety)
                .userStorage(users)
                .activityMetricsService(activityMetricsService)
                .dailyService(recommendationService)
                .undoService(undoService)
                .candidateFinder(candidateFinder)
                .build();
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafety, interactions, users, config, communications)
                .build();
        ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
        MatchQualityService matchQualityService =
                new MatchQualityService(users, interactions, config, compatibilityCalculator);
        datingapp.core.profile.LocationService locationService =
                new datingapp.core.profile.LocationService(validationService);
        datingapp.core.metrics.AchievementService achievementService =
                new DefaultAchievementService(config, analytics, interactions, trustSafety, users, profileService);

        return ServiceRegistry.builder()
                .config(config)
                .userStorage(users)
                .interactionStorage(interactions)
                .communicationStorage(communications)
                .analyticsStorage(analytics)
                .trustSafetyStorage(trustSafety)
                .candidateFinder(candidateFinder)
                .matchingService(matchingService)
                .trustSafetyService(trustSafetyService)
                .activityMetricsService(activityMetricsService)
                .matchQualityService(matchQualityService)
                .profileService(profileService)
                .recommendationService(recommendationService)
                .dailyLimitService(dailyLimitService)
                .dailyPickService(dailyPickService)
                .standoutService(standoutService)
                .undoService(undoService)
                .achievementService(achievementService)
                .connectionService(connectionService)
                .validationService(validationService)
                .locationService(locationService)
                .eventBus(eventBus)
                .build();
    }

    private static User createActiveUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Login test bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
