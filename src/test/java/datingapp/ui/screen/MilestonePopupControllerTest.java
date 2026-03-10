package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.i18n.I18n;
import datingapp.ui.JavaFxTestSupport;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class MilestonePopupControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("achievement popup fxml loads with localized title text")
    void achievementPopupFxmlLoadsWithLocalizedTitleText() throws InterruptedException {
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/achievement_popup.fxml", MilestonePopupController::new);

        Label titleLabel = JavaFxTestSupport.lookup(loaded.root(), ".achievement-title", Label.class);
        assertEquals(I18n.text("ui.achievement.title"), titleLabel.getText());
    }
}
