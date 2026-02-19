package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class NavigationServiceContextTest {

    @AfterEach
    void cleanUpNavigationState() {
        NavigationService navigation = NavigationService.getInstance();
        navigation.consumeNavigationContext(NavigationService.ViewType.CHAT, Object.class);
        navigation.clearHistory();
    }

    @Test
    @DisplayName("typed context is consumed by matching view and type")
    void typedContextConsumedByMatchingViewAndType() {
        NavigationService navigation = NavigationService.getInstance();
        UUID payload = UUID.randomUUID();

        navigation.setNavigationContext(NavigationService.ViewType.CHAT, payload);

        Optional<UUID> consumed = navigation.consumeNavigationContext(NavigationService.ViewType.CHAT, UUID.class);

        assertTrue(consumed.isPresent());
        assertEquals(payload, consumed.orElseThrow());
    }

    @Test
    @DisplayName("typed context is rejected for wrong target view")
    void typedContextRejectedForWrongTargetView() {
        NavigationService navigation = NavigationService.getInstance();
        UUID payload = UUID.randomUUID();

        navigation.setNavigationContext(NavigationService.ViewType.MATCHES, payload);

        Optional<UUID> consumed = navigation.consumeNavigationContext(NavigationService.ViewType.CHAT, UUID.class);

        assertFalse(consumed.isPresent());
        assertFalse(navigation
                .consumeNavigationContext(NavigationService.ViewType.CHAT, Object.class)
                .isPresent());
    }

    @Test
    @DisplayName("typed context is rejected for wrong payload type")
    void typedContextRejectedForWrongPayloadType() {
        NavigationService navigation = NavigationService.getInstance();

        navigation.setNavigationContext(NavigationService.ViewType.CHAT, "not-a-uuid");

        Optional<UUID> consumed = navigation.consumeNavigationContext(NavigationService.ViewType.CHAT, UUID.class);

        assertFalse(consumed.isPresent());
        assertFalse(navigation
                .consumeNavigationContext(NavigationService.ViewType.CHAT, Object.class)
                .isPresent());
    }

    @Test
    @DisplayName("typed API supports unscoped context payloads")
    void typedApiSupportsUnscopedContextPayloads() {
        NavigationService navigation = NavigationService.getInstance();
        String payload = "context-payload";

        navigation.setNavigationContext(null, payload);

        Optional<String> consumed = navigation.consumeNavigationContext(NavigationService.ViewType.CHAT, String.class);
        assertEquals(payload, consumed.orElseThrow());
    }
}
