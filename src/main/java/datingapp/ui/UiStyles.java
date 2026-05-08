package datingapp.ui;

public final class UiStyles {

    private UiStyles() {}

    public static String getThemeUrl() {
        return UiStyles.class.getResource("/css/theme.css").toExternalForm();
    }
}
