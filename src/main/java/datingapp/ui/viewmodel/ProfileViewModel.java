package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases.SaveProfileCommand;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ProfileService.CompletionResult;
import datingapp.core.profile.ValidationService;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.core.workflow.WorkflowDecision;
import datingapp.ui.LocalPhotoStore;
import datingapp.ui.OnboardingContext;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

/**
 * ViewModel for the Profile Editor screen.
 * Handles editing of user bio, location, interests, lifestyle, and profile
 * photo.
 */
public class ProfileViewModel extends BaseViewModel {
    private static final String PLACEHOLDER_PHOTO_URL = "placeholder://default-avatar";
    private static final String NONE_SET_LABEL = "None set";
    private static final String SAVE_PHOTO_ERROR = "Failed to save profile photo";
    private static final String DELETE_PHOTO_ERROR = "Failed to delete profile photo";
    private static final String UPDATE_PRIMARY_PHOTO_ERROR = "Failed to update primary photo";
    private static final String REPLACE_PHOTO_ERROR = "Failed to replace profile photo";
    private static final String SAVE_PROFILE_ERROR = "Failed to save profile";
    private static final String PROFILE_EDITOR_INACTIVE = "Profile editor is no longer active";
    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

    private final AppConfig config;

    private final ProfileService profileCompletionService;
    private final ProfileMutationUseCases profileMutationUseCases;
    private final ProfileUseCases profileUseCases;
    private final ProfileActivationPolicy activationPolicy;
    private final AppSession session;
    private final LocationService locationService;
    private final ValidationService validationService;
    private final ProfileDraftAssembler draftAssembler;
    private final PhotoMutationCoordinator photoMutationCoordinator;

    // Observable properties for form binding - Basic Info
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty bio = new SimpleStringProperty("");
    private final StringProperty locationDisplay = new SimpleStringProperty("");
    private final StringProperty interests = new SimpleStringProperty("");
    private final StringProperty completionStatus = new SimpleStringProperty("0%");
    private final StringProperty completionDetails = new SimpleStringProperty("");
    private final StringProperty primaryPhotoUrl = new SimpleStringProperty("");
    private final DoubleProperty latitude = new SimpleDoubleProperty(Double.NaN);
    private final DoubleProperty longitude = new SimpleDoubleProperty(Double.NaN);
    private final javafx.beans.property.BooleanProperty hasLocation = new SimpleBooleanProperty(false);
    private final IntegerProperty photoCount = new SimpleIntegerProperty(0);
    private final javafx.beans.property.BooleanProperty saving = new SimpleBooleanProperty(false);
    private final javafx.beans.property.BooleanProperty onboardingActive = new SimpleBooleanProperty(false);
    private final StringProperty onboardingHeadline = new SimpleStringProperty("");
    private final StringProperty onboardingSummary = new SimpleStringProperty("");
    private final StringProperty primaryActionLabel = new SimpleStringProperty("Save changes");
    private final javafx.collections.ObservableList<String> onboardingChecklist = FXCollections.observableArrayList();
    private final javafx.collections.ObservableList<String> readOnlyOnboardingChecklist =
            FXCollections.unmodifiableObservableList(onboardingChecklist);

    // Multiple photos
    private final javafx.collections.ObservableList<String> photoUrls = FXCollections.observableArrayList();
    private final IntegerProperty currentPhotoIndex = new SimpleIntegerProperty(0);
    private final PhotoCarouselState photoCarousel = new PhotoCarouselState();

    // Gender and preferences
    private final ObjectProperty<Gender> gender = new SimpleObjectProperty<>(null);
    private final ObservableSet<Gender> interestedInGenders = FXCollections.observableSet(EnumSet.noneOf(Gender.class));
    private final ObjectProperty<LocalDate> birthDate = new SimpleObjectProperty<>(null);

    // Lifestyle properties
    private final ObjectProperty<Integer> height = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Lifestyle.Smoking> smoking = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Lifestyle.Drinking> drinking = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Lifestyle.WantsKids> wantsKids = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Lifestyle.LookingFor> lookingFor = new SimpleObjectProperty<>(null);

    // Search preferences properties
    private final StringProperty minAge = new SimpleStringProperty("18");
    private final StringProperty maxAge = new SimpleStringProperty("99");
    private final StringProperty maxDistance = new SimpleStringProperty("50");

    // Dealbreakers property
    private final ObjectProperty<Dealbreakers> dealbreakers = new SimpleObjectProperty<>(Dealbreakers.none());
    private final StringProperty dealbreakersStatus = new SimpleStringProperty(NONE_SET_LABEL);
    private final ObjectProperty<PacePreferences.MessagingFrequency> messagingFrequency = new SimpleObjectProperty<>();
    private final ObjectProperty<PacePreferences.TimeToFirstDate> timeToFirstDate = new SimpleObjectProperty<>();
    private final ObjectProperty<PacePreferences.CommunicationStyle> communicationStyle = new SimpleObjectProperty<>();
    private final ObjectProperty<PacePreferences.DepthPreference> depthPreference = new SimpleObjectProperty<>();
    private final javafx.beans.property.BooleanProperty hasUnsavedChanges = new SimpleBooleanProperty(false);

    // Selected interests (observable set for multi-selection)
    private final ObservableSet<Interest> selectedInterests =
            FXCollections.observableSet(EnumSet.noneOf(Interest.class));

    private OnboardingContext onboardingContext;
    private String lastSavedSnapshot = "";

    ProfileViewModel(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {
        this(createDependencies(
                userStore, profileCompletionService, profileUseCases, config, session, uiDispatcher, activationPolicy));
    }

    ProfileViewModel(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileMutationUseCases profileMutationUseCases,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {
        this(new Dependencies(
                userStore,
                profileCompletionService,
                profileMutationUseCases,
                profileUseCases,
                config,
                session,
                new ValidationService(config),
                new LocationService(new ValidationService(config)),
                uiDispatcher,
                activationPolicy));
    }

    public ProfileViewModel(Dependencies dependencies) {
        super("profile", dependencies.uiDispatcher());
        this.profileCompletionService = Objects.requireNonNull(
                dependencies.profileCompletionService(), "profileCompletionService cannot be null");
        this.profileMutationUseCases = dependencies.profileMutationUseCases();
        this.profileUseCases = dependencies.profileUseCases();
        this.config = Objects.requireNonNull(dependencies.config(), "config cannot be null");
        this.activationPolicy =
                Objects.requireNonNull(dependencies.activationPolicy(), "activationPolicy cannot be null");
        this.session = Objects.requireNonNull(dependencies.session(), "session cannot be null");
        this.validationService =
                Objects.requireNonNull(dependencies.validationService(), "validationService cannot be null");
        this.locationService = Objects.requireNonNull(dependencies.locationService(), "locationService cannot be null");
        this.draftAssembler = new ProfileDraftAssembler(config);
        this.photoMutationCoordinator = new PhotoMutationCoordinator(
                Objects.requireNonNull(dependencies.userStore(), "userStore cannot be null"), new LocalPhotoStore());
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    @Override
    protected void onDispose() {
        selectedInterests.clear();
        photoUrls.clear();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        setErrorSink(handler);
    }

    public void setOnboardingContext(OnboardingContext onboardingContext) {
        this.onboardingContext = onboardingContext;
        refreshOnboardingState();
    }

    public boolean isIncompleteLoginOnboarding() {
        return onboardingContext != null
                && onboardingContext.entryReason() == OnboardingContext.EntryReason.INCOMPLETE_LOGIN;
    }

    /**
     * Loads the current user's data into the form properties.
     */
    public void loadCurrentUser() {
        User user = session.getCurrentUser();
        if (user == null) {
            logWarn("No current user to load");
            return;
        }

        logInfo("Loading profile for user: {}", user.getName());

        // Basic info
        name.set(user.getName());
        bio.set(user.getBio() != null ? user.getBio() : "");
        loadLocation(user);

        // Gender and interested in
        gender.set(user.getGender());
        interestedInGenders.clear();
        if (user.getInterestedIn() != null && !user.getInterestedIn().isEmpty()) {
            interestedInGenders.addAll(user.getInterestedIn());
        }
        birthDate.set(user.getBirthDate());

        // Format interests as comma-separated list
        if (user.getInterests() != null && !user.getInterests().isEmpty()) {
            String interestStr = user.getInterests().stream()
                    .map(Interest::getDisplayName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            interests.set(interestStr);
            // Populate selected interests set
            selectedInterests.clear();
            selectedInterests.addAll(user.getInterests());
        } else {
            interests.set("");
            selectedInterests.clear();
        }

        // Lifestyle fields
        height.set(user.getHeightCm());
        smoking.set(user.getSmoking());
        drinking.set(user.getDrinking());
        wantsKids.set(user.getWantsKids());
        lookingFor.set(user.getLookingFor());

        // Search preferences
        minAge.set(String.valueOf(user.getMinAge()));
        maxAge.set(String.valueOf(user.getMaxAge()));
        maxDistance.set(String.valueOf(user.getMaxDistanceKm()));

        // Dealbreakers
        Dealbreakers db = user.getDealbreakers();
        if (db != null) {
            dealbreakers.set(db);
            updateDealbreakersStatus(db);
        } else {
            dealbreakers.set(Dealbreakers.none());
            dealbreakersStatus.set(NONE_SET_LABEL);
        }

        loadPacePreferences(user.getPacePreferences());

        // Primary photo
        updatePrimaryPhoto(user);

        // Calculate completion using static method
        CompletionResult completion = updateCompletion(user);
        updateOnboardingState(user, completion);
        markCurrentStateSaved();
    }

    private void loadPacePreferences(PacePreferences pacePreferences) {
        if (pacePreferences == null) {
            messagingFrequency.set(null);
            timeToFirstDate.set(null);
            communicationStyle.set(null);
            depthPreference.set(null);
            return;
        }
        messagingFrequency.set(pacePreferences.messagingFrequency());
        timeToFirstDate.set(pacePreferences.timeToFirstDate());
        communicationStyle.set(pacePreferences.communicationStyle());
        depthPreference.set(pacePreferences.depthPreference());
    }

    private void updateDealbreakersStatus(Dealbreakers db) {
        if (!db.hasAnyDealbreaker()) {
            dealbreakersStatus.set(NONE_SET_LABEL);
            return;
        }

        int count = countDealbreakers(db);
        dealbreakersStatus.set(formatDealbreakersStatus(count));
    }

    private int countDealbreakers(Dealbreakers db) {
        int count = 0;
        if (db.hasSmokingDealbreaker()) {
            count++;
        }
        if (db.hasDrinkingDealbreaker()) {
            count++;
        }
        if (db.hasKidsDealbreaker()) {
            count++;
        }
        if (db.hasLookingForDealbreaker()) {
            count++;
        }
        if (db.hasEducationDealbreaker()) {
            count++;
        }
        if (db.hasHeightDealbreaker()) {
            count++;
        }
        if (db.hasAgeDealbreaker()) {
            count++;
        }
        return count;
    }

    private static String formatDealbreakersStatus(int count) {
        return count + " filter" + (count != 1 ? "s" : "") + " set";
    }

    private CompletionResult updateCompletion(User user) {
        try {
            CompletionResult result;
            if (profileUseCases != null) {
                var useCaseResult = profileUseCases.calculateCompletion(user);
                if (!useCaseResult.success()) {
                    throw new IllegalStateException(useCaseResult.error().message());
                }
                result = useCaseResult.data();
            } else {
                result = profileCompletionService.calculate(user);
            }
            completionStatus.set(result.getDisplayString());
            completionDetails.set(buildCompletionDetails(result));
            return result;
        } catch (Exception e) {
            logError("Failed to calculate profile completion", e);
            notifyError("Failed to calculate profile completion", e);
            completionStatus.set("--");
            completionDetails.set("Unable to calculate completion");
            return null;
        }
    }

    private void refreshOnboardingState() {
        refreshOnboardingState(session.getCurrentUser());
    }

    private void refreshOnboardingState(User user) {
        if (user == null) {
            clearOnboardingState();
            return;
        }
        CompletionResult completion = updateCompletion(user);
        updateOnboardingState(user, completion);
    }

    private void updateOnboardingState(User user, CompletionResult completion) {
        if (onboardingContext == null
                || user == null
                || user.getState() == User.UserState.ACTIVE
                || completion == null) {
            clearOnboardingState();
            return;
        }

        WorkflowDecision activationDecision = activationPolicy.canActivate(user);
        ProfileOnboardingState state = ProfileOnboardingState.from(true, activationDecision, completion.nextSteps());
        onboardingActive.set(state.active());
        onboardingHeadline.set(state.headline());
        onboardingSummary.set(state.summary());
        primaryActionLabel.set(state.primaryActionLabel());
        onboardingChecklist.setAll(state.checklist());
    }

    private void clearOnboardingState() {
        onboardingActive.set(false);
        onboardingHeadline.set("");
        onboardingSummary.set("");
        primaryActionLabel.set("Save changes");
        onboardingChecklist.clear();
    }

    private void updatePrimaryPhoto(User user) {
        if (user == null) {
            primaryPhotoUrl.set("");
            photoCarousel.setPhotos(List.of());
            syncPhotoCarousel();
            return;
        }
        photoCarousel.setPhotos(user.getPhotoUrls());
        syncPhotoCarousel();
    }

    private void loadLocation(User user) {
        if (user != null && user.hasLocation()) {
            applyLocationDisplay(
                    user.getLat(), user.getLon(), locationService.formatForDisplay(user.getLat(), user.getLon()));
            return;
        }
        clearLocation();
    }

    private String formatLocation(double lat, double lon) {
        return String.format(Locale.ROOT, "%.4f, %.4f", lat, lon);
    }

    private void updateLocationDisplay() {
        if (hasLocation.get() && !Double.isNaN(latitude.get()) && !Double.isNaN(longitude.get())) {
            locationDisplay.set(locationService.formatForDisplay(latitude.get(), longitude.get()));
            return;
        }
        locationDisplay.set("");
    }

    /**
     * Saves the profile changes to storage.
     */
    public void save() {
        saveAsync(null);
    }

    public void saveAsync(Consumer<SaveOutcome> onComplete) {
        if (isDisposed()) {
            logWarn("ProfileViewModel is disposed; skipping save");
            completeSave(onComplete, SaveOutcome.FAILED);
            return;
        }
        if (saving.get()) {
            logWarn("Profile save already in progress");
            return;
        }

        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logWarn("No current user to save");
            completeSave(onComplete, SaveOutcome.FAILED);
            return;
        }

        User draftUser;
        try {
            draftUser = createDraftUser();
        } catch (Exception e) {
            logError("Failed to prepare profile draft", e);
            notifyError(SAVE_PROFILE_ERROR, e);
            completeSave(onComplete, SaveOutcome.FAILED);
            return;
        }

        logInfo("Saving profile for user: {}", currentUser.getName());
        saving.set(true);
        asyncScope.runFireAndForget("profile-save", () -> persistProfileInBackground(draftUser, onComplete));
    }

    private void persistProfileInBackground(User draftUser, Consumer<SaveOutcome> onComplete) {
        SaveOperationResult result;
        try {
            if (isDisposed()) {
                result = SaveOperationResult.failure(new IllegalStateException(PROFILE_EDITOR_INACTIVE));
            } else if (profileMutationUseCases == null) {
                result = SaveOperationResult.failure(
                        new IllegalStateException("Profile mutation use cases are not configured"));
            } else {
                result = persistProfileViaMutationUseCase(draftUser);
            }
        } catch (Exception e) {
            logError(SAVE_PROFILE_ERROR, e);
            result = SaveOperationResult.failure(e);
        }

        SaveOperationResult completedResult = result;
        asyncScope.dispatchToUi(() -> applySaveOperationResult(completedResult, onComplete));
    }

    private SaveOperationResult persistProfileViaMutationUseCase(User user) {
        var saveResult =
                profileMutationUseCases.saveProfile(new SaveProfileCommand(UserContext.ui(user.getId()), user));
        if (!saveResult.success()) {
            return SaveOperationResult.failure(
                    new IllegalStateException(saveResult.error().message()));
        }

        User savedUser = saveResult.data().user();
        SaveOutcome outcome = resolveSaveOutcome(savedUser);
        return SaveOperationResult.success(savedUser, outcome);
    }

    private static SaveOutcome resolveSaveOutcome(User user) {
        return user != null && user.getState() == User.UserState.ACTIVE
                ? SaveOutcome.ACTIVATED
                : SaveOutcome.SAVED_DRAFT;
    }

    private void applySaveOperationResult(SaveOperationResult result, Consumer<SaveOutcome> onComplete) {
        saving.set(false);
        if (isDisposed()) {
            completeSave(onComplete, SaveOutcome.FAILED);
            return;
        }

        if (!result.success()) {
            notifyError(SAVE_PROFILE_ERROR, result.error());
            completeSave(onComplete, SaveOutcome.FAILED);
            return;
        }

        updateSessionAndCompletion(result.savedUser());
        logInfo("Profile saved successfully");
        completeSave(onComplete, result.outcome());
    }

    private void completeSave(Consumer<SaveOutcome> onComplete, SaveOutcome outcome) {
        if (onComplete != null) {
            onComplete.accept(outcome);
        }
    }

    private void updateSessionAndCompletion(User user) {
        session.setCurrentUser(user);
        CompletionResult completion = updateCompletion(user);
        updateOnboardingState(user, completion);
        markCurrentStateSaved();
    }

    public javafx.beans.property.BooleanProperty hasUnsavedChangesProperty() {
        return hasUnsavedChanges;
    }

    public void refreshUnsavedChangesFlag() {
        hasUnsavedChanges.set(!createStateSnapshot().equals(lastSavedSnapshot));
    }

    public void markCurrentStateSaved() {
        lastSavedSnapshot = createStateSnapshot();
        hasUnsavedChanges.set(false);
    }

    /**
     * Returns configured minimum height in cm (for controllers to use instead of
     * AppConfig.defaults()).
     */
    public int getMinHeightCm() {
        return config.validation().minHeightCm();
    }

    /**
     * Returns configured maximum height in cm (for controllers to use instead of
     * AppConfig.defaults()).
     */
    public int getMaxHeightCm() {
        return config.validation().maxHeightCm();
    }

    /** Returns the configured maximum number of interests allowed. */
    public int getMaxInterests() {
        return config.validation().maxInterests();
    }

    /** Returns the configured maximum bio length. */
    public int getMaxBioLength() {
        return config.validation().maxBioLength();
    }

    /** Returns a ValidationService bound to this view model's runtime config. */
    public ValidationService getValidationService() {
        return validationService;
    }

    // --- Properties for data binding ---

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty bioProperty() {
        return bio;
    }

    public StringProperty locationProperty() {
        return locationDisplay;
    }

    public StringProperty locationDisplayProperty() {
        return locationDisplay;
    }

    public StringProperty interestsProperty() {
        return interests;
    }

    public StringProperty completionStatusProperty() {
        return completionStatus;
    }

    public StringProperty completionDetailsProperty() {
        return completionDetails;
    }

    /**
     * Returns the primary photo URL property for binding.
     *
     * @return the primary photo URL property
     */
    public StringProperty primaryPhotoUrlProperty() {
        return primaryPhotoUrl;
    }

    public javafx.collections.ObservableList<String> getPhotoUrls() {
        return photoUrls;
    }

    public IntegerProperty photoCountProperty() {
        return photoCount;
    }

    public javafx.beans.property.BooleanProperty onboardingActiveProperty() {
        return onboardingActive;
    }

    public StringProperty onboardingHeadlineProperty() {
        return onboardingHeadline;
    }

    public StringProperty onboardingSummaryProperty() {
        return onboardingSummary;
    }

    public StringProperty primaryActionLabelProperty() {
        return primaryActionLabel;
    }

    public javafx.collections.ObservableList<String> onboardingChecklistProperty() {
        return readOnlyOnboardingChecklist;
    }

    public int getMaxPhotos() {
        return LocalPhotoStore.MAX_PHOTOS;
    }

    public javafx.beans.property.BooleanProperty savingProperty() {
        return saving;
    }

    public IntegerProperty currentPhotoIndexProperty() {
        return currentPhotoIndex;
    }

    public void showNextPhoto() {
        photoCarousel.showNextPhoto();
        syncPhotoCarousel();
    }

    public void showPreviousPhoto() {
        photoCarousel.showPreviousPhoto();
        syncPhotoCarousel();
    }

    private void syncPhotoCarousel() {
        photoUrls.setAll(photoCarousel.photoUrls());
        currentPhotoIndex.set(photoCarousel.currentPhotoIndex());
        photoCount.set(photoCarousel.photoCount());
        String url = photoCarousel.currentPhotoUrl();
        if (url == null || url.isBlank() || PLACEHOLDER_PHOTO_URL.equals(url)) {
            primaryPhotoUrl.set("");
        } else {
            primaryPhotoUrl.set(url);
        }
    }

    // --- Gender and preferences properties ---

    public ObjectProperty<Gender> genderProperty() {
        return gender;
    }

    public ObservableSet<Gender> getInterestedInGenders() {
        return interestedInGenders;
    }

    public ObjectProperty<LocalDate> birthDateProperty() {
        return birthDate;
    }

    public double getLatitude() {
        return latitude.get();
    }

    public double getLongitude() {
        return longitude.get();
    }

    public boolean hasLocationSet() {
        return hasLocation.get();
    }

    public void setLocationCoordinates(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        applyLocationDisplay(latitude, longitude, locationService.formatForDisplay(latitude, longitude));
    }

    public void setResolvedLocation(ResolvedLocation resolvedLocation) {
        Objects.requireNonNull(resolvedLocation, "resolvedLocation cannot be null");
        validateCoordinates(resolvedLocation.latitude(), resolvedLocation.longitude());
        applyLocationDisplay(resolvedLocation.latitude(), resolvedLocation.longitude(), resolvedLocation.label());
    }

    public void clearLocation() {
        latitude.set(Double.NaN);
        longitude.set(Double.NaN);
        hasLocation.set(false);
        updateLocationDisplay();
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    private void applyLocationDisplay(double latitude, double longitude, String displayLabel) {
        this.latitude.set(latitude);
        this.longitude.set(longitude);
        hasLocation.set(true);
        locationDisplay.set(
                displayLabel == null || displayLabel.isBlank() ? formatLocation(latitude, longitude) : displayLabel);
    }

    public LocationService getLocationService() {
        return locationService;
    }

    /**
     * Toggles a gender preference.
     *
     * @param g the gender to toggle
     * @return true if the gender is now selected, false if removed
     */
    public boolean toggleInterestedIn(Gender g) {
        if (interestedInGenders.contains(g)) {
            interestedInGenders.remove(g);
            return false;
        } else {
            interestedInGenders.add(g);
            return true;
        }
    }

    // --- Lifestyle properties for data binding ---

    public ObjectProperty<Integer> heightProperty() {
        return height;
    }

    public ObjectProperty<Lifestyle.Smoking> smokingProperty() {
        return smoking;
    }

    public ObjectProperty<Lifestyle.Drinking> drinkingProperty() {
        return drinking;
    }

    public ObjectProperty<Lifestyle.WantsKids> wantsKidsProperty() {
        return wantsKids;
    }

    public ObjectProperty<Lifestyle.LookingFor> lookingForProperty() {
        return lookingFor;
    }

    public ObjectProperty<PacePreferences.MessagingFrequency> messagingFrequencyProperty() {
        return messagingFrequency;
    }

    public ObjectProperty<PacePreferences.TimeToFirstDate> timeToFirstDateProperty() {
        return timeToFirstDate;
    }

    public ObjectProperty<PacePreferences.CommunicationStyle> communicationStyleProperty() {
        return communicationStyle;
    }

    public ObjectProperty<PacePreferences.DepthPreference> depthPreferenceProperty() {
        return depthPreference;
    }

    // --- Search preferences properties ---

    public StringProperty minAgeProperty() {
        return minAge;
    }

    public StringProperty maxAgeProperty() {
        return maxAge;
    }

    public StringProperty maxDistanceProperty() {
        return maxDistance;
    }

    // --- Dealbreakers properties ---

    public ObjectProperty<Dealbreakers> dealbreakersProperty() {
        return dealbreakers;
    }

    public StringProperty dealbreakersStatusProperty() {
        return dealbreakersStatus;
    }

    /**
     * Returns the current dealbreakers.
     *
     * @return current Dealbreakers instance
     */
    public Dealbreakers getDealbreakers() {
        return dealbreakers.get();
    }

    /**
     * Updates the dealbreakers and refreshes the status display.
     *
     * @param newDealbreakers the new Dealbreakers to set
     */
    public void setDealbreakers(Dealbreakers newDealbreakers) {
        dealbreakers.set(newDealbreakers != null ? newDealbreakers : Dealbreakers.none());
        updateDealbreakersStatus(dealbreakers.get());
    }

    // --- Interests management ---

    public ObservableSet<Interest> getSelectedInterests() {
        return selectedInterests;
    }

    /**
     * Toggles an interest selection.
     *
     * @param interest the interest to toggle
     * @return true if the interest is now selected, false if removed
     */
    public boolean toggleInterest(Interest interest) {
        if (selectedInterests.contains(interest)) {
            selectedInterests.remove(interest);
            updateInterestsDisplay();
            return false;
        } else {
            int maxInterests = getMaxInterests();
            if (selectedInterests.size() < maxInterests) {
                selectedInterests.add(interest);
                updateInterestsDisplay();
                return true;
            } else {
                UiFeedbackService.showWarning("Maximum " + maxInterests + " interests allowed");
                return false;
            }
        }
    }

    /**
     * Updates the interests display string from selected interests.
     */
    private void updateInterestsDisplay() {
        if (selectedInterests.isEmpty()) {
            interests.set("");
        } else {
            String interestStr = selectedInterests.stream()
                    .map(Interest::getDisplayName)
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            interests.set(interestStr);
        }
    }

    /**
     * Saves a profile photo to app data directory and updates user record.
     * Runs on a virtual thread to avoid blocking the UI.
     *
     * @param photoFile the selected photo file
     */
    public void savePhoto(File photoFile) {
        savePhotoInternal(photoFile, null);
    }

    public void replacePhoto(int index, File photoFile) {
        savePhotoInternal(photoFile, index);
    }

    private void savePhotoInternal(File photoFile, Integer replaceIndex) {
        if (isDisposed()) {
            return;
        }
        User user = session.getCurrentUser();
        if (user == null) {
            logWarn("No current user for photo save");
            return;
        }
        if (photoFile == null || !photoFile.isFile()) {
            logWarn("Invalid photo file selected: {}", photoFile);
            UiFeedbackService.showError("Invalid photo file selected");
            return;
        }

        if (photoFile.length() > MAX_PHOTO_SIZE_BYTES) {
            UiFeedbackService.showError("Photo must be 5 MB or smaller");
            return;
        }

        if (replaceIndex != null) {
            asyncScope.runFireAndForget("photo-replace", () -> processPhotoReplacement(user, replaceIndex, photoFile));
            return;
        }

        asyncScope.runFireAndForget("photo-save", () -> processPhotoUpload(user, photoFile));
    }

    public void deletePhoto(int index) {
        if (isDisposed()) {
            return;
        }
        User user = session.getCurrentUser();
        if (user == null) {
            logWarn("No current user for photo delete");
            return;
        }

        asyncScope.runFireAndForget("photo-delete", () -> processPhotoDeletion(user, index));
    }

    public void setPrimaryPhoto(int index) {
        if (isDisposed()) {
            return;
        }
        User user = session.getCurrentUser();
        if (user == null) {
            logWarn("No current user for primary photo update");
            return;
        }

        asyncScope.runFireAndForget("photo-set-primary", () -> processSetPrimaryPhoto(user, index));
    }

    private void processPhotoUpload(User user, File photoFile) {
        try {
            if (isDisposed()) {
                return;
            }
            PhotoMutationCoordinator.PhotoMutationResult result = photoMutationCoordinator.savePhoto(user, photoFile);
            asyncScope.dispatchToUi(() -> applyPhotoMutationResult(user, result));
        } catch (IOException e) {
            logError(SAVE_PHOTO_ERROR, e);
            notifyError(SAVE_PHOTO_ERROR, e);
        } catch (IllegalArgumentException e) {
            logWarn("Invalid photo save request: {}", e.getMessage());
            notifyError(SAVE_PHOTO_ERROR, e);
        }
    }

    private void processPhotoReplacement(User user, int index, File photoFile) {
        try {
            if (isDisposed()) {
                return;
            }
            PhotoMutationCoordinator.PhotoMutationResult result =
                    photoMutationCoordinator.replacePhoto(user, index, photoFile);
            asyncScope.dispatchToUi(() -> applyPhotoMutationResult(user, result));
        } catch (IOException e) {
            logError(REPLACE_PHOTO_ERROR, e);
            notifyError(REPLACE_PHOTO_ERROR, e);
        } catch (IllegalArgumentException e) {
            logWarn("Invalid photo replacement request: {}", e.getMessage());
            notifyError(REPLACE_PHOTO_ERROR, e);
        }
    }

    private void processPhotoDeletion(User user, int index) {
        try {
            if (isDisposed()) {
                return;
            }
            PhotoMutationCoordinator.PhotoMutationResult result = photoMutationCoordinator.deletePhoto(user, index);
            asyncScope.dispatchToUi(() -> applyPhotoMutationResult(user, result));
        } catch (IOException e) {
            logError(DELETE_PHOTO_ERROR, e);
            notifyError(DELETE_PHOTO_ERROR, e);
        } catch (IllegalArgumentException e) {
            logWarn("Invalid photo delete request: {}", e.getMessage());
            notifyError(DELETE_PHOTO_ERROR, e);
        }
    }

    private void processSetPrimaryPhoto(User user, int index) {
        try {
            if (isDisposed()) {
                return;
            }
            PhotoMutationCoordinator.PhotoMutationResult result = photoMutationCoordinator.setPrimaryPhoto(user, index);
            asyncScope.dispatchToUi(() -> applyPhotoMutationResult(user, result));
        } catch (IllegalArgumentException e) {
            logWarn("Invalid primary photo request: {}", e.getMessage());
            notifyError(UPDATE_PRIMARY_PHOTO_ERROR, e);
        }
    }

    private void applyPhotoMutationResult(User user, PhotoMutationCoordinator.PhotoMutationResult result) {
        if (isDisposed()) {
            return;
        }
        photoUrls.setAll(result.photoUrls());
        photoCarousel.setPhotos(result.photoUrls());
        for (int i = 0; i < Math.max(0, result.selectedIndex()); i++) {
            photoCarousel.showNextPhoto();
        }
        syncPhotoCarousel();
        session.setCurrentUser(user);
        CompletionResult completion = updateCompletion(user);
        updateOnboardingState(user, completion);
        markCurrentStateSaved();
        UiFeedbackService.showSuccess(result.successMessage());
    }

    private String buildCompletionDetails(CompletionResult result) {
        if (result == null) {
            return "";
        }
        List<String> missing = result.breakdown().stream()
                .flatMap(breakdown -> breakdown.missingItems().stream())
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
        if (missing.isEmpty()) {
            return "All sections complete";
        }
        int limit = 3;
        String summary =
                missing.stream().limit(limit).reduce((a, b) -> a + ", " + b).orElse("");
        if (missing.size() > limit) {
            summary += " +" + (missing.size() - limit) + " more";
        }
        return "Missing: " + summary;
    }

    public ProfilePreviewSnapshot buildPreviewSnapshot() {
        User draftUser = createDraftUser();
        return new ProfilePreviewSnapshot(
                draftUser.getName(),
                draftUser.getAge(config.safety().userTimeZone()).orElse(0),
                draftUser.getBio() == null || draftUser.getBio().isBlank()
                        ? "No bio provided yet."
                        : draftUser.getBio(),
                formatLocationLabel(draftUser),
                draftUser.getInterests().stream()
                        .map(Interest::getDisplayName)
                        .sorted()
                        .toList(),
                draftUser.getLookingFor() != null
                        ? draftUser.getLookingFor().getDisplayName()
                        : "Open to meeting people",
                List.copyOf(draftUser.getPhotoUrls()),
                completionStatus.get());
    }

    public ProfileService.CompletionResult calculateCurrentCompletion() {
        User draftUser = createDraftUser();
        if (profileUseCases != null) {
            var result = profileUseCases.calculateCompletion(draftUser);
            if (!result.success()) {
                throw new IllegalStateException(result.error().message());
            }
            return result.data();
        }
        return profileCompletionService.calculate(draftUser);
    }

    private User createDraftUser() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("No current user available for preview.");
        }
        return draftAssembler.assemble(currentUser, createDraftState(), UiFeedbackService::showWarning);
    }

    private ProfileDraftAssembler.DraftState createDraftState() {
        return new ProfileDraftAssembler.DraftState(
                bio.get(),
                birthDate.get(),
                gender.get(),
                interestedInGenders,
                latitude.get(),
                longitude.get(),
                hasLocation.get(),
                selectedInterests,
                height.get(),
                smoking.get(),
                drinking.get(),
                wantsKids.get(),
                lookingFor.get(),
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                dealbreakers.get(),
                messagingFrequency.get(),
                timeToFirstDate.get(),
                communicationStyle.get(),
                depthPreference.get());
    }

    private String createStateSnapshot() {
        return String.join(
                "|",
                normalize(name.get()),
                normalize(bio.get()),
                normalize(locationDisplay.get()),
                String.valueOf(gender.get()),
                String.valueOf(birthDate.get()),
                String.valueOf(height.get()),
                String.valueOf(smoking.get()),
                String.valueOf(drinking.get()),
                String.valueOf(wantsKids.get()),
                String.valueOf(lookingFor.get()),
                normalize(minAge.get()),
                normalize(maxAge.get()),
                normalize(maxDistance.get()),
                String.valueOf(dealbreakers.get()),
                String.valueOf(messagingFrequency.get()),
                String.valueOf(timeToFirstDate.get()),
                String.valueOf(communicationStyle.get()),
                String.valueOf(depthPreference.get()),
                photoUrls.toString(),
                selectedInterests.stream().sorted().map(Enum::name).toList().toString(),
                interestedInGenders.stream().sorted().map(Enum::name).toList().toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private String formatLocationLabel(User user) {
        if (user == null || !user.hasLocation()) {
            return "Location not set";
        }
        return locationService.formatForDisplay(user.getLat(), user.getLon());
    }

    public record ProfilePreviewSnapshot(
            String name,
            int age,
            String bio,
            String location,
            List<String> interests,
            String lookingFor,
            List<String> photoUrls,
            String completionText) {
        public ProfilePreviewSnapshot {
            interests = interests != null ? List.copyOf(interests) : List.of();
            photoUrls = photoUrls != null ? List.copyOf(photoUrls) : List.of();
        }
    }

    public enum SaveOutcome {
        FAILED,
        SAVED_DRAFT,
        ACTIVATED
    }

    public record Dependencies(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileMutationUseCases profileMutationUseCases,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            ValidationService validationService,
            LocationService locationService,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {}

    private static Dependencies createDependencies(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {
        ValidationService validationService = new ValidationService(config);
        return new Dependencies(
                userStore,
                profileCompletionService,
                profileUseCases != null ? profileUseCases.getProfileMutationUseCases() : null,
                profileUseCases,
                config,
                session,
                validationService,
                new LocationService(validationService),
                uiDispatcher,
                activationPolicy);
    }

    private record SaveOperationResult(boolean success, User savedUser, SaveOutcome outcome, Exception error) {
        private static SaveOperationResult success(User savedUser, SaveOutcome outcome) {
            return new SaveOperationResult(true, savedUser, outcome, null);
        }

        private static SaveOperationResult failure(Exception error) {
            return new SaveOperationResult(false, null, SaveOutcome.FAILED, error);
        }
    }
}
