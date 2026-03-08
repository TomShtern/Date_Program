package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class NavigationServiceRouteTest {

    @Test
    @DisplayName("preferences route points at the real preferences fxml")
    void preferencesRoutePointsAtRealPreferencesFxml() {
        assertEquals("/fxml/preferences.fxml", NavigationService.ViewType.PREFERENCES.getFxmlPath());
        assertNotNull(
                NavigationService.class.getResource(NavigationService.ViewType.PREFERENCES.getFxmlPath()),
                "Preferences FXML resource should exist");
    }

    @Test
    @DisplayName("safety and notes routes point at real fxml resources")
    void safetyAndNotesRoutesPointAtRealFxml() {
        assertEquals("/fxml/safety.fxml", NavigationService.ViewType.SAFETY.getFxmlPath());
        assertEquals("/fxml/notes.fxml", NavigationService.ViewType.NOTES.getFxmlPath());
        assertNotNull(
                NavigationService.class.getResource(NavigationService.ViewType.SAFETY.getFxmlPath()),
                "Safety FXML resource should exist");
        assertNotNull(
                NavigationService.class.getResource(NavigationService.ViewType.NOTES.getFxmlPath()),
                "Notes FXML resource should exist");
    }
}
