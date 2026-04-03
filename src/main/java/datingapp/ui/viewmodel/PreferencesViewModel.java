package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases.UpdateDiscoveryPreferencesCommand;
import datingapp.app.usecase.profile.ProfileNormalizationSupport;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.ui.UiPreferencesStore.ThemeMode;
import datingapp.ui.UiThemeService;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * ViewModel for the Preferences screen.
 * Handles loading and saving user discovery preferences.
 */
public class PreferencesViewModel extends BaseViewModel {

    private final AppConfig config;
    private final AppSession session;
    private final ProfileMutationUseCases profileMutationUseCases;
    private final UiThemeService uiThemeService;
    private User currentUser;

    public static enum GenderPreference {
        MEN,
        WOMEN,
        EVERYONE
    }

    private final IntegerProperty minAge = new SimpleIntegerProperty(18);
    private final IntegerProperty maxAge = new SimpleIntegerProperty(99);
    private final IntegerProperty maxDistance = new SimpleIntegerProperty(50);
    private final ObjectProperty<GenderPreference> interestedIn = new SimpleObjectProperty<>(GenderPreference.EVERYONE);
    private final ObjectProperty<ThemeMode> themeMode = new SimpleObjectProperty<>(ThemeMode.DARK);

    public PreferencesViewModel(
            ProfileMutationUseCases profileMutationUseCases,
            UiThemeService uiThemeService,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("preferences", uiDispatcher);
        this.profileMutationUseCases =
                Objects.requireNonNull(profileMutationUseCases, "profileMutationUseCases cannot be null");
        this.uiThemeService = Objects.requireNonNull(uiThemeService, "uiThemeService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public void initialize() {
        if (isDisposed()) {
            return;
        }
        asyncScope.runLatest("preferences-load", "load preferences", this::loadSnapshot, this::applySnapshot);
    }

    private PreferencesSnapshot loadSnapshot() {
        User user = session.getCurrentUser();
        ThemeMode storedThemeMode = uiThemeService.loadThemeMode();
        if (user == null) {
            return PreferencesSnapshot.empty(storedThemeMode);
        }

        Set<Gender> interested = user.getInterestedIn() != null ? user.getInterestedIn() : Set.of();
        GenderPreference genderPreference = mapInterestedIn(interested);
        return new PreferencesSnapshot(
                user, user.getMinAge(), user.getMaxAge(), user.getMaxDistanceKm(), genderPreference, storedThemeMode);
    }

    private void applySnapshot(PreferencesSnapshot snapshot) {
        currentUser = snapshot.user();
        minAge.set(snapshot.minAgeValue());
        maxAge.set(snapshot.maxAgeValue());
        maxDistance.set(snapshot.maxDistanceValue());
        interestedIn.set(snapshot.genderPreference());
        themeMode.set(snapshot.loadedThemeMode());
        uiThemeService.setThemeMode(snapshot.loadedThemeMode());

        if (currentUser == null) {
            return;
        }

        logInfo(
                "Loaded preferences for {}: Age {}-{}, Dist {}km, Interested in {}, Theme {}",
                currentUser.getName(),
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get(),
                themeMode.get());
    }

    private GenderPreference mapInterestedIn(Set<Gender> interested) {
        if (interested.contains(Gender.MALE) && interested.contains(Gender.FEMALE)) {
            return GenderPreference.EVERYONE;
        }
        if (interested.contains(Gender.MALE)) {
            return GenderPreference.MEN;
        }
        if (interested.contains(Gender.FEMALE)) {
            return GenderPreference.WOMEN;
        }
        return GenderPreference.EVERYONE;
    }

    public void updateThemeMode(ThemeMode newThemeMode) {
        if (isDisposed()) {
            return;
        }
        ThemeMode resolvedThemeMode = newThemeMode == null ? ThemeMode.DARK : newThemeMode;
        if (themeMode.get() == resolvedThemeMode) {
            return;
        }
        themeMode.set(resolvedThemeMode);
        asyncScope.runFireAndForget("save theme preference", () -> uiThemeService.setThemeMode(resolvedThemeMode));
    }

    public void savePreferences() {
        if (isDisposed() || currentUser == null) {
            return;
        }

        logInfo(
                "Saving preferences: Age {}-{}, Dist {}km, Interested in {}, Theme {}",
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get(),
                themeMode.get());

        ProfileNormalizationSupport.DiscoveryPreferences discoveryPreferences =
                ProfileNormalizationSupport.normalizeDiscoveryPreferences(
                        config, minAge.get(), maxAge.get(), maxDistance.get());
        final int minAgeVal = discoveryPreferences.minAge();
        final int maxAgeVal = discoveryPreferences.maxAge();
        final int maxDistVal = discoveryPreferences.maxDistanceKm();

        currentUser.setAgeRange(
                minAgeVal,
                maxAgeVal,
                config.validation().minAge(),
                config.validation().maxAge());
        currentUser.setMaxDistanceKm(maxDistVal, config.matching().maxDistanceKm());

        final Set<Gender> newInterests = EnumSet.noneOf(Gender.class);
        GenderPreference preference = interestedIn.get();
        if (preference == null) {
            logWarn("Interested-in preference missing; defaulting to EVERYONE");
            preference = GenderPreference.EVERYONE;
        }

        switch (preference) {
            case MEN -> newInterests.add(Gender.MALE);
            case WOMEN -> newInterests.add(Gender.FEMALE);
            case EVERYONE -> {
                newInterests.add(Gender.MALE);
                newInterests.add(Gender.FEMALE);
                newInterests.add(Gender.OTHER);
            }
            default -> {
                logWarn("Unknown interested-in preference: {}", preference);
                newInterests.add(Gender.MALE);
                newInterests.add(Gender.FEMALE);
                newInterests.add(Gender.OTHER);
            }
        }
        currentUser.setInterestedIn(newInterests);
        final ThemeMode selectedThemeMode = themeMode.get() == null ? ThemeMode.DARK : themeMode.get();

        asyncScope.runLatest(
                "preferences-save",
                "save preferences",
                () -> {
                    persistDiscoveryPreferences(minAgeVal, maxAgeVal, maxDistVal, newInterests);
                    uiThemeService.setThemeMode(selectedThemeMode);
                    return selectedThemeMode;
                },
                themeMode::set);
    }

    private ThemeMode persistDiscoveryPreferences(
            int minAgeVal, int maxAgeVal, int maxDistVal, Set<Gender> newInterests) {
        UseCaseResult<User> result =
                profileMutationUseCases.updateDiscoveryPreferences(new UpdateDiscoveryPreferencesCommand(
                        UserContext.ui(currentUser.getId()), minAgeVal, maxAgeVal, maxDistVal, newInterests));
        if (!result.success()) {
            logWarn(
                    "Failed to save preferences via use-case: {}",
                    result.error().message());
        }
        return themeMode.get() == null ? ThemeMode.DARK : themeMode.get();
    }

    public IntegerProperty minAgeProperty() {
        return minAge;
    }

    public IntegerProperty maxAgeProperty() {
        return maxAge;
    }

    public IntegerProperty maxDistanceProperty() {
        return maxDistance;
    }

    public ObjectProperty<GenderPreference> interestedInProperty() {
        return interestedIn;
    }

    public ObjectProperty<ThemeMode> themeModeProperty() {
        return themeMode;
    }

    private record PreferencesSnapshot(
            User user,
            int minAgeValue,
            int maxAgeValue,
            int maxDistanceValue,
            GenderPreference genderPreference,
            ThemeMode loadedThemeMode) {
        private static PreferencesSnapshot empty(ThemeMode themeMode) {
            return new PreferencesSnapshot(null, 18, 99, 50, GenderPreference.EVERYONE, themeMode);
        }
    }
}
