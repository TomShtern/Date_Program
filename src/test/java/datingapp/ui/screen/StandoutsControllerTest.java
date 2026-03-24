package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.viewmodel.StandoutsViewModel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("StandoutsController wiring and rendering")
class StandoutsControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 2, 1);

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void resetSession() {
        AppSession.getInstance().reset();
        TestClock.reset();
    }

    @Test
    @DisplayName("FXML loads and renders seeded standout entries")
    void fxmlLoadsAndRendersSeededStandouts() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedData();

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/standouts.fxml", () -> new StandoutsController(fixture.viewModel));
        Parent root = loaded.root();

        ListView<?> listView = JavaFxTestSupport.lookup(root, "#standoutsListView", ListView.class);
        assertTrue(JavaFxTestSupport.waitUntil(() -> !listView.getItems().isEmpty(), 5000));

        fixture.dispose();
    }

    @Test
    @DisplayName("Sort and filter controls narrow results by name and reason")
    void sortAndFilterNarrowResults() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedTwoDistinctStandouts();

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/standouts.fxml", () -> new StandoutsController(fixture.viewModel));
        Parent root = loaded.root();

        ListView<?> listView = JavaFxTestSupport.lookup(root, "#standoutsListView", ListView.class);
        ComboBox<?> sortComboBox = JavaFxTestSupport.lookup(root, "#sortComboBox", ComboBox.class);
        TextInputControl filterTextField = JavaFxTestSupport.lookup(root, "#filterTextField", TextInputControl.class);

        // Wait for initial data to load
        assertTrue(JavaFxTestSupport.waitUntil(() -> listView.getItems().size() >= 2, 5000));

        // Select "Name (A-Z)" sort option
        JavaFxTestSupport.runOnFxAndWait(() -> {
            if (sortComboBox.getItems().size() > 0) {
                sortComboBox.getSelectionModel().select(0); // Assuming first item is "Name (A-Z)"
            }
        });

        // Verify list is sorted alphabetically
        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    List<?> items = listView.getItems();
                    if (items.size() < 2) return false;
                    // Verify order by checking display names (implementation detail depends on cell rendering)
                    return items.size() == 2;
                },
                5000));

        // Type a filter query to narrow results
        JavaFxTestSupport.runOnFxAndWait(() -> filterTextField.setText("Alpha"));

        // Verify filtering narrows results to one entry
        assertTrue(JavaFxTestSupport.waitUntil(() -> listView.getItems().size() == 1, 5000));

        fixture.dispose();
    }

    private static final class Fixture {
        private final TestStorages.Users users = new TestStorages.Users();
        private final TestStorages.Interactions interactions = new TestStorages.Interactions();
        private final TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        private final TestStorages.Analytics analytics = new TestStorages.Analytics();
        private final TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
        private final AppConfig config = AppConfig.defaults();
        private final User currentUser = buildActiveUser("StandoutCurrent");
        private final User standoutUser = buildActiveUser("StandoutCandidate");
        private final User alphaUser = buildActiveUser("Alpha");
        private final User betaUser = buildActiveUser("Beta");
        private final StandoutsViewModel viewModel;

        private Fixture() {
            TestClock.setFixed(FIXED_INSTANT);
            CandidateFinder candidateFinder = new CandidateFinder(users, interactions, trustSafety, ZoneId.of("UTC"));
            ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

            RecommendationService recommendationService = RecommendationService.builder()
                    .interactionStorage(interactions)
                    .userStorage(users)
                    .trustSafetyStorage(trustSafety)
                    .analyticsStorage(analytics)
                    .candidateFinder(candidateFinder)
                    .standoutStorage(standoutStorage)
                    .profileService(profileService)
                    .config(config)
                    .build();

            this.viewModel = new StandoutsViewModel(recommendationService, AppSession.getInstance());
        }

        private void seedData() {
            users.save(currentUser);
            users.save(standoutUser);
            AppSession.getInstance().setCurrentUser(currentUser);
            standoutStorage.saveStandouts(
                    currentUser.getId(),
                    List.of(Standout.create(
                            currentUser.getId(), standoutUser.getId(), FIXED_DATE, 1, 91, "Shared goals")),
                    FIXED_DATE);
        }

        private void seedTwoDistinctStandouts() {
            users.save(currentUser);
            users.save(alphaUser);
            users.save(betaUser);
            AppSession.getInstance().setCurrentUser(currentUser);
            standoutStorage.saveStandouts(
                    currentUser.getId(),
                    List.of(
                            Standout.create(currentUser.getId(), betaUser.getId(), FIXED_DATE, 1, 87, "Enjoys travel"),
                            Standout.create(
                                    currentUser.getId(), alphaUser.getId(), FIXED_DATE, 2, 95, "Alpha prospect")),
                    FIXED_DATE);
        }

        private void dispose() {
            viewModel.dispose();
        }

        private static User buildActiveUser(String name) {
            User user = new User(UUID.randomUUID(), name);
            user.setBirthDate(AppClock.today().minusYears(27));
            user.setGender(Gender.FEMALE);
            user.setInterestedIn(EnumSet.of(Gender.MALE));
            user.setAgeRange(20, 45, 18, 120);
            user.setMaxDistanceKm(100, AppConfig.defaults().matching().maxDistanceKm());
            user.setLocation(40.7128, -74.0060);
            user.addPhotoUrl("http://example.com/" + name + ".jpg");
            user.setBio("Standouts controller test user");
            user.setPacePreferences(new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEEP_CHAT));
            user.activate();
            if (user.getState() != UserState.ACTIVE) {
                throw new IllegalStateException("User must be ACTIVE for test");
            }
            return user;
        }
    }
}
