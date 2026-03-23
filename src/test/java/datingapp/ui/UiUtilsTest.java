package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("UiUtils helper tests")
class UiUtilsTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("setLabelMessage toggles label visibility and managed state")
    void setLabelMessageTogglesVisibilityAndManagedState() {
        Label label = new Label();

        UiUtils.setLabelMessage(label, "Invalid value");

        assertEquals("Invalid value", label.getText());
        assertTrue(label.isVisible());
        assertTrue(label.isManaged());

        UiUtils.setLabelMessage(label, "   ");

        assertEquals("", label.getText());
        assertFalse(label.isVisible());
        assertFalse(label.isManaged());
    }

    @Test
    @DisplayName("createThemedDialog applies the shared theme stylesheet")
    void createThemedDialogAppliesThemeStylesheet() {
        Dialog<ButtonType> dialog;
        try {
            dialog = JavaFxTestSupport.callOnFxAndWait(
                    () -> UiUtils.createThemedDialog(null, "Profile Preview", "Header text"));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        assertEquals("Profile Preview", dialog.getTitle());
        assertEquals("Header text", dialog.getHeaderText());
        assertNotNull(dialog.getDialogPane());
        assertTrue(dialog.getDialogPane().getStylesheets().stream().anyMatch(style -> style.endsWith("theme.css")));
    }

    @Test
    @DisplayName("configureEnumComboBox populates values and display conversion")
    void configureEnumComboBoxPopulatesValuesAndDisplayConversion() {
        ComboBox<TestChoice> comboBox = new ComboBox<>();

        UiUtils.configureEnumComboBox(comboBox, TestChoice.values(), TestChoice::getDisplayName);

        assertEquals(List.of(TestChoice.values()), comboBox.getItems());
        assertEquals("Alpha", comboBox.getConverter().toString(TestChoice.ALPHA));
        assertNotNull(comboBox.getButtonCell());
    }

    @Test
    @DisplayName("applyDarkPanelStyle applies the shared dark panel background")
    void applyDarkPanelStyleAppliesSharedStyle() {
        VBox panel = new VBox();

        UiUtils.applyDarkPanelStyle(panel);

        assertEquals("-fx-background-color: #1e293b;", panel.getStyle());
    }

    private enum TestChoice {
        ALPHA("Alpha"),
        BETA("Beta");

        private final String displayName;

        TestChoice(String displayName) {
            this.displayName = displayName;
        }

        String getDisplayName() {
            return displayName;
        }
    }
}
