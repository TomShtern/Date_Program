package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.ui.NavigationService.ViewType;
import java.lang.reflect.Field;
import java.util.Deque;
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

    @Test
    @DisplayName("resetNavigationState clears history and pending navigation context")
    void resetNavigationStateClearsHistoryAndPendingNavigationContext() throws Exception {
        navigationService.setNavigationContext(ViewType.CHAT, UUID.randomUUID());
        navigationHistory().push(ViewType.DASHBOARD);
        navigationHistory().push(ViewType.CHAT);

        navigationService.resetNavigationState();

        assertTrue(navigationHistory().isEmpty());
        assertTrue(navigationService
                .consumeNavigationContext(ViewType.CHAT, UUID.class)
                .isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Deque<ViewType> navigationHistory() throws Exception {
        Field field = NavigationService.class.getDeclaredField("navigationHistory");
        field.setAccessible(true);
        return (Deque<ViewType>) field.get(navigationService);
    }
}
