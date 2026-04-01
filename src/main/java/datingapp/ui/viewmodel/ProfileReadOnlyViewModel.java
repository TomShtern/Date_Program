package datingapp.ui.viewmodel;

import datingapp.core.AppConfig;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Read-only profile view model used for viewing another user's public profile details. */
public final class ProfileReadOnlyViewModel extends BaseViewModel {

    private final UiUserStore userStore;
    private final AppConfig config;
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty bio = new SimpleStringProperty("");
    private final StringProperty location = new SimpleStringProperty("");
    private final StringProperty lookingFor = new SimpleStringProperty("");
    private final StringProperty interests = new SimpleStringProperty("");
    private final ObservableList<String> photoUrls = FXCollections.observableArrayList();
    private final IntegerProperty currentPhotoIndex = new SimpleIntegerProperty(0);
    private final StringProperty currentPhotoUrl = new SimpleStringProperty("");
    private final PhotoCarouselState photoCarousel = new PhotoCarouselState();

    public ProfileReadOnlyViewModel(UiUserStore userStore, AppConfig config) {
        this(userStore, config, new JavaFxUiThreadDispatcher());
    }

    public ProfileReadOnlyViewModel(UiUserStore userStore, AppConfig config, UiThreadDispatcher dispatcher) {
        super("profile-view", dispatcher);
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public void loadUser(UUID userId) {
        if (userId == null || isDisposed()) {
            return;
        }
        asyncScope.runLatest(
                "profile-view-load",
                "load profile view",
                () -> {
                    Set<UUID> ids = Set.of(userId);
                    return userStore.findByIds(ids).get(userId);
                },
                this::applyUser);
    }

    private void applyUser(datingapp.core.model.User user) {
        if (user == null) {
            name.set("Unknown user");
            bio.set("Profile unavailable.");
            location.set("Location unavailable");
            lookingFor.set("");
            interests.set("");
            photoUrls.clear();
            currentPhotoUrl.set("");
            return;
        }
        int age = user.getAge(config.safety().userTimeZone()).orElse(0);
        name.set(user.getName() + ", " + age);
        bio.set(user.getBio() == null || user.getBio().isBlank() ? "No bio provided." : user.getBio());
        location.set(
                user.hasLocation()
                        ? String.format(java.util.Locale.ROOT, "%.4f, %.4f", user.getLat(), user.getLon())
                        : "Location not shared");
        lookingFor.set(user.getLookingFor() != null ? user.getLookingFor().getDisplayName() : "Open to meeting people");
        interests.set(formatInterests(user.getInterests()));
        photoCarousel.setPhotos(user.getPhotoUrls());
        syncPhotoCarousel();
    }

    private String formatInterests(Set<Interest> selectedInterests) {
        if (selectedInterests == null || selectedInterests.isEmpty()) {
            return "No interests shared yet.";
        }
        return selectedInterests.stream()
                .map(Interest::getDisplayName)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
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
        currentPhotoUrl.set(photoCarousel.currentPhotoUrl() != null ? photoCarousel.currentPhotoUrl() : "");
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty bioProperty() {
        return bio;
    }

    public StringProperty locationProperty() {
        return location;
    }

    public StringProperty lookingForProperty() {
        return lookingFor;
    }

    public StringProperty interestsProperty() {
        return interests;
    }

    public ObservableList<String> getPhotoUrls() {
        return photoUrls;
    }

    public IntegerProperty currentPhotoIndexProperty() {
        return currentPhotoIndex;
    }

    public StringProperty currentPhotoUrlProperty() {
        return currentPhotoUrl;
    }

    @Override
    protected void onDispose() {
        photoUrls.clear();
    }
}
