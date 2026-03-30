package datingapp.ui;

import datingapp.ui.UiPreferencesStore.ThemeMode;
import java.util.Objects;

/** ViewModel-facing theme façade that owns theme persistence and application. */
public class UiThemeService {

    private final UiPreferencesStore preferencesStore;
    private final NavigationService navigationService;

    protected UiThemeService() {
        this.preferencesStore = null;
        this.navigationService = null;
    }

    public UiThemeService(UiPreferencesStore preferencesStore, NavigationService navigationService) {
        this.preferencesStore = Objects.requireNonNull(preferencesStore, "preferencesStore cannot be null");
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService cannot be null");
    }

    public ThemeMode loadThemeMode() {
        ensureInitialized();
        return preferencesStore.loadThemeMode();
    }

    public ThemeMode getCurrentThemeMode() {
        ensureInitialized();
        return navigationService.getCurrentThemeMode();
    }

    public void setThemeMode(ThemeMode themeMode) {
        ensureInitialized();
        ThemeMode resolvedThemeMode = Objects.requireNonNull(themeMode, "themeMode cannot be null");
        preferencesStore.saveThemeMode(resolvedThemeMode);
        navigationService.setThemeMode(resolvedThemeMode);
    }

    private void ensureInitialized() {
        if (preferencesStore == null || navigationService == null) {
            throw new IllegalStateException("UiThemeService has not been initialized");
        }
    }
}
