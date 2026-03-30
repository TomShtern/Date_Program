package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.model.User;
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
import java.util.Optional;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for ProfileHandler validation and input-gating behavior. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfileHandlerTest {

    private TestStorages.Users userStorage;
    private AppSession session;
    private ValidationService validationService;
    private ProfileUseCases profileUseCases;

    private interface Cleanup extends AutoCloseable {
        @Override
        void close();
    }

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        session = AppSession.getInstance();
        session.reset();
        validationService = new ValidationService(AppConfig.defaults());
        profileUseCases = new ProfileUseCases(
                userStorage,
                null,
                validationService,
                null,
                TestAchievementService.empty(),
                AppConfig.defaults(),
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus());
    }

    @Test
    @DisplayName("createUser rejects overlong names")
    void createUserRejectsOverlongNames() {
        ProfileHandler handler = createHandler(repeat('a', 101) + "\n");

        handler.createUser();

        assertEquals(0, userStorage.findAll().size());
        assertNull(session.getCurrentUser());
    }

    @Test
    @DisplayName("promptBio rejects overlong bios")
    void promptBioRejectsOverlongBios() throws Exception {
        ProfileHandler handler = createHandler(repeat('b', 501) + "\n");
        User user = createEditableUser();

        invokePrompt(handler, "promptBio", user);

        assertNull(user.getBio());
    }

    @Test
    @DisplayName("promptPhoto rejects unsafe URLs")
    void promptPhotoRejectsUnsafeUrls() throws Exception {
        ProfileHandler handler = createHandler("javascript:alert(1)\n");
        User user = createEditableUser();

        invokePrompt(handler, "promptPhoto", user);

        assertTrue(user.getPhotoUrls().isEmpty());
    }

    @Test
    @DisplayName("addNote rejects overlong notes using configured limit")
    void addNoteRejectsOverlongNotesUsingConfiguredLimit() throws Exception {
        AppConfig customConfig = AppConfig.builder().maxProfileNoteLength(5).build();
        ValidationService customValidationService = new ValidationService(customConfig);
        ProfileUseCases customProfileUseCases = new ProfileUseCases(
                userStorage,
                null,
                customValidationService,
                null,
                TestAchievementService.empty(),
                customConfig,
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus());
        InputReader inputReader = new InputReader(new Scanner(new StringReader(repeat('n', 6) + "\n")));
        ProfileHandler handler = new ProfileHandler(
                userStorage, customValidationService, customProfileUseCases, customConfig, session, inputReader);

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

        ProfileUseCases failingUseCases =
                new ProfileUseCases(
                        userStorage,
                        null,
                        validationService,
                        null,
                        TestAchievementService.empty(),
                        AppConfig.defaults(),
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new TestEventBus()) {
                    @Override
                    public UseCaseResult<ProfileUseCases.ProfileSaveResult> saveProfile(
                            ProfileUseCases.SaveProfileCommand command) {
                        return UseCaseResult.failure(UseCaseError.internal("forced-save-failure"));
                    }
                };

        InputReader inputReader = new InputReader(new Scanner(new StringReader("new-bio\n")));
        ProfileHandler handler = new ProfileHandler(
                userStorage, validationService, failingUseCases, AppConfig.defaults(), session, inputReader);

        handler.completeProfile();

        assertEquals("original-bio", session.getCurrentUser().getBio());
        assertEquals(original.getId(), session.getCurrentUser().getId());
    }

    @Test
    @DisplayName("previewProfile uses the configured zone for age display")
    void previewProfileUsesConfiguredZoneForAgeDisplay() {
        AppConfig config =
                AppConfig.builder().userTimeZone(ZoneId.of("Pacific/Honolulu")).build();
        TestStorages.Users previewUsers = new TestStorages.Users();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, previewUsers);
        ProfileUseCases previewUseCases = new ProfileUseCases(
                previewUsers,
                profileService,
                validationService,
                null,
                TestAchievementService.empty(),
                config,
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new TestEventBus());

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
                    previewUsers,
                    validationService,
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

    private ProfileHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new ProfileHandler(
                userStorage, validationService, profileUseCases, AppConfig.defaults(), session, inputReader);
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
}
