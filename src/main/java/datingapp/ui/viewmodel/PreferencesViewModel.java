package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.UpdateDiscoveryPreferencesCommand;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.ui.NavigationService;
import datingapp.ui.UiPreferencesStore;
import datingapp.ui.UiPreferencesStore.ThemeMode;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Preferences screen.
 * Handles loading and saving user discovery MatchPreferences.
 */
public class PreferencesViewModel {
    private static final Logger logger = LoggerFactory.getLogger(PreferencesViewModel.class);

    private final AppConfig config;
    private final AppSession session;
    private final UiUserStore userStore;
    private final ProfileUseCases profileUseCases;
    private final UiPreferencesStore uiPreferencesStore;
    private final ViewModelAsyncScope asyncScope;
    private User currentUser;

    // UI-specific enum for single-selection preference
    public static enum GenderPreference {
        MEN,
        WOMEN,
        EVERYONE
    }

    // Properties bound to UI
    private final IntegerProperty minAge = new SimpleIntegerProperty(18);
    private final IntegerProperty maxAge = new SimpleIntegerProperty(99);
    private final IntegerProperty maxDistance = new SimpleIntegerProperty(50);
    private final ObjectProperty<GenderPreference> interestedIn = new SimpleObjectProperty<>(GenderPreference.EVERYONE);
    private final ObjectProperty<ThemeMode> themeMode = new SimpleObjectProperty<>(ThemeMode.DARK);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public PreferencesViewModel(UiUserStore userStore, AppConfig config, AppSession session) {
        this(userStore, null, new UiPreferencesStore(), config, session, new JavaFxUiThreadDispatcher());
    }

    public PreferencesViewModel(
            UiUserStore userStore, ProfileUseCases profileUseCases, AppConfig config, AppSession session) {
        this(userStore, profileUseCases, new UiPreferencesStore(), config, session, new JavaFxUiThreadDispatcher());
    }

    public PreferencesViewModel(
            UiUserStore userStore,
            ProfileUseCases profileUseCases,
            UiPreferencesStore uiPreferencesStore,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.profileUseCases = profileUseCases;
        this.uiPreferencesStore = Objects.requireNonNull(uiPreferencesStore, "uiPreferencesStore cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);
    }

    public void initialize() {
        if (disposed.get()) {
            return;
        }
        asyncScope.runLatest("preferences-load", "load preferences", this::loadSnapshot, this::applySnapshot);
    }

    private PreferencesSnapshot loadSnapshot() {
        User user = session.getCurrentUser();
        ThemeMode storedThemeMode = uiPreferencesStore.loadThemeMode();
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
        minAge.set(snapshot.minAge());
        maxAge.set(snapshot.maxAge());
        maxDistance.set(snapshot.maxDistance());
        interestedIn.set(snapshot.genderPreference());
        themeMode.set(snapshot.themeMode());
        NavigationService.getInstance().setThemeMode(snapshot.themeMode());

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
        if (disposed.get()) {
            return;
        }
        ThemeMode resolvedThemeMode = newThemeMode == null ? ThemeMode.DARK : newThemeMode;
        if (themeMode.get() == resolvedThemeMode) {
            return;
        }
        themeMode.set(resolvedThemeMode);
        NavigationService.getInstance().setThemeMode(resolvedThemeMode);
        asyncScope.runFireAndForget("save theme preference", () -> uiPreferencesStore.saveThemeMode(resolvedThemeMode));
    }

    public void savePreferences() {
        if (disposed.get() || currentUser == null) {
            return;
        }

        logInfo(
                "Saving preferences: Age {}-{}, Dist {}km, Interested in {}, Theme {}",
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get(),
                themeMode.get());

        int normalizedMinAge = Math.clamp(
                minAge.get(), config.validation().minAge(), config.validation().maxAge());
        int normalizedMaxAge = Math.clamp(
                maxAge.get(), config.validation().minAge(), config.validation().maxAge());
        if (normalizedMinAge > normalizedMaxAge) {
            logWarn("Invalid age range: {}>{}, swapping values", normalizedMinAge, normalizedMaxAge);
            int temp = normalizedMinAge;
            normalizedMinAge = normalizedMaxAge;
            normalizedMaxAge = temp;
        }
        final int minAgeVal = normalizedMinAge;
        final int maxAgeVal = normalizedMaxAge;
        final int maxDistVal =
                Math.clamp(maxDistance.get(), 1, config.matching().maxDistanceKm());

        currentUser.setAgeRange(
                minAgeVal,
                maxAgeVal,
                config.validation().minAge(),
                config.validation().maxAge());
        currentUser.setMaxDistanceKm(maxDistVal, config.matching().maxDistanceKm());

        // Map UI GenderPreference to Set<Gender>
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
        NavigationService.getInstance().setThemeMode(selectedThemeMode);

        asyncScope.runLatest(
                "preferences-save",
                "save preferences",
                () -> {
                    persistDiscoveryPreferences(minAgeVal, maxAgeVal, maxDistVal, newInterests);
                    uiPreferencesStore.saveThemeMode(selectedThemeMode);
                    return selectedThemeMode;
                },
                themeMode::set);
    }

    private ThemeMode persistDiscoveryPreferences(
            int minAgeVal, int maxAgeVal, int maxDistVal, Set<Gender> newInterests) {
        if (profileUseCases != null) {
            var result = profileUseCases.updateDiscoveryPreferences(new UpdateDiscoveryPreferencesCommand(
                    UserContext.ui(currentUser.getId()), minAgeVal, maxAgeVal, maxDistVal, newInterests));
            if (!result.success()) {
                logWarn(
                        "Failed to save preferences via use-case: {}",
                        result.error().message());
            }
        } else {
            userStore.save(currentUser);
        }
        return themeMode.get() == null ? ThemeMode.DARK : themeMode.get();
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

    // --- Property Getters ---

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

    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        asyncScope.dispose();
    }

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "preferences", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> null));
        scope.setLoadingStateConsumer(loading::set);
        return scope;
    }

    private record PreferencesSnapshot(
            User user,
            int minAge,
            int maxAge,
            int maxDistance,
            GenderPreference genderPreference,
            ThemeMode themeMode) {
        private static PreferencesSnapshot empty(ThemeMode themeMode) {
            return new PreferencesSnapshot(null, 18, 99, 50, GenderPreference.EVERYONE, themeMode);
        }
    }
}
