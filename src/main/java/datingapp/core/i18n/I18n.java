package datingapp.core.i18n;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/** Shared localization access for CLI and JavaFX flows. */
public final class I18n {

    private static final String BUNDLE_NAME = "i18n.messages";

    private I18n() {}

    public static ResourceBundle bundle() {
        return bundle(Locale.getDefault());
    }

    public static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNull(locale, "locale cannot be null"));
    }

    public static String text(String key, Object... args) {
        return text(bundle(), key, args);
    }

    public static String text(ResourceBundle bundle, String key, Object... args) {
        Objects.requireNonNull(bundle, "bundle cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        String pattern = bundle.getString(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return String.format(resolveLocale(bundle), pattern, args);
    }

    private static Locale resolveLocale(ResourceBundle bundle) {
        Locale locale = bundle.getLocale();
        return locale == null || Locale.ROOT.equals(locale) ? Locale.getDefault() : locale;
    }
}
