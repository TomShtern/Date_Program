package datingapp.ui.viewmodel;

import datingapp.core.ProfileCompletionService;
import datingapp.core.ProfileCompletionService.CompletionResult;
import datingapp.core.User;
import datingapp.core.UserStorage;
import datingapp.ui.ViewModelFactory.UISession;
import datingapp.ui.util.ToastService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Profile Editor screen.
 * Handles editing of user bio, location, and profile photo.
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
    private final StringProperty primaryPhotoUrl = new SimpleStringProperty("");

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
            } catch (NumberFormatException ignored) {
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

    /**
     * Returns the primary photo URL property for binding.
     *
     * @return the primary photo URL property
     */
    public StringProperty primaryPhotoUrlProperty() {
        return primaryPhotoUrl;
    }

    /**
     * Saves a profile photo to app data directory and updates user record.
     * Runs on a virtual thread to avoid blocking the UI.
     *
     * @param photoFile the selected photo file
     */
    public void savePhoto(File photoFile) {
        User user = UISession.getInstance().getCurrentUser();
        if (user == null) {
            logger.warn("No current user for photo save");
            return;
        }

        Thread.ofVirtual().name("photo-save").start(() -> {
            try {
                // 1. Create app data directory for photos
                Path appData = Paths.get(System.getProperty("user.home"), ".datingapp", "photos");
                Files.createDirectories(appData);

                // 2. Generate unique filename based on user ID and timestamp
                String filename = user.getId() + "_" + System.currentTimeMillis() + getExtension(photoFile);
                Path destination = appData.resolve(filename);

                // 3. Copy file to app data directory
                Files.copy(photoFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);

                // 4. Update user record with photo URL (use file:// URI)
                String photoUrl = destination.toUri().toString();
                user.setPhotoUrls(List.of(photoUrl));
                userStorage.save(user);

                // 5. Update UI on FX thread
                Platform.runLater(() -> {
                    primaryPhotoUrl.set(photoUrl);
                    ToastService.getInstance().showSuccess("Photo saved!");
                    logger.info("Profile photo saved: {}", destination);
                });

            } catch (IOException e) {
                logger.error("Failed to save profile photo", e);
                Platform.runLater(
                        () -> ToastService.getInstance().showError("Failed to save photo: " + e.getMessage()));
            }
        });
    }

    /**
     * Gets the file extension from a file.
     */
    private String getExtension(File file) {
        String fileName = file.getName();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : ".jpg";
    }
}
