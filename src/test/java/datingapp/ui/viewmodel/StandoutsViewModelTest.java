package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
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
@DisplayName("StandoutsViewModel async loading and lifecycle tests")
class StandoutsViewModelTest {

    /** Fixed date matching FIXED_INSTANT (2026-02-01T12:00:00Z). */
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 2, 1);

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.TrustSafety trustSafety;
    private TestStorages.Analytics analytics;
    private TestStorages.Standouts standoutStorage;

    private StandoutsViewModel viewModel;
    private User currentUser;

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized by another test class
        }
    }

    @BeforeEach
    void setUp() {
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        trustSafety = new TestStorages.TrustSafety();
        analytics = new TestStorages.Analytics();
        standoutStorage = new TestStorages.Standouts();

        TestClock.setFixed(FIXED_INSTANT);

        AppConfig config = AppConfig.defaults();
        CandidateFinder candidateFinder = new CandidateFinder(users, interactions, trustSafety);
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

        viewModel = new StandoutsViewModel(recommendationService, AppSession.getInstance());

        currentUser = buildActiveUser("Alice");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);
    }

    @AfterEach
    void tearDown() {
        if (viewModel != null) {
            viewModel.dispose();
        }
        TestClock.reset();
        AppSession.getInstance().reset();
    }

    // --- Tests ---

    @Test
    @DisplayName("initialize() with no candidates sets a status message and leaves list empty")
    void shouldSetStatusMessageWhenNoStandouts() throws InterruptedException {
        // No candidates in storage — RecommendationService returns an empty result
        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertTrue(viewModel.getStandouts().isEmpty());
        assertFalse(viewModel.loadingProperty().get());
        // The ViewModel should communicate the empty state to the user via statusMessage
        assertNotNull(viewModel.statusMessageProperty().get());
        assertFalse(viewModel.statusMessageProperty().get().isBlank());
    }

    @Test
    @DisplayName("initialize() with pre-seeded standouts populates the observable list")
    void shouldPopulateListFromPreSeededStandouts() throws InterruptedException {
        // Pre-seed two standout candidates so we bypass the scoring algorithm
        User candidate1 = buildActiveUser("Eve");
        User candidate2 = buildActiveUser("Zoe");
        users.save(candidate1);
        users.save(candidate2);

        List<Standout> seeded = List.of(
                Standout.create(currentUser.getId(), candidate1.getId(), FIXED_DATE, 1, 90, "Great match"),
                Standout.create(currentUser.getId(), candidate2.getId(), FIXED_DATE, 2, 80, "Compatible vibes"));
        standoutStorage.saveStandouts(currentUser.getId(), seeded, FIXED_DATE);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertFalse(viewModel.loadingProperty().get());
        assertEquals(2, viewModel.getStandouts().size());
        assertEquals("Eve", viewModel.getStandouts().get(0).displayName());
        assertEquals("Zoe", viewModel.getStandouts().get(1).displayName());
    }

    @Test
    @DisplayName("StandoutEntry record delegates correctly to underlying Standout and User")
    void standoutEntryShouldDelegateToComponents() throws InterruptedException {
        User candidate = buildActiveUser("Diana");
        users.save(candidate);

        Standout seeded = Standout.create(currentUser.getId(), candidate.getId(), FIXED_DATE, 1, 95, "Top pick");
        standoutStorage.saveStandouts(currentUser.getId(), List.of(seeded), FIXED_DATE);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(1, viewModel.getStandouts().size());

        StandoutsViewModel.StandoutEntry entry = viewModel.getStandouts().get(0);
        assertEquals(candidate.getName(), entry.displayName());
        assertEquals(1, entry.rank());
        assertEquals(95, entry.score());
        assertEquals("Top pick", entry.reason());
        assertEquals(candidate.getId(), entry.userId());
    }

    @Test
    @DisplayName("dispose() clears the standout list and stops loading regardless of in-flight tasks")
    void shouldClearListOnDispose() throws InterruptedException {
        User candidate = buildActiveUser("Grace");
        users.save(candidate);
        standoutStorage.saveStandouts(
                currentUser.getId(),
                List.of(Standout.create(currentUser.getId(), candidate.getId(), FIXED_DATE, 1, 70, "Good fit")),
                FIXED_DATE);

        viewModel.initialize();
        viewModel.dispose(); // Dispose immediately, before the virtual thread may finish

        Thread.sleep(500);
        waitForFxEvents();

        assertTrue(viewModel.getStandouts().isEmpty());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("markInteracted() runs silently without disrupting ViewModel state")
    void markInteractedShouldBeIdempotentAndSafe() throws InterruptedException {
        User candidate = buildActiveUser("Hana");
        users.save(candidate);
        standoutStorage.saveStandouts(
                currentUser.getId(),
                List.of(Standout.create(currentUser.getId(), candidate.getId(), FIXED_DATE, 1, 85, "Close by")),
                FIXED_DATE);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(1, viewModel.getStandouts().size());

        // markInteracted should not throw and must not alter the list or set loading
        StandoutsViewModel.StandoutEntry entry = viewModel.getStandouts().get(0);
        viewModel.markInteracted(entry);
        viewModel.markInteracted(null); // null guard must be handled silently

        Thread.sleep(300);
        waitForFxEvents();

        assertFalse(viewModel.loadingProperty().get());
        assertEquals(1, viewModel.getStandouts().size());
    }

    // --- Helpers ---

    private void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for FX thread");
        }
    }

    private static User buildActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(27));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE));
        user.setAgeRange(20, 45);
        user.setMaxDistanceKm(100);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Standouts test bio");
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
