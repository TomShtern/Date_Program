package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.SessionSummaryQuery;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.StatsQuery;
import datingapp.app.usecase.profile.ProfileMutationUseCases.DeleteAccountCommand;
import datingapp.app.usecase.profile.ProfileMutationUseCases.SaveProfileCommand;
import datingapp.app.usecase.profile.ProfileMutationUseCases.UpdateDiscoveryPreferencesCommand;
import datingapp.app.usecase.profile.ProfileMutationUseCases.UpdateProfileCommand;
import datingapp.app.usecase.profile.ProfileNotesUseCases.DeleteProfileNoteCommand;
import datingapp.app.usecase.profile.ProfileNotesUseCases.UpsertProfileNoteCommand;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileUseCases")
class ProfileUseCasesTest {

    private static final String AUTHOR_NAME = "Author";
    private static final String SUBJECT_NAME = "Subject";

    private AppConfig config;
    private TestStorages.Users userStorage;
    private ProfileService profileService;
    private ValidationService validationService;
    private ActivityMetricsService metricsService;
    private AchievementService achievementService;
    private TestEventBus eventBus;
    private ProfileUseCases useCases;

    @BeforeEach
    void setUp() {
        config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        var interactionStorage = new TestStorages.Interactions();
        var analyticsStorage = new TestStorages.Analytics();
        var trustSafetyStorage = new TestStorages.TrustSafety();

        profileService = new ProfileService(userStorage);
        validationService = new ValidationService(config);
        metricsService = new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);
        achievementService = TestAchievementService.empty();
        eventBus = new TestEventBus();

        useCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                eventBus);
    }

    @Test
    @DisplayName("saveProfile should activate complete INCOMPLETE profile")
    void saveProfileActivatesCompleteProfile() {
        User user = User.StorageBuilder.create(UUID.randomUUID(), "Ready User", AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .bio("Complete profile bio")
                .birthDate(AppClock.today().minusYears(25))
                .gender(User.Gender.MALE)
                .interestedIn(Set.of(User.Gender.FEMALE))
                .photoUrls(List.of("http://example.com/photo.jpg"))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .maxDistanceKm(config.matching().maxDistanceKm())
                .ageRange(config.validation().minAge(), config.validation().maxAge())
                .pacePreferences(new PacePreferences(
                        MessagingFrequency.OFTEN,
                        TimeToFirstDate.FEW_DAYS,
                        CommunicationStyle.MIX_OF_EVERYTHING,
                        DepthPreference.DEEP_CHAT))
                .build();

        var result = useCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertTrue(result.data().activated());
        assertEquals(User.UserState.ACTIVE, result.data().user().getState());
    }

    @Test
    @DisplayName("saveProfile should publish ProfileCompleted when activation succeeds")
    void saveProfilePublishesProfileCompletedEventWhenActivated() {
        User user = User.StorageBuilder.create(UUID.randomUUID(), "Ready User", AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .bio("Complete profile bio")
                .birthDate(AppClock.today().minusYears(25))
                .gender(User.Gender.MALE)
                .interestedIn(Set.of(User.Gender.FEMALE))
                .photoUrls(List.of("http://example.com/photo.jpg"))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .maxDistanceKm(config.matching().maxDistanceKm())
                .ageRange(config.validation().minAge(), config.validation().maxAge())
                .pacePreferences(new PacePreferences(
                        MessagingFrequency.OFTEN,
                        TimeToFirstDate.FEW_DAYS,
                        CommunicationStyle.MIX_OF_EVERYTHING,
                        DepthPreference.DEEP_CHAT))
                .build();

        List<AppEvent> publishedEvents = new ArrayList<>();
        ProfileUseCases eventUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                capturingEventBus(publishedEvents));

        var result = eventUseCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertEquals(2, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.ProfileSaved);
        assertTrue(publishedEvents.get(1) instanceof AppEvent.ProfileCompleted);
        AppEvent.ProfileCompleted event = (AppEvent.ProfileCompleted) publishedEvents.get(1);
        assertEquals(user.getId(), event.userId());
        assertNotNull(event.occurredAt());
    }

    @Test
    @DisplayName("saveProfile should not publish ProfileCompleted when the profile remains incomplete")
    void saveProfileDoesNotPublishProfileCompletedWhenProfileRemainsIncomplete() {
        User user = User.StorageBuilder.create(UUID.randomUUID(), "Draft User", AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .bio("Draft bio only")
                .build();

        List<AppEvent> publishedEvents = new ArrayList<>();
        ProfileUseCases eventUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                capturingEventBus(publishedEvents));

        var result = eventUseCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertFalse(result.data().activated());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.ProfileSaved);
        assertFalse(publishedEvents.stream().anyMatch(event -> event instanceof AppEvent.ProfileCompleted));
    }

    @Test
    @DisplayName("saveProfile should sanitize name and bio before persisting")
    void saveProfileSanitizesNameAndBio() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "<b>Alice</b>");
        user.setBio("<script>alert('xss')</script>Bio");

        var result = useCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertEquals("Alice", result.data().user().getName());
        assertEquals("Bio", result.data().user().getBio());
    }

    @Test
    @DisplayName("saveProfile returns success when achievement unlock fails after persistence")
    void saveProfileReturnsSuccessWhenAchievementUnlockFails() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Failure User");

        AtomicBoolean eventPublished = new AtomicBoolean(false);
        ProfileUseCases failingUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                throwingAchievementService("achievements down"),
                config,
                new ProfileActivationPolicy(),
                recordingEventBus(eventPublished));

        var result = failingUseCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertTrue(userStorage.get(user.getId()).isPresent());
        assertTrue(eventPublished.get());
        assertNotNull(result.data());
        assertNotNull(result.data().user());
        assertTrue(result.data().newlyUnlocked().isEmpty());
    }

    @Test
    @DisplayName("saveProfile returns success when event publication fails after persistence")
    void saveProfileReturnsSuccessWhenEventPublicationFails() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Event Failure User");

        AtomicBoolean eventPublished = new AtomicBoolean(false);
        ProfileUseCases failingUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                noOpAchievementService(),
                config,
                new ProfileActivationPolicy(),
                throwingEventBus(eventPublished));

        var result = failingUseCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertTrue(eventPublished.get());
        assertTrue(userStorage.get(user.getId()).isPresent());
        assertNotNull(result.data());
        assertNotNull(result.data().user());
    }

    @Test
    @DisplayName("upsertProfileNote should sanitize note content")
    void upsertProfileNoteSanitizesContent() {
        User author = TestUserFactory.createActiveUser(UUID.randomUUID(), AUTHOR_NAME);
        User subject = TestUserFactory.createActiveUser(UUID.randomUUID(), SUBJECT_NAME);
        userStorage.save(author);
        userStorage.save(subject);

        var result = useCases.upsertProfileNote(new UpsertProfileNoteCommand(
                UserContext.cli(author.getId()), subject.getId(), "<img src=x onerror=alert('xss')>Keep"));

        assertTrue(result.success());
        assertEquals("Keep", result.data().content());
    }

    @Test
    @DisplayName("upsertProfileNote should publish ProfileNoteSaved on success")
    void upsertProfileNotePublishesProfileNoteSavedEvent() {
        User author = TestUserFactory.createActiveUser(UUID.randomUUID(), AUTHOR_NAME);
        User subject = TestUserFactory.createActiveUser(UUID.randomUUID(), SUBJECT_NAME);
        userStorage.save(author);
        userStorage.save(subject);

        List<AppEvent> publishedEvents = new ArrayList<>();
        ProfileUseCases eventUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                capturingEventBus(publishedEvents));

        var result = eventUseCases.upsertProfileNote(
                new UpsertProfileNoteCommand(UserContext.cli(author.getId()), subject.getId(), "<b>Keep</b>"));

        assertTrue(result.success());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.ProfileNoteSaved);
        AppEvent.ProfileNoteSaved event = (AppEvent.ProfileNoteSaved) publishedEvents.getFirst();
        assertEquals(author.getId(), event.authorId());
        assertEquals(subject.getId(), event.subjectId());
        assertEquals("Keep".length(), event.contentLength());
    }

    @Test
    @DisplayName("deleteProfileNote should publish ProfileNoteDeleted on success")
    void deleteProfileNotePublishesProfileNoteDeletedEvent() {
        User author = TestUserFactory.createActiveUser(UUID.randomUUID(), AUTHOR_NAME);
        User subject = TestUserFactory.createActiveUser(UUID.randomUUID(), SUBJECT_NAME);
        userStorage.save(author);
        userStorage.save(subject);
        userStorage.saveProfileNote(ProfileNote.create(author.getId(), subject.getId(), "Legacy note"));

        List<AppEvent> publishedEvents = new ArrayList<>();
        ProfileUseCases eventUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                capturingEventBus(publishedEvents));

        var result = eventUseCases.deleteProfileNote(
                new DeleteProfileNoteCommand(UserContext.cli(author.getId()), subject.getId()));

        assertTrue(result.success());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.ProfileNoteDeleted);
        AppEvent.ProfileNoteDeleted event = (AppEvent.ProfileNoteDeleted) publishedEvents.getFirst();
        assertEquals(author.getId(), event.authorId());
        assertEquals(subject.getId(), event.subjectId());
        assertNotNull(event.occurredAt());
    }

    @Test
    @DisplayName("updateDiscoveryPreferences should clamp out-of-range values")
    void updateDiscoveryPreferencesClampsValues() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Preference User");
        userStorage.save(user);

        var result = useCases.updateDiscoveryPreferences(new UpdateDiscoveryPreferencesCommand(
                UserContext.cli(user.getId()), 1, 500, 9_999, Set.of(User.Gender.OTHER)));

        assertTrue(result.success());
        assertEquals(config.validation().minAge(), result.data().getMinAge());
        assertEquals(config.validation().maxAge(), result.data().getMaxAge());
        assertEquals(config.matching().maxDistanceKm(), result.data().getMaxDistanceKm());
        assertTrue(result.data().getInterestedIn().contains(User.Gender.OTHER));
    }

    @Test
    @DisplayName("listUsers and getUserById should return stored users")
    void listUsersAndGetUserByIdReturnStoredUsers() {
        User first = TestUserFactory.createActiveUser(UUID.randomUUID(), "First User");
        User second = TestUserFactory.createActiveUser(UUID.randomUUID(), "Second User");
        userStorage.save(first);
        userStorage.save(second);

        var listResult = useCases.listUsers();
        var getResult = useCases.getUserById(first.getId());

        assertTrue(listResult.success());
        assertTrue(getResult.success());
        assertEquals(2, listResult.data().size());
        assertEquals(first.getId(), getResult.data().getId());
    }

    @Test
    @DisplayName("getUsersByIds returns a stable map and ignores unknown IDs")
    void getUsersByIdsReturnsStableMapAndIgnoresUnknownIds() {
        User first = TestUserFactory.createActiveUser(UUID.randomUUID(), "First User");
        User second = TestUserFactory.createActiveUser(UUID.randomUUID(), "Second User");
        UUID unknownId = UUID.randomUUID();
        userStorage.save(first);
        userStorage.save(second);

        var result = useCases.getUsersByIds(
                new ProfileUseCases.GetUsersByIdsQuery(List.of(second.getId(), unknownId, first.getId())));

        assertTrue(result.success());
        assertEquals(
                List.of(second.getId(), first.getId()),
                List.copyOf(result.data().keySet()));
        assertSame(second, result.data().get(second.getId()));
        assertSame(first, result.data().get(first.getId()));
        assertFalse(result.data().containsKey(unknownId));
    }

    @Test
    @DisplayName("getAchievements and getOrComputeStats should return successful snapshots")
    void achievementsAndStatsQueriesSucceed() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Stats User");
        userStorage.save(user);

        var achievements = useCases.getAchievements(new AchievementsQuery(UserContext.cli(user.getId()), true));
        var stats = useCases.getOrComputeStats(new StatsQuery(UserContext.cli(user.getId())));

        assertTrue(achievements.success());
        assertTrue(stats.success());
        assertNotNull(achievements.data());
        assertNotNull(stats.data());
    }

    @Test
    @DisplayName("getSessionSummary returns the current swipe session snapshot")
    void getSessionSummaryReturnsCurrentSwipeSessionSnapshot() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Session User");
        userStorage.save(user);

        metricsService.recordSwipe(user.getId(), datingapp.core.connection.ConnectionModels.Like.Direction.LIKE, false);
        metricsService.recordSwipe(user.getId(), datingapp.core.connection.ConnectionModels.Like.Direction.PASS, false);

        var result = useCases.getSessionSummary(new SessionSummaryQuery(UserContext.cli(user.getId())));

        assertTrue(result.success());
        assertTrue(result.data().currentSession().isPresent());
        var snapshot = result.data().currentSession().orElseThrow();
        assertEquals(2, snapshot.swipeCount());
        assertEquals(1, snapshot.likeCount());
        assertEquals(1, snapshot.passCount());
        assertNotNull(snapshot.formattedDuration());
    }

    @Test
    @DisplayName("deleteAccount delegates cleanup to the account cleanup storage")
    void deleteAccountDelegatesCleanupToStorage() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Cleanup User");
        userStorage.save(user);

        RecordingAccountCleanupStorage cleanupStorage = new RecordingAccountCleanupStorage();
        ProfileUseCases cleanupUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                eventBus,
                cleanupStorage);

        var result = cleanupUseCases.deleteAccount(
                new DeleteAccountCommand(UserContext.cli(user.getId()), AppEvent.DeletionReason.PRIVACY_REQUEST));

        assertTrue(result.success());
        assertEquals(user.getId(), cleanupStorage.userId.get());
        assertNotNull(cleanupStorage.deletedAt.get());
        assertTrue(cleanupStorage.userSnapshot.get().isDeleted());
        assertEquals(User.UserState.PAUSED, cleanupStorage.userSnapshot.get().getState());
        assertTrue(userStorage.get(user.getId()).orElseThrow().isDeleted());
    }

    @Test
    @DisplayName("deleteAccount should publish AccountDeleted on success")
    void deleteAccountPublishesAccountDeletedEvent() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Delete Me");
        userStorage.save(user);

        List<AppEvent> publishedEvents = new ArrayList<>();
        RecordingAccountCleanupStorage cleanupStorage = new RecordingAccountCleanupStorage();
        ProfileUseCases eventUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                capturingEventBus(publishedEvents),
                cleanupStorage);

        var result = eventUseCases.deleteAccount(
                new DeleteAccountCommand(UserContext.cli(user.getId()), AppEvent.DeletionReason.PRIVACY_REQUEST));

        assertTrue(result.success());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.AccountDeleted);
        AppEvent.AccountDeleted event = (AppEvent.AccountDeleted) publishedEvents.getFirst();
        assertEquals(user.getId(), event.userId());
        assertEquals("PRIVACY_REQUEST", event.reason().name());
        assertEquals(cleanupStorage.deletedAt.get(), event.occurredAt());
    }

    @Test
    @DisplayName("deleteAccount should not mutate caller-visible user state when persistence fails")
    void deleteAccountLeavesUserUnchangedWhenPersistenceFails() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Failing Delete User");
        userStorage.save(user);

        AtomicBoolean eventPublished = new AtomicBoolean(false);
        ProfileUseCases failingUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                recordingEventBus(eventPublished),
                new ThrowingAccountCleanupStorage());

        var result = failingUseCases.deleteAccount(
                new DeleteAccountCommand(UserContext.cli(user.getId()), AppEvent.DeletionReason.PRIVACY_REQUEST));

        assertFalse(result.success());
        assertEquals(User.UserState.ACTIVE, user.getState());
        assertFalse(user.isDeleted());
        assertEquals(
                User.UserState.ACTIVE,
                userStorage.get(user.getId()).orElseThrow().getState());
        assertFalse(userStorage.get(user.getId()).orElseThrow().isDeleted());
        assertFalse(eventPublished.get());
    }

    @Test
    @DisplayName("updateProfile should publish LocationUpdated when coordinates change")
    void updateProfilePublishesLocationUpdatedEvent() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Location User");
        user.setLocation(32.0700, 34.8000);
        userStorage.save(user);

        List<AppEvent> publishedEvents = new ArrayList<>();
        ProfileUseCases eventUseCases = new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                capturingEventBus(publishedEvents));

        var result = eventUseCases.updateProfile(new UpdateProfileCommand(
                UserContext.cli(user.getId()),
                null,
                null,
                null,
                null,
                32.0853,
                34.7818,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        assertTrue(result.success());
        assertEquals(2, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.ProfileSaved);
        assertTrue(publishedEvents.get(1) instanceof AppEvent.LocationUpdated);
        AppEvent.LocationUpdated event = (AppEvent.LocationUpdated) publishedEvents.get(1);
        assertEquals(user.getId(), event.userId());
        assertEquals(32.0853, event.latitude(), 0.0000001);
        assertEquals(34.7818, event.longitude(), 0.0000001);
        assertNotNull(event.occurredAt());
    }

    @Test
    @DisplayName("updateProfile with dealbreakers roundtrip persists dealbreakers exactly (Task 5 Regression)")
    void updateProfileWithDealbreakersRoundtrip() {
        // Arrange - create a user and save it
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Dealbreaker User");
        userStorage.save(user);

        // Create dealbreakers with explicit values
        Set<Lifestyle.Smoking> acceptableSmoking = Set.of(Lifestyle.Smoking.NEVER, Lifestyle.Smoking.SOMETIMES);
        Set<Lifestyle.Drinking> acceptableDrinking = Set.of(Lifestyle.Drinking.SOCIALLY);
        Set<Lifestyle.WantsKids> acceptableKids = Set.of(Lifestyle.WantsKids.SOMEDAY);
        Set<Lifestyle.LookingFor> acceptableLooking = Set.of(Lifestyle.LookingFor.LONG_TERM);
        Set<Lifestyle.Education> acceptableEducation = Set.of(Lifestyle.Education.BACHELORS);

        Dealbreakers dealbreakersToSet = new Dealbreakers(
                acceptableSmoking,
                acceptableDrinking,
                acceptableKids,
                acceptableLooking,
                acceptableEducation,
                160, // minHeightCm
                200, // maxHeightCm
                8 // maxAgeDifference
                );

        // Act - updateProfile with dealbreakers
        var result = useCases.updateProfile(new UpdateProfileCommand(
                UserContext.cli(user.getId()),
                null, // bio
                null, // birthDate
                null, // gender
                null, // interestedIn
                null, // latitude
                null, // longitude
                null, // maxDistanceKm
                null, // minAge
                null, // maxAge
                null, // heightCm
                null, // smoking
                null, // drinking
                null, // wantsKids
                null, // lookingFor
                null, // education
                null, // interests
                dealbreakersToSet // dealbreakers
                ));

        // Assert - updateProfile succeeded
        assertTrue(result.success(), "updateProfile should succeed");

        // Load the user from storage to verify persistence
        var loadedUser = userStorage.get(user.getId());
        assertTrue(loadedUser.isPresent(), "User should be found in storage");

        User persistedUser = loadedUser.get();
        Dealbreakers persistedDealbreakers = persistedUser.getDealbreakers();
        assertNotNull(persistedDealbreakers, "Dealbreakers should be persisted");

        // Verify all dealbreaker fields match exactly
        assertEquals(acceptableSmoking, persistedDealbreakers.acceptableSmoking(), "acceptableSmoking should match");
        assertEquals(acceptableDrinking, persistedDealbreakers.acceptableDrinking(), "acceptableDrinking should match");
        assertEquals(acceptableKids, persistedDealbreakers.acceptableKidsStance(), "acceptableKidsStance should match");
        assertEquals(
                acceptableLooking, persistedDealbreakers.acceptableLookingFor(), "acceptableLookingFor should match");
        assertEquals(
                acceptableEducation, persistedDealbreakers.acceptableEducation(), "acceptableEducation should match");
        assertEquals(160, persistedDealbreakers.minHeightCm(), "minHeightCm should match");
        assertEquals(200, persistedDealbreakers.maxHeightCm(), "maxHeightCm should match");
        assertEquals(8, persistedDealbreakers.maxAgeDifference(), "maxAgeDifference should match");
    }

    @Test
    @DisplayName("updateProfile clears interestedIn and interests when empty sets are provided")
    void updateProfileClearsInterestedInAndInterestsWhenEmptySetsAreProvided() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Clearable User");
        user.setInterestedIn(EnumSet.of(Gender.MALE, Gender.FEMALE));
        user.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE));
        userStorage.save(user);

        var result = useCases.updateProfile(new UpdateProfileCommand(
                UserContext.cli(user.getId()),
                null,
                null,
                null,
                EnumSet.noneOf(Gender.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EnumSet.noneOf(Interest.class),
                null));

        assertTrue(result.success());
        User persistedUser = userStorage.get(user.getId()).orElseThrow();
        assertTrue(persistedUser.getInterestedIn().isEmpty());
        assertTrue(persistedUser.getInterests().isEmpty());
    }

    @Test
    @DisplayName("updateDiscoveryPreferences clears interestedIn when an empty set is provided")
    void updateDiscoveryPreferencesClearsInterestedInWhenEmptySetProvided() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Discovery User");
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        userStorage.save(user);

        var result = useCases.updateDiscoveryPreferences(new UpdateDiscoveryPreferencesCommand(
                UserContext.cli(user.getId()), 22, 35, 40, EnumSet.noneOf(Gender.class)));

        assertTrue(result.success());
        assertTrue(userStorage.get(user.getId()).orElseThrow().getInterestedIn().isEmpty());
    }

    @Test
    @DisplayName("builder rejects missing event bus for production wiring")
    void builderRejectsMissingEventBusForProductionWiring() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, this::buildProfileUseCasesWithoutEventBus);

        assertEquals("eventBus cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("constructor requires non-null event bus")
    void constructorRequiresNonNullEventBus() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, this::newProfileUseCasesWithoutEventBus);

        assertEquals("eventBus cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("getAchievements uses AchievementService without ProfileService fallback")
    void getAchievementsUsesAchievementServiceWithoutProfileServiceFallback() {
        UUID userId = UUID.randomUUID();
        AchievementService unlockedService = TestAchievementService.unlocked(
                datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement.create(
                        userId, datingapp.core.metrics.EngagementDomain.Achievement.FIRST_SPARK));
        ProfileUseCases achievementOnlyUseCases = new ProfileUseCases(
                userStorage,
                null,
                validationService,
                metricsService,
                unlockedService,
                config,
                new ProfileActivationPolicy(),
                new TestEventBus());

        var result = achievementOnlyUseCases.getAchievements(new AchievementsQuery(UserContext.cli(userId), false));

        assertTrue(result.success());
        assertEquals(1, result.data().unlocked().size());
        assertEquals(
                datingapp.core.metrics.EngagementDomain.Achievement.FIRST_SPARK,
                result.data().unlocked().getFirst().achievement());
    }

    private ProfileUseCases buildProfileUseCasesWithoutEventBus() {
        return ProfileUseCases.builder()
                .userStorage(userStorage)
                .profileService(profileService)
                .validationService(validationService)
                .activityMetricsService(metricsService)
                .achievementService(achievementService)
                .config(config)
                .activationPolicy(new ProfileActivationPolicy())
                .build();
    }

    private ProfileUseCases newProfileUseCasesWithoutEventBus() {
        return new ProfileUseCases(
                userStorage,
                profileService,
                validationService,
                metricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                null);
    }

    private static AchievementService noOpAchievementService() {
        return new AchievementService() {
            @Override
            public List<datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement> checkAndUnlock(
                    UUID userId) {
                return List.of();
            }

            @Override
            public List<datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement> getUnlocked(UUID userId) {
                return List.of();
            }

            @Override
            public List<AchievementService.AchievementProgress> getProgress(UUID userId) {
                return List.of();
            }

            @Override
            public Map<
                            datingapp.core.metrics.EngagementDomain.Achievement.Category,
                            List<AchievementService.AchievementProgress>>
                    getProgressByCategory(UUID userId) {
                return Map.of();
            }

            @Override
            public int countUnlocked(UUID userId) {
                return 0;
            }
        };
    }

    private static AchievementService throwingAchievementService(String message) {
        return new AchievementService() {
            @Override
            public List<datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement> checkAndUnlock(
                    UUID userId) {
                throw new IllegalStateException(message);
            }

            @Override
            public List<datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement> getUnlocked(UUID userId) {
                return List.of();
            }

            @Override
            public List<AchievementService.AchievementProgress> getProgress(UUID userId) {
                return List.of();
            }

            @Override
            public Map<
                            datingapp.core.metrics.EngagementDomain.Achievement.Category,
                            List<AchievementService.AchievementProgress>>
                    getProgressByCategory(UUID userId) {
                return Map.of();
            }

            @Override
            public int countUnlocked(UUID userId) {
                return 0;
            }
        };
    }

    private static AppEventBus recordingEventBus(AtomicBoolean published) {
        return new AppEventBus() {
            @Override
            public void publish(AppEvent event) {
                published.set(true);
            }

            @Override
            public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
                // Not needed for these tests.
            }

            @Override
            public <T extends AppEvent> void subscribe(
                    Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
                // Not needed for these tests.
            }
        };
    }

    private static AppEventBus throwingEventBus(AtomicBoolean published) {
        return new AppEventBus() {
            @Override
            public void publish(AppEvent event) {
                published.set(true);
                throw new IllegalStateException("event publication failed");
            }

            @Override
            public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
                // Not needed for these tests.
            }

            @Override
            public <T extends AppEvent> void subscribe(
                    Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
                // Not needed for these tests.
            }
        };
    }

    private static AppEventBus capturingEventBus(List<AppEvent> publishedEvents) {
        return new AppEventBus() {
            @Override
            public void publish(AppEvent event) {
                publishedEvents.add(event);
            }

            @Override
            public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
                // Not needed for these tests.
            }

            @Override
            public <T extends AppEvent> void subscribe(
                    Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
                // Not needed for these tests.
            }
        };
    }

    private static final class RecordingAccountCleanupStorage implements AccountCleanupStorage {
        private final AtomicReference<UUID> userId = new AtomicReference<>();
        private final AtomicReference<Instant> deletedAt = new AtomicReference<>();
        private final AtomicReference<User> userSnapshot = new AtomicReference<>();

        @Override
        public void softDeleteAccount(User user, Instant deletedAt) {
            userId.set(user.getId());
            this.deletedAt.set(deletedAt);
            userSnapshot.set(user);
        }
    }

    private static final class ThrowingAccountCleanupStorage implements AccountCleanupStorage {
        @Override
        public void softDeleteAccount(User user, Instant deletedAt) {
            throw new IllegalStateException("cleanup failed");
        }
    }
}
