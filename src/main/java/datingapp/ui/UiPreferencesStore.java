package datingapp.ui;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores UI-local preferences that should not live in domain models or runtime
 * app config.
 */
public final class UiPreferencesStore {

    private static final Logger logger = LoggerFactory.getLogger(UiPreferencesStore.class);
    private static final String DEFAULT_NODE_PATH = "/datingapp/ui";
    private static final String THEME_MODE_KEY = "themeMode";
    private static final String SEEN_ACHIEVEMENT_IDS_KEY = "seenAchievementIds";

    private final Preferences preferences;

    public enum ThemeMode {
        DARK,
        LIGHT;

        public boolean isDark() {
            return this == DARK;
        }

        public static ThemeMode fromDarkMode(boolean darkMode) {
            return darkMode ? DARK : LIGHT;
        }

        public static ThemeMode fromStoredValue(String value) {
            if (value == null || value.isBlank()) {
                return DARK;
            }
            try {
                return ThemeMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException _) {
                return DARK;
            }
        }
    }

    public UiPreferencesStore() {
        this(DEFAULT_NODE_PATH);
    }

    public UiPreferencesStore(String nodePath) {
        this(Preferences.userRoot().node(Objects.requireNonNull(nodePath, "nodePath cannot be null")));
    }

    UiPreferencesStore(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
    }

    public ThemeMode loadThemeMode() {
        return ThemeMode.fromStoredValue(preferences.get(THEME_MODE_KEY, ThemeMode.DARK.name()));
    }

    public void saveThemeMode(ThemeMode themeMode) {
        ThemeMode resolvedThemeMode = Objects.requireNonNull(themeMode, "themeMode cannot be null");
        preferences.put(THEME_MODE_KEY, resolvedThemeMode.name());
        flushQuietly();
    }

    public Set<String> loadSeenAchievementIds() {
        String raw = preferences.get(SEEN_ACHIEVEMENT_IDS_KEY, "");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void saveSeenAchievementIds(Set<String> achievementIds) {
        Set<String> resolvedIds = achievementIds == null ? Set.of() : achievementIds;
        String raw = resolvedIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
        preferences.put(SEEN_ACHIEVEMENT_IDS_KEY, raw);
        flushQuietly();
    }

    void clearThemeMode() {
        preferences.remove(THEME_MODE_KEY);
        flushQuietly();
    }

    private void flushQuietly() {
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to flush UI preferences", e);
            }
        }
    }
}
