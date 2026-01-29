package datingapp.ui.viewmodel;

import datingapp.core.Dealbreakers;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.ProfileCompletionService;
import datingapp.core.ProfileCompletionService.CompletionResult;
import datingapp.core.User;
import datingapp.core.User.Gender;
import datingapp.core.storage.UserStorage;
import datingapp.ui.ViewModelFactory.UISession;
import datingapp.ui.util.UiServices;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Profile Editor screen.
 * Handles editing of user bio, location, interests, lifestyle, and profile photo.
 */
public class ProfileViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ProfileViewModel.class);
    private static final String PLACEHOLDER_PHOTO_URL = "placeholder://default-avatar";

    private final UserStorage userStorage;

    // Observable properties for form binding - Basic Info
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty bio = new SimpleStringProperty("");
    private final StringProperty location = new SimpleStringProperty("");
    private final StringProperty interests = new SimpleStringProperty("");
    private final StringProperty completionStatus = new SimpleStringProperty("0%");
    private final StringProperty completionDetails = new SimpleStringProperty("");
    private final StringProperty primaryPhotoUrl = new SimpleStringProperty("");

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
    private final StringProperty dealbreakersStatus = new SimpleStringProperty("None set");

    // Selected interests (observable set for multi-selection)
    private final ObservableSet<Interest> selectedInterests =
            FXCollections.observableSet(EnumSet.noneOf(Interest.class));

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

        // Basic info
        name.set(user.getName());
        bio.set(user.getBio() != null ? user.getBio() : "");
        location.set(formatLocation(user.getLat(), user.getLon()));

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
            dealbreakersStatus.set("None set");
        }

        // Primary photo
        updatePrimaryPhoto(user);

        // Calculate completion using static method
        updateCompletion(user);
    }

    private void updateDealbreakersStatus(Dealbreakers db) {
        if (!db.hasAnyDealbreaker()) {
            dealbreakersStatus.set("None set");
        } else {
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
            dealbreakersStatus.set(count + " filter" + (count != 1 ? "s" : "") + " set");
        }
    }

    private void updateCompletion(User user) {
        try {
            CompletionResult result = ProfileCompletionService.calculate(user);
            completionStatus.set(result.getDisplayString());
            completionDetails.set(buildCompletionDetails(result));
        } catch (Exception e) {
            logger.error("Failed to calculate profile completion", e);
            completionStatus.set("--");
            completionDetails.set("Unable to calculate completion");
        }
    }

    private void updatePrimaryPhoto(User user) {
        if (user == null) {
            primaryPhotoUrl.set("");
            return;
        }
        List<String> urls = user.getPhotoUrls();
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

        // Update basic fields
        user.setBio(bio.get());

        // Save gender and interested in
        if (gender.get() != null) {
            user.setGender(gender.get());
        }
        if (!interestedInGenders.isEmpty()) {
            user.setInterestedIn(EnumSet.copyOf(interestedInGenders));
        }
        applyBirthDate(user);

        // Parse location if provided
        String loc = location.get();
        if (loc != null && loc.contains(",")) {
            try {
                String[] parts = loc.split(",");
                if (parts.length == 2) {
                    user.setLocation(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
                }
            } catch (NumberFormatException _) {
                logger.warn("Could not parse location: {}", loc);
            }
        }

        // Save interests
        if (!selectedInterests.isEmpty()) {
            user.setInterests(EnumSet.copyOf(selectedInterests));
        }

        // Save lifestyle fields
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

        // Save search preferences
        try {
            int min = Integer.parseInt(minAge.get());
            int max = Integer.parseInt(maxAge.get());
            if (min >= 18 && max <= 120 && min <= max) {
                user.setAgeRange(min, max);
            }
        } catch (NumberFormatException _) {
            logger.warn("Invalid age range values");
        }

        try {
            int dist = Integer.parseInt(maxDistance.get());
            if (dist > 0 && dist <= 500) {
                user.setMaxDistanceKm(dist);
            }
        } catch (NumberFormatException _) {
            logger.warn("Invalid max distance value");
        }

        // Save dealbreakers
        if (dealbreakers.get() != null) {
            user.setDealbreakers(dealbreakers.get());
        }

        // Save to storage
        userStorage.save(user);

        // Try to activate user if profile is now complete
        if (user.isComplete() && user.getState() == User.State.INCOMPLETE) {
            try {
                user.activate();
                userStorage.save(user);
                logger.info("User {} activated after profile completion", user.getName());
                UiServices.Toast.getInstance().showSuccess("Profile complete! You're now active!");
            } catch (IllegalStateException e) {
                logger.warn("Could not activate user: {}", e.getMessage());
            }
        }

        // Update the user in session
        UISession.getInstance().setCurrentUser(user);

        // Update completion display
        updateCompletion(user);

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
                UiServices.Toast.getInstance().showWarning("Maximum " + Interest.MAX_PER_USER + " interests allowed");
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
        User user = UISession.getInstance().getCurrentUser();
        if (user == null) {
            logger.warn("No current user for photo save");
            return;
        }
        if (photoFile == null || !photoFile.isFile()) {
            logger.warn("Invalid photo file selected: {}", photoFile);
            UiServices.Toast.getInstance().showError("Invalid photo file selected");
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
                    updateCompletion(user);
                    UiServices.Toast.getInstance().showSuccess("Photo saved!");
                    logger.info("Profile photo saved: {}", destination);
                });

            } catch (IOException e) {
                logger.error("Failed to save profile photo", e);
                Platform.runLater(
                        () -> UiServices.Toast.getInstance().showError("Failed to save photo: " + e.getMessage()));
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

    private String buildCompletionDetails(CompletionResult result) {
        if (result == null) {
            return "";
        }
        List<String> missing = result.breakdown().stream()
                .flatMap(breakdown -> breakdown.missingItems().stream())
                .map(String::trim)
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

    private void applyBirthDate(User user) {
        LocalDate selected = birthDate.get();
        if (selected == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (selected.isAfter(today)) {
            logger.warn("Birth date cannot be in the future: {}", selected);
            UiServices.Toast.getInstance().showWarning("Birth date cannot be in the future");
            return;
        }
        int age = Period.between(selected, today).getYears();
        if (age < 18 || age > 120) {
            logger.warn("Birth date outside allowed age range: {}", selected);
            UiServices.Toast.getInstance().showWarning("Birth date must be for ages 18-120");
            return;
        }
        user.setBirthDate(selected);
    }
}
