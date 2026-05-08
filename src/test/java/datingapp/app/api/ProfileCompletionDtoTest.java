package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileCompletionDto")
class ProfileCompletionDtoTest {

    @Test
    @DisplayName("of(null) returns null")
    void ofNullReturnsNull() {
        assertNull(ProfileCompletionDto.of(null));
    }

    @Test
    @DisplayName("of(view) copies all current fields")
    void ofViewCopiesAllCurrentFields() {
        ProfileCompletionView view =
                new ProfileCompletionView(List.of("bio", "photoUrls"), List.of("Bio", "Photo"), 8, true, false, true);

        ProfileCompletionDto dto = ProfileCompletionDto.of(view);

        assertAll(
                () -> assertEquals(view.missingProfileFields(), dto.missingProfileFields()),
                () -> assertEquals(view.missingProfileFieldLabels(), dto.missingProfileFieldLabels()),
                () -> assertEquals(view.requiredProfileFieldCount(), dto.requiredProfileFieldCount()),
                () -> assertEquals(view.profileComplete(), dto.profileComplete()),
                () -> assertEquals(view.canActivate(), dto.canActivate()),
                () -> assertEquals(view.canBrowse(), dto.canBrowse()));
    }
}
