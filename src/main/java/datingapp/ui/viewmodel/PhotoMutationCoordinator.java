package datingapp.ui.viewmodel;

import datingapp.core.model.User;
import datingapp.ui.LocalPhotoStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

final class PhotoMutationCoordinator {
    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

    private final UiUserStore userStore;
    private final LocalPhotoStore photoStore;
    private final Object photoMutationLock = new Object();

    PhotoMutationCoordinator(UiUserStore userStore, LocalPhotoStore photoStore) {
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.photoStore = Objects.requireNonNull(photoStore, "photoStore cannot be null");
    }

    PhotoMutationResult savePhoto(User user, File photoFile) throws IOException {
        validateUser(user);
        validateFile(photoFile);
        synchronized (photoMutationLock) {
            List<String> updatedPhotoUrls =
                    photoStore.importPhoto(user.getId(), user.getPhotoUrls(), photoFile.toPath());
            return persist(user, updatedPhotoUrls, updatedPhotoUrls.size() - 1, "Photo saved!");
        }
    }

    PhotoMutationResult replacePhoto(User user, int index, File photoFile) throws IOException {
        validateUser(user);
        validateFile(photoFile);
        synchronized (photoMutationLock) {
            List<String> updatedPhotoUrls =
                    photoStore.replacePhoto(user.getId(), user.getPhotoUrls(), index, photoFile.toPath());
            return persist(user, updatedPhotoUrls, index, "Photo replaced.");
        }
    }

    PhotoMutationResult deletePhoto(User user, int index) throws IOException {
        validateUser(user);
        synchronized (photoMutationLock) {
            List<String> updatedPhotoUrls = photoStore.deletePhoto(user.getPhotoUrls(), index);
            int selectedIndex = updatedPhotoUrls.isEmpty() ? 0 : Math.min(index, updatedPhotoUrls.size() - 1);
            return persist(user, updatedPhotoUrls, selectedIndex, "Photo removed.");
        }
    }

    PhotoMutationResult setPrimaryPhoto(User user, int index) {
        validateUser(user);
        synchronized (photoMutationLock) {
            List<String> updatedPhotoUrls = photoStore.setPrimaryPhoto(user.getPhotoUrls(), index);
            return persist(user, updatedPhotoUrls, 0, "Primary photo updated.");
        }
    }

    private PhotoMutationResult persist(
            User user, List<String> updatedPhotoUrls, int selectedIndex, String successMessage) {
        user.setPhotoUrls(updatedPhotoUrls);
        userStore.save(user);
        return new PhotoMutationResult(updatedPhotoUrls, selectedIndex, successMessage);
    }

    private static void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }
    }

    private static void validateFile(File photoFile) {
        if (photoFile == null || !photoFile.isFile()) {
            throw new IllegalArgumentException("Invalid photo file selected");
        }
        if (photoFile.length() > MAX_PHOTO_SIZE_BYTES) {
            throw new IllegalArgumentException("Photo must be 5 MB or smaller");
        }
    }

    record PhotoMutationResult(List<String> photoUrls, int selectedIndex, String successMessage) {
        PhotoMutationResult {
            photoUrls = List.copyOf(photoUrls);
        }
    }
}
