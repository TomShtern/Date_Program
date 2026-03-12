package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UiPreferencesStore")
class UiPreferencesStoreTest {

    private String nodePath;

    @AfterEach
    void cleanupNode() throws Exception {
        if (nodePath != null) {
            Preferences.userRoot().node(nodePath).removeNode();
            Preferences.userRoot().flush();
        }
    }

    @Test
    @DisplayName("theme mode round-trips and defaults to DARK for invalid values")
    void themeModeRoundTripAndDefault() {
        nodePath = "/datingapp/test/" + UUID.randomUUID();
        UiPreferencesStore store = new UiPreferencesStore(nodePath);

        store.saveThemeMode(UiPreferencesStore.ThemeMode.LIGHT);
        assertEquals(UiPreferencesStore.ThemeMode.LIGHT, store.loadThemeMode());

        Preferences.userRoot().node(nodePath).put("themeMode", "bogus");
        assertEquals(UiPreferencesStore.ThemeMode.DARK, store.loadThemeMode());
    }

    @Test
    @DisplayName("seen achievement ids persist trimmed, unique, sorted values")
    void seenAchievementIdsPersistNormalized() {
        nodePath = "/datingapp/test/" + UUID.randomUUID();
        UiPreferencesStore store = new UiPreferencesStore(nodePath);

        store.saveSeenAchievementIds(new LinkedHashSet<>(List.of("  a2  ", "a1", "", "a1")));

        Set<String> loaded = store.loadSeenAchievementIds();
        assertEquals(Set.of("a1", "a2"), loaded);
        assertTrue(loaded.contains("a1"));
    }
}
