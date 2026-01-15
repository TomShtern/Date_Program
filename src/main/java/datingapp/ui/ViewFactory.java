package datingapp.ui;

/**
 * Enum defining available views and their FXML resource paths.
 */
public class ViewFactory {

    public enum ViewType {
        LOGIN("/fxml/login.fxml"),
        DASHBOARD("/fxml/dashboard.fxml"),
        PROFILE("/fxml/profile.fxml"),
        MATCHING("/fxml/matching.fxml"),
        MATCHES("/fxml/matches.fxml"),
        CHAT("/fxml/chat.fxml"),
        STATS("/fxml/stats.fxml"),
        PREFERENCES("/fxml/preferences.fxml");

        private final String fxmlPath;

        ViewType(String fxmlPath) {
            this.fxmlPath = fxmlPath;
        }

        public String getFxmlPath() {
            return fxmlPath;
        }
    }
}
