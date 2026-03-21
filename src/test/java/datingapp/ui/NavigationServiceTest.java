package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.ui.NavigationService.ViewType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression tests for navigation context consumption. */
class NavigationServiceTest {

    private final NavigationService navigationService = NavigationService.getInstance();

    @Test
    @DisplayName("mismatched consumer keeps navigation context available")
    void mismatchedConsumerKeepsNavigationContext() {
        UUID payload = UUID.randomUUID();
        navigationService.setNavigationContext(ViewType.CHAT, payload);

        assertTrue(navigationService
                .consumeNavigationContext(ViewType.DASHBOARD, UUID.class)
                .isEmpty());
        assertEquals(
                payload,
                navigationService
                        .consumeNavigationContext(ViewType.CHAT, UUID.class)
                        .orElseThrow());
    }

    @Test
    @DisplayName("type mismatch keeps navigation context available")
    void typeMismatchKeepsNavigationContext() {
        String payload = "navigate-me";
        navigationService.setNavigationContext(ViewType.PROFILE, payload);

        assertTrue(navigationService
                .consumeNavigationContext(ViewType.PROFILE, Integer.class)
                .isEmpty());
        assertEquals(
                payload,
                navigationService
                        .consumeNavigationContext(ViewType.PROFILE, String.class)
                        .orElseThrow());
    }
}
