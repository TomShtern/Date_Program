package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.SaveProfileCommand;
import datingapp.core.AppClock;
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
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.core.workflow.ProfileActivationPolicy.ActivationResult;
import datingapp.ui.LocalPhotoStore;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Profile Editor screen.
 * Handles editing of user bio, location, interests, lifestyle, and profile
 * photo.
 */
public class ProfileViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ProfileViewModel.class);
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

    private final UiUserStore userStore;
    private final ProfileService profileCompletionService;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final ProfileActivationPolicy activationPolicy;
    private final LocationService locationService;
    private final ViewModelAsyncScope asyncScope;
    private final LocalPhotoStore photoStore;

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
    private final BooleanProperty hasLocation = new SimpleBooleanProperty(false);
    private final IntegerProperty photoCount = new SimpleIntegerProperty(0);
    private final BooleanProperty saving = new SimpleBooleanProperty(false);

    // Multiple photos
    private final javafx.collections.ObservableList<String> photoUrls = FXCollections.observableArrayList();
    private final IntegerProperty currentPhotoIndex = new SimpleIntegerProperty(0);

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
    private final BooleanProperty hasUnsavedChanges = new SimpleBooleanProperty(false);

    // Selected interests (observable set for multi-selection)
    private final ObservableSet<Interest> selectedInterests =
            FXCollections.observableSet(EnumSet.noneOf(Interest.class));

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private final Object photoMutationLock = new Object();

    private String lastSavedSnapshot = "";

    private ViewModelErrorSink errorHandler;

    public ProfileViewModel(
            UiUserStore userStore, ProfileService profileCompletionService, AppConfig config, AppSession session) {
        this(new Dependencies(
                userStore,
                profileCompletionService,
                null,
                config,
                session,
                new LocationService(new datingapp.core.profile.ValidationService(config)),
                new JavaFxUiThreadDispatcher(),
                new ProfileActivationPolicy()));
    }

    public ProfileViewModel(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session) {
        this(new Dependencies(
                userStore,
                profileCompletionService,
                profileUseCases,
                config,
                session,
                new LocationService(new datingapp.core.profile.ValidationService(config)),
                new JavaFxUiThreadDispatcher(),
                new ProfileActivationPolicy()));
    }

    public ProfileViewModel(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this(new Dependencies(
                userStore,
                profileCompletionService,
                profileUseCases,
                config,
                session,
                new LocationService(new datingapp.core.profile.ValidationService(config)),
                uiDispatcher,
                new ProfileActivationPolicy()));
    }

    public ProfileViewModel(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {
        this(new Dependencies(
                userStore,
                profileCompletionService,
                profileUseCases,
                config,
                session,
                new LocationService(new datingapp.core.profile.ValidationService(config)),
                uiDispatcher,
                activationPolicy));
    }

    public ProfileViewModel(Dependencies dependencies) {
        this.userStore = Objects.requireNonNull(dependencies.userStore(), "userStore cannot be null");
        this.profileCompletionService = Objects.requireNonNull(
                dependencies.profileCompletionService(), "profileCompletionService cannot be null");
        this.profileUseCases = dependencies.profileUseCases();
        this.config = Objects.requireNonNull(dependencies.config(), "config cannot be null");
        this.session = Objects.requireNonNull(dependencies.session(), "session cannot be null");
        this.locationService = Objects.requireNonNull(dependencies.locationService(), "locationService cannot be null");
        this.activationPolicy =
                Objects.requireNonNull(dependencies.activationPolicy(), "activationPolicy cannot be null");
        this.asyncScope = createAsyncScope(dependencies.uiDispatcher());
        this.photoStore = new LocalPhotoStore();
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        asyncScope.dispose();
        selectedInterests.clear();
        photoUrls.clear();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
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
        updateCompletion(user);
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

    private void updateCompletion(User user) {
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
        } catch (Exception e) {
            logError("Failed to calculate profile completion", e);
            notifyError("Failed to calculate profile completion", e);
            completionStatus.set("--");
            completionDetails.set("Unable to calculate completion");
        }
    }

    private void updatePrimaryPhoto(User user) {
        if (user == null) {
            primaryPhotoUrl.set("");
            photoUrls.clear();
            currentPhotoIndex.set(0);
            photoCount.set(0);
            return;
        }
        List<String> urls = user.getPhotoUrls();
        photoUrls.setAll(urls != null ? urls : List.of());
        currentPhotoIndex.set(0);
        photoCount.set(photoUrls.size());

        if (urls == null || urls.isEmpty()) {
            primaryPhotoUrl.set("");
            return;
        }
        String first = urls.getFirst();
        if (first == null || first.isBlank() || PLACEHOLDER_PHOTO_URL.equals(first)) {
            primaryPhotoUrl.set("");
        } else {
            primaryPhotoUrl.set(first);
        }
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
            locationDisplay.set(formatLocation(latitude.get(), longitude.get()));
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

    public void saveAsync(Consumer<Boolean> onComplete) {
        if (disposed.get()) {
            logWarn("ProfileViewModel is disposed; skipping save");
            completeSave(onComplete, false);
            return;
        }
        if (saving.get()) {
            logWarn("Profile save already in progress");
            return;
        }

        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logWarn("No current user to save");
            completeSave(onComplete, false);
            return;
        }

        User draftUser;
        try {
            draftUser = createDraftUser();
        } catch (Exception e) {
            logError("Failed to prepare profile draft", e);
            notifyError(SAVE_PROFILE_ERROR, e);
            completeSave(onComplete, false);
            return;
        }

        logInfo("Saving profile for user: {}", currentUser.getName());
        saving.set(true);
        asyncScope.runFireAndForget("profile-save", () -> persistProfileInBackground(draftUser, onComplete));
    }

    private void persistProfileInBackground(User draftUser, Consumer<Boolean> onComplete) {
        SaveOperationResult result;
        try {
            if (disposed.get()) {
                result = SaveOperationResult.failure(new IllegalStateException(PROFILE_EDITOR_INACTIVE));
            } else if (profileUseCases != null) {
                result = persistProfileViaUseCase(draftUser);
            } else {
                result = persistProfileLegacy(draftUser);
            }
        } catch (Exception e) {
            logError("Failed to save profile: {}", e.getMessage(), e);
            result = SaveOperationResult.failure(e);
        }

        SaveOperationResult completedResult = result;
        asyncScope.dispatchToUi(() -> applySaveOperationResult(completedResult, onComplete));
    }

    private SaveOperationResult persistProfileViaUseCase(User user) {
        var saveResult = profileUseCases.saveProfile(new SaveProfileCommand(UserContext.ui(user.getId()), user));
        if (!saveResult.success()) {
            return SaveOperationResult.failure(
                    new IllegalStateException(saveResult.error().message()));
        }

        User savedUser = saveResult.data().user();
        return SaveOperationResult.success(savedUser);
    }

    private SaveOperationResult persistProfileLegacy(User user) {
        persistUser(user);
        if (disposed.get()) {
            return SaveOperationResult.failure(new IllegalStateException(PROFILE_EDITOR_INACTIVE));
        }

        attemptActivation(user);
        if (disposed.get()) {
            return SaveOperationResult.failure(new IllegalStateException(PROFILE_EDITOR_INACTIVE));
        }

        return SaveOperationResult.success(user);
    }

    private void applySaveOperationResult(SaveOperationResult result, Consumer<Boolean> onComplete) {
        saving.set(false);
        if (disposed.get()) {
            completeSave(onComplete, false);
            return;
        }

        if (!result.success()) {
            notifyError(SAVE_PROFILE_ERROR, result.error());
            completeSave(onComplete, false);
            return;
        }

        updateSessionAndCompletion(result.savedUser());
        logInfo("Profile saved successfully");
        completeSave(onComplete, true);
    }

    private void completeSave(Consumer<Boolean> onComplete, boolean success) {
        if (onComplete != null) {
            onComplete.accept(success);
        }
    }

    private void applyBasicFields(User user) {
        user.setBio(bio.get());

        if (gender.get() != null) {
            user.setGender(gender.get());
        }
        if (!interestedInGenders.isEmpty()) {
            user.setInterestedIn(EnumSet.copyOf(interestedInGenders));
        }
        applyBirthDate(user, true);
    }

    private void applyLocation(User user) {
        if (!hasLocation.get()) {
            return;
        }
        user.setLocation(latitude.get(), longitude.get());
    }

    private void applyInterests(User user) {
        if (!selectedInterests.isEmpty()) {
            user.setInterests(EnumSet.copyOf(selectedInterests));
        }
    }

    private void applyLifestyleFields(User user) {
        if (height.get() != null) {
            user.setHeightCm(height.get());
        }
        if (smoking.get() != null) {
            user.setSmoking(smoking.get());
        }
        if (drinking.get() != null) {
            user.setDrinking(drinking.get());
        }
        if (wantsKids.get() != null) {
            user.setWantsKids(wantsKids.get());
        }
        if (lookingFor.get() != null) {
            user.setLookingFor(lookingFor.get());
        }
    }

    private void applySearchPreferences(User user, boolean notifyUser) {
        applyAgeRangePreference(user, notifyUser);
        applyMaxDistancePreference(user, notifyUser);
    }

    private void applyAgeRangePreference(User user, boolean notifyUser) {
        try {
            int min = Integer.parseInt(minAge.get());
            int max = Integer.parseInt(maxAge.get());
            if (min >= config.validation().minAge()
                    && max <= config.validation().maxAge()
                    && min <= max) {
                user.setAgeRange(
                        min,
                        max,
                        config.validation().minAge(),
                        config.validation().maxAge());
            } else {
                logWarn("Invalid age range values: {}-{}", min, max);
                if (notifyUser) {
                    UiFeedbackService.showWarning("Please enter valid ages");
                }
            }
        } catch (NumberFormatException _) {
            logWarn("Invalid age range values");
            if (notifyUser) {
                UiFeedbackService.showWarning("Please enter valid ages");
            }
        }
    }

    private void applyMaxDistancePreference(User user, boolean notifyUser) {
        try {
            int dist = Integer.parseInt(maxDistance.get());
            if (dist > 0 && dist <= config.matching().maxDistanceKm()) {
                user.setMaxDistanceKm(dist, config.matching().maxDistanceKm());
            } else {
                logWarn("Invalid max distance value: {}", dist);
                if (notifyUser) {
                    UiFeedbackService.showWarning("Please enter a valid distance");
                }
            }
        } catch (NumberFormatException _) {
            logWarn("Invalid max distance value");
            if (notifyUser) {
                UiFeedbackService.showWarning("Please enter a valid distance");
            }
        }
    }

    private void applyDealbreakers(User user) {
        if (dealbreakers.get() != null) {
            user.setDealbreakers(dealbreakers.get());
        }
    }

    private void persistUser(User user) {
        userStore.save(user);
    }

    private void attemptActivation(User user) {
        ActivationResult activation = activationPolicy.tryActivate(user);
        if (activation.activated()) {
            userStore.save(user);
            logInfo("User {} activated after profile completion", user.getName());
        }
    }

    private void updateSessionAndCompletion(User user) {
        session.setCurrentUser(user);
        updateCompletion(user);
        markCurrentStateSaved();
    }

    public BooleanProperty hasUnsavedChangesProperty() {
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

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void logError(String message, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(message, args);
        }
    }

    private void notifyError(String userMessage, Exception e) {
        if (errorHandler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        asyncScope.dispatchToUi(() -> errorHandler.onError(message));
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

    public int getMaxPhotos() {
        return LocalPhotoStore.MAX_PHOTOS;
    }

    public BooleanProperty savingProperty() {
        return saving;
    }

    public IntegerProperty currentPhotoIndexProperty() {
        return currentPhotoIndex;
    }

    public void showNextPhoto() {
        if (photoUrls.isEmpty()) {
            return;
        }
        int nextIndex = (currentPhotoIndex.get() + 1) % photoUrls.size();
        currentPhotoIndex.set(nextIndex);
        updatePrimaryPhotoFromIndex();
    }

    public void showPreviousPhoto() {
        if (photoUrls.isEmpty()) {
            return;
        }
        int prevIndex = currentPhotoIndex.get() - 1;
        if (prevIndex < 0) {
            prevIndex = photoUrls.size() - 1;
        }
        currentPhotoIndex.set(prevIndex);
        updatePrimaryPhotoFromIndex();
    }

    private void updatePrimaryPhotoFromIndex() {
        if (photoUrls.isEmpty()) {
            primaryPhotoUrl.set("");
            return;
        }
        int index = currentPhotoIndex.get();
        if (index >= 0 && index < photoUrls.size()) {
            String url = photoUrls.get(index);
            if (url == null || url.isBlank() || PLACEHOLDER_PHOTO_URL.equals(url)) {
                primaryPhotoUrl.set("");
            } else {
                primaryPhotoUrl.set(url);
            }
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
            if (selectedInterests.size() < Interest.MAX_PER_USER) {
                selectedInterests.add(interest);
                updateInterestsDisplay();
                return true;
            } else {
                UiFeedbackService.showWarning("Maximum " + Interest.MAX_PER_USER + " interests allowed");
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
        if (disposed.get()) {
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
        if (disposed.get()) {
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
        if (disposed.get()) {
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
        synchronized (photoMutationLock) {
            try {
                if (disposed.get()) {
                    return;
                }
                List<String> updatedPhotoUrls =
                        photoStore.importPhoto(user.getId(), user.getPhotoUrls(), photoFile.toPath());
                persistPhotoUrls(user, updatedPhotoUrls, updatedPhotoUrls.size() - 1, "Photo saved!");

            } catch (IOException e) {
                logError(SAVE_PHOTO_ERROR, e);
                notifyError(SAVE_PHOTO_ERROR, e);
            } catch (IllegalArgumentException e) {
                logWarn("Invalid photo save request: {}", e.getMessage());
                notifyError(SAVE_PHOTO_ERROR, e);
            }
        }
    }

    private void processPhotoReplacement(User user, int index, File photoFile) {
        synchronized (photoMutationLock) {
            try {
                if (disposed.get()) {
                    return;
                }
                List<String> updatedPhotoUrls =
                        photoStore.replacePhoto(user.getId(), user.getPhotoUrls(), index, photoFile.toPath());
                persistPhotoUrls(user, updatedPhotoUrls, index, "Photo replaced.");
            } catch (IOException e) {
                logError(REPLACE_PHOTO_ERROR, e);
                notifyError(REPLACE_PHOTO_ERROR, e);
            } catch (IllegalArgumentException e) {
                logWarn("Invalid photo replacement request: {}", e.getMessage());
                notifyError(REPLACE_PHOTO_ERROR, e);
            }
        }
    }

    private void processPhotoDeletion(User user, int index) {
        synchronized (photoMutationLock) {
            try {
                if (disposed.get()) {
                    return;
                }
                List<String> updatedPhotoUrls = photoStore.deletePhoto(user.getPhotoUrls(), index);
                int selectedIndex = updatedPhotoUrls.isEmpty() ? 0 : Math.min(index, updatedPhotoUrls.size() - 1);
                persistPhotoUrls(user, updatedPhotoUrls, selectedIndex, "Photo removed.");
            } catch (IOException e) {
                logError(DELETE_PHOTO_ERROR, e);
                notifyError(DELETE_PHOTO_ERROR, e);
            } catch (IllegalArgumentException e) {
                logWarn("Invalid photo delete request: {}", e.getMessage());
                notifyError(DELETE_PHOTO_ERROR, e);
            }
        }
    }

    private void processSetPrimaryPhoto(User user, int index) {
        synchronized (photoMutationLock) {
            try {
                if (disposed.get()) {
                    return;
                }
                List<String> updatedPhotoUrls = photoStore.setPrimaryPhoto(user.getPhotoUrls(), index);
                persistPhotoUrls(user, updatedPhotoUrls, 0, "Primary photo updated.");
            } catch (IllegalArgumentException e) {
                logWarn("Invalid primary photo request: {}", e.getMessage());
                notifyError(UPDATE_PRIMARY_PHOTO_ERROR, e);
            }
        }
    }

    private void persistPhotoUrls(User user, List<String> updatedPhotoUrls, int selectedIndex, String successMessage) {
        user.setPhotoUrls(updatedPhotoUrls);
        userStore.save(user);

        asyncScope.dispatchToUi(() -> {
            if (disposed.get()) {
                return;
            }
            photoUrls.setAll(updatedPhotoUrls);
            photoCount.set(updatedPhotoUrls.size());
            currentPhotoIndex.set(Math.max(0, selectedIndex));
            updatePrimaryPhotoFromIndex();
            session.setCurrentUser(user);
            updateCompletion(user);
            UiFeedbackService.showSuccess(successMessage);
        });
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

    private void applyBirthDate(User user, boolean notifyUser) {
        LocalDate selected = birthDate.get();
        if (selected == null) {
            return;
        }
        LocalDate today = AppClock.today();
        if (selected.isAfter(today)) {
            logWarn("Birth date cannot be in the future: {}", selected);
            if (notifyUser) {
                UiFeedbackService.showWarning("Birth date cannot be in the future");
            }
            return;
        }
        int age = Period.between(selected, today).getYears();
        if (age < config.validation().minAge() || age > config.validation().maxAge()) {
            logWarn("Birth date outside allowed age range: {}", selected);
            if (notifyUser) {
                UiFeedbackService.showWarning("Birth date must be for ages "
                        + config.validation().minAge()
                        + "-"
                        + config.validation().maxAge());
            }
            return;
        }
        user.setBirthDate(selected);
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

        User draftUser = User.StorageBuilder.create(
                        currentUser.getId(), currentUser.getName(), currentUser.getCreatedAt())
                .bio(currentUser.getBio())
                .birthDate(currentUser.getBirthDate())
                .gender(currentUser.getGender())
                .interestedIn(currentUser.getInterestedIn())
                .location(currentUser.getLat(), currentUser.getLon())
                .hasLocationSet(currentUser.hasLocationSet())
                .maxDistanceKm(currentUser.getMaxDistanceKm())
                .ageRange(currentUser.getMinAge(), currentUser.getMaxAge())
                .photoUrls(currentUser.getPhotoUrls())
                .state(currentUser.getState())
                .updatedAt(currentUser.getUpdatedAt())
                .interests(currentUser.getInterests())
                .smoking(currentUser.getSmoking())
                .drinking(currentUser.getDrinking())
                .wantsKids(currentUser.getWantsKids())
                .lookingFor(currentUser.getLookingFor())
                .education(currentUser.getEducation())
                .heightCm(currentUser.getHeightCm())
                .email(currentUser.getEmail())
                .phone(currentUser.getPhone())
                .verified(currentUser.isVerified())
                .verificationMethod(currentUser.getVerificationMethod())
                .verificationCode(currentUser.getVerificationCode())
                .verificationSentAt(currentUser.getVerificationSentAt())
                .verifiedAt(currentUser.getVerifiedAt())
                .pacePreferences(currentUser.getPacePreferences())
                .build();

        applyBasicFields(draftUser);
        applyLocation(draftUser);
        applyInterests(draftUser);
        applyLifestyleFields(draftUser);
        applySearchPreferences(draftUser, false);
        applyDealbreakers(draftUser);
        applyPacePreferences(draftUser, false);
        return draftUser;
    }

    private void applyPacePreferences(User user, boolean notifyUser) {
        boolean anySet = messagingFrequency.get() != null
                || timeToFirstDate.get() != null
                || communicationStyle.get() != null
                || depthPreference.get() != null;
        boolean allSet = messagingFrequency.get() != null
                && timeToFirstDate.get() != null
                && communicationStyle.get() != null
                && depthPreference.get() != null;

        if (!anySet) {
            user.setPacePreferences(null);
            return;
        }

        if (!allSet) {
            if (notifyUser) {
                UiFeedbackService.showWarning("Complete all pace preferences or clear them all.");
            }
            throw new IllegalArgumentException("Complete all pace preferences or clear them all.");
        }

        user.setPacePreferences(new PacePreferences(
                messagingFrequency.get(), timeToFirstDate.get(), communicationStyle.get(), depthPreference.get()));
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
        return formatLocation(user.getLat(), user.getLon());
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

    public record Dependencies(
            UiUserStore userStore,
            ProfileService profileCompletionService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            LocationService locationService,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {}

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        return new ViewModelAsyncScope(
                "profile", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorHandler));
    }

    private record SaveOperationResult(boolean success, User savedUser, Exception error) {
        private static SaveOperationResult success(User savedUser) {
            return new SaveOperationResult(true, savedUser, null);
        }

        private static SaveOperationResult failure(Exception error) {
            return new SaveOperationResult(false, null, error);
        }
    }
}
