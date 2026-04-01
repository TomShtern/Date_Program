package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.i18n.I18n;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.model.User;
import datingapp.ui.JavaFxTestSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kordamp.ikonli.javafx.FontIcon;

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

    @Test
    @DisplayName("showAchievement overloads populate fields and auto-dismiss can be disabled")
    void showAchievementOverloadsPopulateFieldsAndCanStayVisible() throws Exception {
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/achievement_popup.fxml", MilestonePopupController::new);
        MilestonePopupController controller = (MilestonePopupController) loaded.controller();
        StackPane host = JavaFxTestSupport.callOnFxAndWait(() -> new StackPane(loaded.root()));

        Label titleLabel = JavaFxTestSupport.lookup(loaded.root(), ".achievement-title", Label.class);
        Label nameLabel = JavaFxTestSupport.lookup(loaded.root(), ".achievement-name", Label.class);
        Label descriptionLabel = JavaFxTestSupport.lookup(loaded.root(), ".achievement-description", Label.class);
        Label xpLabel = JavaFxTestSupport.lookup(loaded.root(), ".achievement-xp-text", Label.class);
        FontIcon achievementIcon = JavaFxTestSupport.lookup(loaded.root(), ".achievement-icon", FontIcon.class);

        JavaFxTestSupport.runOnFxAndWait(() -> controller.setAutoDismiss(false));

        JavaFxTestSupport.runOnFxAndWait(
                () -> controller.showAchievement("mdi2n-note-text-outline", "Custom Badge", "Custom description", 123));

        assertEquals(I18n.text("ui.achievement.title"), JavaFxTestSupport.callOnFxAndWait(titleLabel::getText));
        assertEquals("Custom Badge", JavaFxTestSupport.callOnFxAndWait(nameLabel::getText));
        assertEquals("Custom description", JavaFxTestSupport.callOnFxAndWait(descriptionLabel::getText));
        assertEquals(I18n.text("ui.achievement.xp", 123), JavaFxTestSupport.callOnFxAndWait(xpLabel::getText));
        assertEquals("mdi2n-note-text-outline", JavaFxTestSupport.callOnFxAndWait(achievementIcon::getIconLiteral));

        Achievement achievement = Achievement.SUPERSTAR;
        JavaFxTestSupport.runOnFxAndWait(() -> controller.showAchievement(achievement));

        assertEquals(achievement.getDisplayName(), JavaFxTestSupport.callOnFxAndWait(nameLabel::getText));
        assertEquals(achievement.getDescription(), JavaFxTestSupport.callOnFxAndWait(descriptionLabel::getText));
        assertEquals(
                I18n.text("ui.achievement.xp", achievement.getXp()),
                JavaFxTestSupport.callOnFxAndWait(xpLabel::getText));
        assertEquals(achievement.getIconLiteral(), JavaFxTestSupport.callOnFxAndWait(achievementIcon::getIconLiteral));

        assertFalse(JavaFxTestSupport.waitUntil(() -> false, 5600));

        assertTrue(JavaFxTestSupport.callOnFxAndWait(() -> host.getChildren().contains(loaded.root())));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(() -> loaded.root().getParent() == host));
    }

    @Test
    @DisplayName("setMatchedUser and callbacks run without popup chrome")
    void setMatchedUserAndCallbacksRunWithoutPopupChrome() throws Exception {
        MilestonePopupController controller = new MilestonePopupController();
        Label matchMessage = new Label();
        setField(controller, "matchMessage", matchMessage);

        User currentUser = new User(UUID.randomUUID(), "Current User");
        User matchedUser = new User(UUID.randomUUID(), "Matched User");

        AtomicInteger closeCount = new AtomicInteger();
        AtomicInteger messageCount = new AtomicInteger();
        AtomicInteger continueCount = new AtomicInteger();

        controller.setOnClose(closeCount::incrementAndGet);
        controller.setOnMessage(messageCount::incrementAndGet);
        controller.setOnContinue(continueCount::incrementAndGet);

        controller.setMatchedUser(currentUser, matchedUser);

        assertEquals(I18n.text("ui.match.popup.message", matchedUser.getName()), matchMessage.getText());

        invokeNoArgMethod(controller, "handleMessage");
        assertEquals(1, closeCount.get());
        assertEquals(1, messageCount.get());
        assertEquals(0, continueCount.get());

        invokeNoArgMethod(controller, "handleContinue");
        assertEquals(2, closeCount.get());
        assertEquals(1, messageCount.get());
        assertEquals(1, continueCount.get());

        invokeNoArgMethod(controller, "handleClose");
        assertEquals(3, closeCount.get());
        assertEquals(1, messageCount.get());
        assertEquals(1, continueCount.get());
    }

    @Test
    @DisplayName("manual close cancels auto-dismiss timer")
    void manualCloseCancelsAutoDismissTimer() throws Exception {
        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/achievement_popup.fxml", MilestonePopupController::new);
        MilestonePopupController controller = (MilestonePopupController) loaded.controller();

        JavaFxTestSupport.runOnFxAndWait(
                () -> controller.showAchievement("mdi2n-note-text-outline", "Badge", "Description", 10));

        PauseTransition autoDismiss = (PauseTransition) getField(controller, "autoDismissTransition");
        assertNotNull(autoDismiss);
        assertEquals(Animation.Status.RUNNING, JavaFxTestSupport.callOnFxAndWait(autoDismiss::getStatus));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            try {
                invokeNoArgMethod(controller, "handleClose");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });

        assertEquals(null, getField(controller, "autoDismissTransition"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = MilestonePopupController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = MilestonePopupController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invokeNoArgMethod(Object target, String methodName) throws Exception {
        Method method = MilestonePopupController.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }
}
