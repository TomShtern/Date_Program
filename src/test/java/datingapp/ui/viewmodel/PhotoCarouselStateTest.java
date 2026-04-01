package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PhotoCarouselState")
class PhotoCarouselStateTest {

    @Test
    @DisplayName("next and previous wrap through the photo list")
    void nextAndPreviousWrapThroughPhotoList() {
        PhotoCarouselState state = new PhotoCarouselState();
        state.setPhotos(List.of("one", "two", "three"));

        assertEquals("one", state.currentPhotoUrl());
        assertEquals("two", state.showNextPhoto());
        assertEquals("three", state.showNextPhoto());
        assertEquals("one", state.showNextPhoto());
        assertEquals("three", state.showPreviousPhoto());
    }

    @Test
    @DisplayName("empty lists reset index and current photo")
    void emptyListsResetIndexAndCurrentPhoto() {
        PhotoCarouselState state = new PhotoCarouselState();
        state.setPhotos(List.of("one", "two"));
        state.showNextPhoto();

        state.setPhotos(List.of());

        assertEquals(0, state.currentPhotoIndex());
        assertNull(state.currentPhotoUrl());
        assertEquals(List.of(), state.photoUrls());
    }
}
