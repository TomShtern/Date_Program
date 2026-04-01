package datingapp.ui.viewmodel;

import java.util.List;

final class PhotoCarouselState {
    private List<String> photoUrls = List.of();
    private int currentPhotoIndex;

    void setPhotos(List<String> photoUrls) {
        this.photoUrls = photoUrls != null ? List.copyOf(photoUrls) : List.of();
        if (this.photoUrls.isEmpty()) {
            currentPhotoIndex = 0;
            return;
        }
        if (currentPhotoIndex >= this.photoUrls.size()) {
            currentPhotoIndex = this.photoUrls.size() - 1;
        }
    }

    List<String> photoUrls() {
        return photoUrls;
    }

    int currentPhotoIndex() {
        return currentPhotoIndex;
    }

    int photoCount() {
        return photoUrls.size();
    }

    String currentPhotoUrl() {
        if (photoUrls.isEmpty()) {
            return null;
        }
        return photoUrls.get(currentPhotoIndex);
    }

    String showNextPhoto() {
        if (photoUrls.isEmpty()) {
            return null;
        }
        currentPhotoIndex = (currentPhotoIndex + 1) % photoUrls.size();
        return currentPhotoUrl();
    }

    String showPreviousPhoto() {
        if (photoUrls.isEmpty()) {
            return null;
        }
        currentPhotoIndex = currentPhotoIndex == 0 ? photoUrls.size() - 1 : currentPhotoIndex - 1;
        return currentPhotoUrl();
    }
}
