package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.MainMenuRegistry.DispatchResult;
import datingapp.app.cli.MainMenuRegistry.MenuAction;
import datingapp.app.cli.MainMenuRegistry.MenuRenderContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MainMenuRegistry")
class MainMenuRegistryTest {

    @Nested
    @DisplayName("default registry")
    class DefaultRegistry {

        @Test
        @DisplayName("keeps option numbering and ordering stable")
        void keepsOptionOrderingStable() {
            MainMenuRegistry registry = MainMenuRegistry.createDefault(defaultActions());

            List<String> keys = registry.orderedOptions().stream()
                    .map(MainMenuRegistry.MenuOption::key)
                    .toList();

            assertEquals(expectedOrderedKeys(), keys);
        }

        @Test
        @DisplayName("encodes login guard metadata correctly")
        void encodesLoginGuardMetadata() {
            MainMenuRegistry registry = MainMenuRegistry.createDefault(defaultActions());

            assertFalse(registry.findOption("1").orElseThrow().requiresLogin());
            assertFalse(registry.findOption("2").orElseThrow().requiresLogin());
            assertFalse(
                    registry.findOption(MainMenuRegistry.EXIT_KEY).orElseThrow().requiresLogin());

            for (int option = 3; option <= 20; option++) {
                assertTrue(registry.findOption(String.valueOf(option))
                        .orElseThrow()
                        .requiresLogin());
            }
        }

        @Test
        @DisplayName("maps selections to the corresponding configured actions")
        void mapsSelectionsToConfiguredActions() {
            List<String> invoked = new ArrayList<>();
            MainMenuRegistry registry = MainMenuRegistry.createDefault(trackingActions(invoked));

            DispatchResult first =
                    registry.findOption("4").orElseThrow().action().execute();
            DispatchResult second =
                    registry.findOption("17").orElseThrow().action().execute();
            DispatchResult third = registry.findOption(MainMenuRegistry.EXIT_KEY)
                    .orElseThrow()
                    .action()
                    .execute();

            assertEquals(DispatchResult.CONTINUE, first);
            assertEquals(DispatchResult.CONTINUE, second);
            assertEquals(DispatchResult.EXIT, third);
            assertEquals(List.of("4", "17", MainMenuRegistry.EXIT_KEY), invoked);
        }

        @Test
        @DisplayName("renders dynamic unread label for conversations option")
        void rendersDynamicUnreadConversationLabel() {
            MainMenuRegistry registry = MainMenuRegistry.createDefault(defaultActions());
            MainMenuRegistry.MenuOption conversations =
                    registry.findOption("17").orElseThrow();

            assertEquals("💬 Conversations", conversations.displayLabel(new MenuRenderContext(0)));
            assertEquals("💬 Conversations (5 new)", conversations.displayLabel(new MenuRenderContext(5)));
        }

        @Test
        @DisplayName("rejects negative unread render context")
        void rejectsNegativeUnreadRenderContext() {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> new MenuRenderContext(-1));

            assertEquals("unreadConversationCount cannot be negative", error.getMessage());
        }
    }

    private static Map<String, MenuAction> defaultActions() {
        Map<String, MenuAction> actions = new HashMap<>();
        for (String key : expectedOrderedKeys()) {
            if (MainMenuRegistry.EXIT_KEY.equals(key)) {
                actions.put(key, () -> DispatchResult.EXIT);
            } else {
                actions.put(key, () -> DispatchResult.CONTINUE);
            }
        }
        return actions;
    }

    private static Map<String, MenuAction> trackingActions(List<String> invoked) {
        Map<String, MenuAction> actions = new HashMap<>();
        for (String key : expectedOrderedKeys()) {
            if (MainMenuRegistry.EXIT_KEY.equals(key)) {
                actions.put(key, () -> {
                    invoked.add(key);
                    return DispatchResult.EXIT;
                });
                continue;
            }

            actions.put(key, () -> {
                invoked.add(key);
                return DispatchResult.CONTINUE;
            });
        }
        return actions;
    }

    private static List<String> expectedOrderedKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            keys.add(String.valueOf(i));
        }
        keys.add(MainMenuRegistry.EXIT_KEY);
        return keys;
    }
}
