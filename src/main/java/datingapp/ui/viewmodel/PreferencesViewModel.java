package datingapp.ui.viewmodel;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
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
    private static final AppConfig CONFIG = AppConfig.defaults();

    private final UiUserStore userStore;
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

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public PreferencesViewModel(UiUserStore userStore) {
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
    }

    public void initialize() {
        if (disposed.get()) {
            return;
        }
        currentUser = AppSession.getInstance().getCurrentUser();
        if (currentUser != null) {
            loadPreferences();
        }
    }

    private void loadPreferences() {
        minAge.set(currentUser.getMinAge());
        maxAge.set(currentUser.getMaxAge());
        maxDistance.set(currentUser.getMaxDistanceKm());

        // Map Set<Gender> to UI GenderPreference
        Set<Gender> interested = currentUser.getInterestedIn();
        if (interested.contains(Gender.MALE) && interested.contains(Gender.FEMALE)) {
            interestedIn.set(GenderPreference.EVERYONE);
        } else if (interested.contains(Gender.MALE)) {
            interestedIn.set(GenderPreference.MEN);
        } else if (interested.contains(Gender.FEMALE)) {
            interestedIn.set(GenderPreference.WOMEN);
        } else {
            interestedIn.set(GenderPreference.EVERYONE); // Default
        }

        logInfo(
                "Loaded preferences for {}: Age {}-{}, Dist {}km, Interested in {}",
                currentUser.getName(),
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get());
    }

    public void savePreferences() {
        if (disposed.get() || currentUser == null) {
            return;
        }

        logInfo(
                "Saving preferences: Age {}-{}, Dist {}km, Interested in {}",
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get());

        // M-17: Validate input ranges before saving using centralized config bounds
        int minAgeVal = Math.clamp(minAge.get(), CONFIG.minAge(), CONFIG.maxAge());
        int maxAgeVal = Math.clamp(maxAge.get(), CONFIG.minAge(), CONFIG.maxAge());
        if (minAgeVal > maxAgeVal) {
            logWarn("Invalid age range: {}>{}, swapping values", minAgeVal, maxAgeVal);
            int temp = minAgeVal;
            minAgeVal = maxAgeVal;
            maxAgeVal = temp;
        }
        int maxDistVal = Math.clamp(maxDistance.get(), 1, CONFIG.maxDistanceKm());

        currentUser.setAgeRange(minAgeVal, maxAgeVal);
        currentUser.setMaxDistanceKm(maxDistVal);

        // Map UI GenderPreference to Set<Gender>
        Set<Gender> newInterests = EnumSet.noneOf(Gender.class);
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

        // Persist
        userStore.save(currentUser);
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

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
    }
}
