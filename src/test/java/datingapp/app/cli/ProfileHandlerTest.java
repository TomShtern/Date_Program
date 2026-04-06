package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.model.User;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactions;
    private TestStorages.TrustSafety trustSafety;
    private TestStorages.Analytics analytics;
    private ValidationService validationService;
    private ProfileUseCases profileUseCases;
    private AppSession session;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        trustSafety = new TestStorages.TrustSafety();
        analytics = new TestStorages.Analytics();
        validationService = new ValidationService(AppConfig.defaults());
        profileUseCases = new ProfileUseCases(
                userStorage,
                new ProfileService(userStorage),
                validationService,
                new ProfileMutationUseCases(
                        userStorage,
                        validationService,
                        TestAchievementService.empty(),
                        AppConfig.defaults(),
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new TestEventBus()),
                new ProfileNotesUseCases(userStorage, validationService, AppConfig.defaults(), new TestEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafety, analytics, AppConfig.defaults())));
        session = AppSession.getInstance();
    }

    @Test
    @DisplayName("addNote rejects overlong notes using configured limit")
    void addNoteRejectsOverlongNotesUsingConfiguredLimit() throws Exception {
        AppConfig customConfig = AppConfig.builder().maxProfileNoteLength(5).build();
        ValidationService customValidationService = new ValidationService(customConfig);
        ProfileMutationUseCases customMutationUseCases = new ProfileMutationUseCases(
                userStorage,
                customValidationService,
                TestAchievementService.empty(),
                customConfig,
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus());
        ProfileNotesUseCases customNotesUseCases =
                new ProfileNotesUseCases(userStorage, customValidationService, customConfig, new TestEventBus());
        ProfileUseCases customProfileUseCases = new ProfileUseCases(
                userStorage,
                new ProfileService(userStorage),
                customValidationService,
                customMutationUseCases,
                customNotesUseCases,
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafety, analytics, customConfig)));
        ProfileHandler handler = new ProfileHandler(
                customValidationService,
                new LocationService(customValidationService),
                customProfileUseCases,
                customConfig,
                session,
                new InputReader(new Scanner(new StringReader(repeat('n', 6) + "\n"))));

        UUID authorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        userStorage.save(new User(authorId, "Author"));
        userStorage.save(new User(subjectId, "Subject"));

        Method method = ProfileHandler.class.getDeclaredMethod("addNote", UUID.class, UUID.class, String.class);
        method.setAccessible(true);
        method.invoke(handler, authorId, subjectId, "Subject");

        assertTrue(userStorage.getProfileNote(authorId, subjectId).isEmpty());
    }

    @Test
    @DisplayName("completeProfile keeps session user unchanged when save fails")
    void completeProfileKeepsSessionUserUnchangedWhenSaveFails() {
        User original = createEditableUser();
        original.setBio("original-bio");
        session.setCurrentUser(original);

        ProfileMutationUseCases failingMutation = new ProfileMutationUseCases(
                null,
                validationService,
                TestAchievementService.empty(),
                AppConfig.defaults(),
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus(),
                null);

        ProfileUseCases failingUseCases = createProfileUseCases(
                userStorage,
                failingMutation,
                new ProfileNotesUseCases(userStorage, validationService, AppConfig.defaults(), new TestEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafety, analytics, AppConfig.defaults())));

        ProfileHandler handler = new ProfileHandler(
                validationService,
                new LocationService(validationService),
                failingUseCases,
                AppConfig.defaults(),
                session,
                new InputReader(new Scanner(new StringReader("new-bio\n"))));

        handler.completeProfile();

        assertEquals("original-bio", session.getCurrentUser().getBio());
        assertEquals(original.getId(), session.getCurrentUser().getId());
    }

    @Test
    @DisplayName("previewProfile uses the configured zone for age display")
    void previewProfileUsesConfiguredZoneForAgeDisplay() throws Exception {
        AppConfig config =
                AppConfig.builder().userTimeZone(ZoneId.of("Pacific/Honolulu")).build();
        TestStorages.Users previewUsers = new TestStorages.Users();
        ProfileUseCases previewUseCases = createProfileUseCases(
                previewUsers,
                new ProfileMutationUseCases(
                        previewUsers,
                        validationService,
                        TestAchievementService.empty(),
                        config,
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new TestEventBus()),
                new ProfileNotesUseCases(previewUsers, validationService, config, new TestEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafety, analytics, config)));

        User user = createEditableUser();
        user.setBio("Preview bio");
        user.setBirthDate(LocalDate.of(2000, 3, 29));
        user.setLocation(32.0853, 34.7818);
        previewUsers.save(user);
        session.setCurrentUser(user);

        TimeZone originalZone = TimeZone.getDefault();
        AppClock.setClock(Clock.fixed(Instant.parse("2026-03-29T00:30:00Z"), ZoneId.of("UTC")));
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(ProfileHandler.class);
        Level previousLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);

        try (Cleanup cleanup = () -> {
            handlerLogger.detachAppender(appender);
            handlerLogger.setLevel(previousLevel);
            appender.stop();
            TimeZone.setDefault(originalZone);
            AppClock.reset();
        }) {
            cleanup.getClass();
            ProfileHandler handler = new ProfileHandler(
                    validationService,
                    new LocationService(validationService),
                    previewUseCases,
                    config,
                    session,
                    new InputReader(new Scanner(new StringReader("\n"))));

            handler.previewProfile();

            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("25 years old")));
        }
    }

    @Test
    @DisplayName("invalid preference input keeps current values instead of claiming defaults")
    void invalidPreferenceInputKeepsCurrentValuesInsteadOfClaimingDefaults() throws Exception {
        User user = createEditableUser();
        user.setMaxDistanceKm(80, AppConfig.defaults().matching().maxDistanceKm());
        user.setAgeRange(
                21,
                40,
                AppConfig.defaults().validation().minAge(),
                AppConfig.defaults().validation().maxAge());

        Logger handlerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(ProfileHandler.class);
        Level previousLevel = handlerLogger.getLevel();
        handlerLogger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);

        try {
            ProfileHandler handler = createHandler("999\n50\n20\n");
            invokePrompt(handler, "promptPreferences", user);

            assertEquals(80, user.getMaxDistanceKm());
            assertEquals(21, user.getMinAge());
            assertEquals(40, user.getMaxAge());
            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("Keeping current value")));
        } finally {
            handlerLogger.detachAppender(appender);
            handlerLogger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("promptZipSelection offers approximate fallback for valid unsupported ZIP codes")
    void promptZipSelectionOffersApproximateFallbackForValidUnsupportedZipCodes() throws Exception {
        ProfileHandler handler = createHandler("9999999\ny\n");

        Method method = ProfileHandler.class.getDeclaredMethod("promptZipSelection", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<ResolvedLocation> resolvedLocation = (Optional<ResolvedLocation>) method.invoke(handler, "IL");

        assertTrue(resolvedLocation.isPresent());
        assertTrue(resolvedLocation.orElseThrow().label().contains("Approximate"));
    }

    @Test
    @DisplayName("createUser routes account creation through ProfileMutationUseCases")
    void createUserRoutesAccountCreationThroughProfileMutationUseCases() {
        TestStorages.Users routedStorage = new TestStorages.Users();
        ProfileMutationUseCases mutationUseCases = new ProfileMutationUseCases(
                routedStorage,
                validationService,
                TestAchievementService.empty(),
                AppConfig.defaults(),
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus());
        ProfileUseCases routedUseCases = createProfileUseCases(
                routedStorage,
                mutationUseCases,
                new ProfileNotesUseCases(routedStorage, validationService, AppConfig.defaults(), new TestEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafety, analytics, AppConfig.defaults())));
        ProfileHandler handler = createHandler("New User\n", routedUseCases);

        assertDoesNotThrow(handler::createUser);
        assertEquals(1, routedStorage.findAll().size());
        assertEquals("New User", session.getCurrentUser().getName());
    }

    @Test
    @DisplayName("selectUser loads candidates through ProfileUseCases")
    void selectUserLoadsCandidatesThroughProfileUseCases() {
        User storedUser = TestUserFactory.createActiveUser("Selected User");
        ProfileUseCases routedUseCases =
                new ProfileUseCases(
                        userStorage,
                        new ProfileService(userStorage),
                        validationService,
                        new ProfileMutationUseCases(
                                userStorage,
                                validationService,
                                TestAchievementService.empty(),
                                AppConfig.defaults(),
                                new datingapp.core.workflow.ProfileActivationPolicy(),
                                new TestEventBus()),
                        new ProfileNotesUseCases(
                                userStorage, validationService, AppConfig.defaults(), new TestEventBus()),
                        new ProfileInsightsUseCases(
                                TestAchievementService.empty(),
                                new ActivityMetricsService(
                                        interactions, trustSafety, analytics, AppConfig.defaults()))) {
                    @Override
                    public UseCaseResult<List<User>> listUsers() {
                        return UseCaseResult.success(List.of(storedUser));
                    }

                    @Override
                    public UseCaseResult<User> getUserById(UUID userId) {
                        return UseCaseResult.success(storedUser);
                    }
                };
        ProfileHandler handler = createHandler("1\n", routedUseCases);

        assertDoesNotThrow(handler::selectUser);
        assertEquals(storedUser.getId(), session.getCurrentUser().getId());
    }

    @Test
    @DisplayName("viewAllNotes resolves note subjects through batched ProfileUseCases lookup")
    void viewAllNotesResolvesNoteSubjectsThroughBatchedProfileUseCasesLookup() {
        User author = TestUserFactory.createActiveUser(UUID.randomUUID(), "Author");
        User subject = TestUserFactory.createActiveUser(UUID.randomUUID(), "Subject");
        TestStorages.Users notesStorage = new TestStorages.Users();
        notesStorage.save(author);
        notesStorage.save(subject);
        notesStorage.saveProfileNote(
                datingapp.core.model.ProfileNote.create(author.getId(), subject.getId(), "Test note"));
        ProfileNotesUseCases notesUseCases =
                new ProfileNotesUseCases(notesStorage, validationService, AppConfig.defaults(), new TestEventBus());
        TestStorages.Interactions notesInteractions = new TestStorages.Interactions();
        TestStorages.TrustSafety notesTrustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics notesAnalytics = new TestStorages.Analytics();
        ProfileUseCases routedUseCases =
                new ProfileUseCases(
                        notesStorage,
                        new ProfileService(notesStorage),
                        validationService,
                        new ProfileMutationUseCases(
                                notesStorage,
                                validationService,
                                TestAchievementService.empty(),
                                AppConfig.defaults(),
                                new datingapp.core.workflow.ProfileActivationPolicy(),
                                new TestEventBus()),
                        notesUseCases,
                        new ProfileInsightsUseCases(
                                TestAchievementService.empty(),
                                new ActivityMetricsService(
                                        notesInteractions, notesTrustSafety, notesAnalytics, AppConfig.defaults()))) {
                    @Override
                    public UseCaseResult<Map<UUID, User>> getUsersByIds(GetUsersByIdsQuery query) {
                        return UseCaseResult.success(Map.of(subject.getId(), subject));
                    }
                };
        session.setCurrentUser(author);
        ProfileHandler handler = createHandler("0\n", routedUseCases);

        assertDoesNotThrow(handler::viewAllNotes);
    }

    @Test
    @DisplayName("setDealbreakers persists through app-layer mutation instead of raw storage")
    void setDealbreakersPersistsThroughAppLayerMutationInsteadOfRawStorage() {
        User currentUser = TestUserFactory.createActiveUser(UUID.randomUUID(), "Dealbreaker User");
        TestStorages.Users routedStorage = new TestStorages.Users();
        routedStorage.save(currentUser);
        ProfileMutationUseCases mutationUseCases = new ProfileMutationUseCases(
                routedStorage,
                validationService,
                TestAchievementService.empty(),
                AppConfig.defaults(),
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus());
        TestStorages.Interactions dealbreakInteractions = new TestStorages.Interactions();
        TestStorages.TrustSafety dealbreakTrustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics dealbreakAnalytics = new TestStorages.Analytics();
        ProfileUseCases routedUseCases = createProfileUseCases(
                routedStorage,
                mutationUseCases,
                new ProfileNotesUseCases(routedStorage, validationService, AppConfig.defaults(), new TestEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(
                                dealbreakInteractions,
                                dealbreakTrustSafety,
                                dealbreakAnalytics,
                                AppConfig.defaults())));
        session.setCurrentUser(currentUser);
        ProfileHandler handler = createHandler("7\n0\n", routedUseCases);

        assertDoesNotThrow(handler::setDealbreakers);
        assertTrue(routedStorage.get(currentUser.getId()).isPresent());
    }

    private ProfileUseCases createProfileUseCases(
            datingapp.core.storage.UserStorage userStorage,
            ProfileMutationUseCases profileMutationUseCases,
            ProfileNotesUseCases profileNotesUseCases,
            ProfileInsightsUseCases profileInsightsUseCases) {
        return new ProfileUseCases(
                userStorage,
                new ProfileService(userStorage),
                validationService,
                profileMutationUseCases,
                profileNotesUseCases,
                profileInsightsUseCases);
    }

    private ProfileHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new ProfileHandler(
                validationService,
                new LocationService(validationService),
                profileUseCases,
                AppConfig.defaults(),
                session,
                inputReader);
    }

    private ProfileHandler createHandler(String input, ProfileUseCases useCases) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new ProfileHandler(
                validationService,
                new LocationService(validationService),
                useCases,
                AppConfig.defaults(),
                session,
                inputReader);
    }

    private static void invokePrompt(ProfileHandler handler, String methodName, User user) throws Exception {
        Method method = ProfileHandler.class.getDeclaredMethod(methodName, User.class);
        method.setAccessible(true);
        method.invoke(handler, user);
    }

    private static User createEditableUser() {
        User user = TestUserFactory.createActiveUser("Test User");
        user.setBio(null);
        user.setPhotoUrls(java.util.List.of());
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        return user;
    }

    private static String repeat(char value, int count) {
        return String.valueOf(value).repeat(count);
    }

    /**
     * Functional interface for cleanup operations in try-with-resources blocks.
     */
    private interface Cleanup extends AutoCloseable {
        @Override
        void close() throws Exception;
    }
}
