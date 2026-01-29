package datingapp.ui.viewmodel;

import datingapp.core.User;
import datingapp.core.storage.UserStorage;
import datingapp.ui.ViewModelFactory.UISession;
import java.util.EnumSet;
import java.util.Set;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Preferences screen.
 * Handles loading and saving user discovery preferences.
 */
public class PreferencesViewModel {
    private static final Logger logger = LoggerFactory.getLogger(PreferencesViewModel.class);

    private final UserStorage userStorage;
    private User currentUser;

    // UI-specific enum for single-selection preference
    public enum GenderPreference {
        MEN,
        WOMEN,
        EVERYONE
    }

    // Properties bound to UI
    private final IntegerProperty minAge = new SimpleIntegerProperty(18);
    private final IntegerProperty maxAge = new SimpleIntegerProperty(99);
    private final IntegerProperty maxDistance = new SimpleIntegerProperty(50);
    private final ObjectProperty<GenderPreference> interestedIn = new SimpleObjectProperty<>(GenderPreference.EVERYONE);

    public PreferencesViewModel(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public void initialize() {
        currentUser = UISession.getInstance().getCurrentUser();
        if (currentUser != null) {
            loadPreferences();
        }
    }

    private void loadPreferences() {
        minAge.set(currentUser.getMinAge());
        maxAge.set(currentUser.getMaxAge());
        maxDistance.set(currentUser.getMaxDistanceKm());

        // Map Set<Gender> to UI GenderPreference
        Set<User.Gender> interested = currentUser.getInterestedIn();
        if (interested.contains(User.Gender.MALE) && interested.contains(User.Gender.FEMALE)) {
            interestedIn.set(GenderPreference.EVERYONE);
        } else if (interested.contains(User.Gender.MALE)) {
            interestedIn.set(GenderPreference.MEN);
        } else if (interested.contains(User.Gender.FEMALE)) {
            interestedIn.set(GenderPreference.WOMEN);
        } else {
            interestedIn.set(GenderPreference.EVERYONE); // Default
        }

        logger.info(
                "Loaded preferences for {}: Age {}-{}, Dist {}km, Interested in {}",
                currentUser.getName(),
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get());
    }

    public void savePreferences() {
        if (currentUser == null) {
            return;
        }

        logger.info(
                "Saving preferences: Age {}-{}, Dist {}km, Interested in {}",
                minAge.get(),
                maxAge.get(),
                maxDistance.get(),
                interestedIn.get());

        currentUser.setAgeRange(minAge.get(), maxAge.get());
        currentUser.setMaxDistanceKm(maxDistance.get());

        // Map UI GenderPreference to Set<Gender>
        Set<User.Gender> newInterests = EnumSet.noneOf(User.Gender.class);
        GenderPreference preference = interestedIn.get();
        if (preference == null) {
            logger.warn("Interested-in preference missing; defaulting to EVERYONE");
            preference = GenderPreference.EVERYONE;
        }

        switch (preference) {
            case MEN -> newInterests.add(User.Gender.MALE);
            case WOMEN -> newInterests.add(User.Gender.FEMALE);
            case EVERYONE -> {
                newInterests.add(User.Gender.MALE);
                newInterests.add(User.Gender.FEMALE);
                newInterests.add(User.Gender.OTHER);
            }
            default -> {
                logger.warn("Unknown interested-in preference: {}", preference);
                newInterests.add(User.Gender.MALE);
                newInterests.add(User.Gender.FEMALE);
                newInterests.add(User.Gender.OTHER);
            }
        }
        currentUser.setInterestedIn(newInterests);

        // Persist
        userStorage.save(currentUser);
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
}
