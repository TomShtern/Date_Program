package datingapp.ui;

public final class UiStyles {

    private UiStyles() {}

    public static String getThemeUrl() {
        var url = UiStyles.class.getResource("/css/theme.css");
        if (url == null) {
            throw new NullPointerException("Resource /css/theme.css not found on classpath");
        }
        return url.toExternalForm();
    }
}
