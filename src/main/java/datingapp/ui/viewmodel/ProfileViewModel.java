package datingapp.ui.viewmodel;

import datingapp.core.ProfileCompletionService;
import datingapp.core.ProfileCompletionService.CompletionResult;
import datingapp.core.User;
import datingapp.core.UserStorage;
import datingapp.ui.UISession;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Profile Editor screen.
 * Handles editing of user bio and location.
 */
public class ProfileViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ProfileViewModel.class);

    private final UserStorage userStorage;

    // Observable properties for form binding
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty bio = new SimpleStringProperty("");
    private final StringProperty location = new SimpleStringProperty("");
    private final StringProperty interests = new SimpleStringProperty("");
    private final StringProperty completionStatus = new SimpleStringProperty("0%");

    @SuppressWarnings("unused")
    public ProfileViewModel(UserStorage userStorage, ProfileCompletionService unused) {
        // ProfileCompletionService has static methods, so we ignore the parameter
        this.userStorage = userStorage;
    }

    /**
     * Loads the current user's data into the form properties.
     */
    public void loadCurrentUser() {
        User user = UISession.getInstance().getCurrentUser();
        if (user == null) {
            logger.warn("No current user to load");
            return;
        }

        logger.info("Loading profile for user: {}", user.getName());

        name.set(user.getName());
        bio.set(user.getBio() != null ? user.getBio() : "");
        location.set(formatLocation(user.getLat(), user.getLon()));

        // Format interests as comma-separated list
        if (user.getInterests() != null && !user.getInterests().isEmpty()) {
            String interestStr = user.getInterests().stream()
                    .map(Enum::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            interests.set(interestStr);
        } else {
            interests.set("");
        }

        // Calculate completion using static method
        updateCompletion(user);
    }

    private void updateCompletion(User user) {
        try {
            CompletionResult result = ProfileCompletionService.calculate(user);
            completionStatus.set(result.getDisplayString());
        } catch (Exception e) {
            logger.error("Failed to calculate profile completion", e);
            completionStatus.set("--");
        }
    }

    private String formatLocation(double lat, double lon) {
        if (lat == 0 && lon == 0) {
            return "";
        }
        return String.format("%.4f, %.4f", lat, lon);
    }

    /**
     * Saves the profile changes to storage.
     */
    public void save() {
        User user = UISession.getInstance().getCurrentUser();
        if (user == null) {
            logger.warn("No current user to save");
            return;
        }

        logger.info("Saving profile for user: {}", user.getName());

        // Update user fields
        user.setBio(bio.get());

        // Parse location if provided
        String loc = location.get();
        if (loc != null && loc.contains(",")) {
            try {
                String[] parts = loc.split(",");
                if (parts.length == 2) {
                    user.setLocation(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
                }
            } catch (NumberFormatException e) {
                logger.warn("Could not parse location: {}", loc);
            }
        }

        // Save to storage
        userStorage.save(user);

        // Update the user in session
        UISession.getInstance().setCurrentUser(user);

        logger.info("Profile saved successfully");
    }

    // --- Properties for data binding ---

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty bioProperty() {
        return bio;
    }

    public StringProperty locationProperty() {
        return location;
    }

    public StringProperty interestsProperty() {
        return interests;
    }

    public StringProperty completionStatusProperty() {
        return completionStatus;
    }
}
