package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link ErrorMessages} constants. Verifies templates produce correct
 * output and all constants are non-null/non-blank.
 */
@Timeout(5)
@SuppressWarnings("unused")
class ErrorMessagesTest {

    @Nested
    @DisplayName("Static messages")
    class StaticMessages {

        @Test
        @DisplayName("messaging constants are non-blank")
        void messagingConstantsNonBlank() {
            assertFalse(ErrorMessages.SENDER_NOT_FOUND.isBlank());
            assertFalse(ErrorMessages.RECIPIENT_NOT_FOUND.isBlank());
            assertFalse(ErrorMessages.NO_ACTIVE_MATCH.isBlank());
            assertFalse(ErrorMessages.EMPTY_MESSAGE.isBlank());
        }

        @Test
        @DisplayName("undo constants are non-blank")
        void undoConstantsNonBlank() {
            assertFalse(ErrorMessages.NO_SWIPE_TO_UNDO.isBlank());
            assertFalse(ErrorMessages.UNDO_WINDOW_EXPIRED.isBlank());
            assertFalse(ErrorMessages.LIKE_NOT_FOUND.isBlank());
        }

        @Test
        @DisplayName("general constants are non-blank")
        void generalConstantsNonBlank() {
            assertFalse(ErrorMessages.USER_NOT_FOUND.isBlank());
            assertFalse(ErrorMessages.USER_NOT_ACTIVE.isBlank());
            assertFalse(ErrorMessages.MATCH_NOT_FOUND.isBlank());
            assertFalse(ErrorMessages.CANNOT_LIKE_SELF.isBlank());
        }
    }

    @Nested
    @DisplayName("Parameterized templates")
    class ParameterizedTemplates {

        @Test
        @DisplayName("MESSAGE_TOO_LONG formats correctly with int")
        void messageTooLongFormats() {
            String result = ErrorMessages.MESSAGE_TOO_LONG.formatted(500);
            assertEquals("Message too long (max 500 characters)", result);
        }

        @Test
        @DisplayName("NAME_TOO_LONG formats correctly with limit")
        void nameTooLongFormats() {
            String result = ErrorMessages.NAME_TOO_LONG.formatted(100);
            assertEquals("Name too long (max 100 chars)", result);
        }

        @Test
        @DisplayName("AGE_TOO_YOUNG formats correctly with min age")
        void ageTooYoungFormats() {
            String result = ErrorMessages.AGE_TOO_YOUNG.formatted(18);
            assertEquals("Must be 18 or older", result);
        }

        @Test
        @DisplayName("HEIGHT_TOO_SHORT formats correctly")
        void heightTooShortFormats() {
            String result = ErrorMessages.HEIGHT_TOO_SHORT.formatted(50);
            assertEquals("Height too short (min 50cm)", result);
        }

        @Test
        @DisplayName("HEIGHT_TOO_TALL formats correctly")
        void heightTooTallFormats() {
            String result = ErrorMessages.HEIGHT_TOO_TALL.formatted(300);
            assertEquals("Height too tall (max 300cm)", result);
        }

        @Test
        @DisplayName("DISTANCE_TOO_SHORT formats correctly")
        void distanceTooShortFormats() {
            String result = ErrorMessages.DISTANCE_TOO_SHORT.formatted(1);
            assertEquals("Distance must be at least 1km", result);
        }

        @Test
        @DisplayName("DISTANCE_TOO_FAR formats correctly")
        void distanceTooFarFormats() {
            String result = ErrorMessages.DISTANCE_TOO_FAR.formatted(500);
            assertEquals("Distance too far (max 500km)", result);
        }

        @Test
        @DisplayName("BIO_TOO_LONG formats correctly")
        void bioTooLongFormats() {
            String result = ErrorMessages.BIO_TOO_LONG.formatted(500);
            assertEquals("Bio too long (max 500 chars)", result);
        }

        @Test
        @DisplayName("UNDO_FAILED formats correctly with cause")
        void undoFailedFormats() {
            String result = ErrorMessages.UNDO_FAILED.formatted("timeout");
            assertEquals("Failed to undo: timeout", result);
        }

        @Test
        @DisplayName("NULL_PARAMETER formats correctly")
        void nullParameterFormats() {
            String result = ErrorMessages.NULL_PARAMETER.formatted("userId");
            assertEquals("userId cannot be null", result);
        }
    }
}
