package datingapp.ui;

import java.net.URL;
import java.util.Set;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.util.StringConverter;

/**
 * Utility class for common JavaFX UI operations.
 * Helps reduce controller bloat and ensures consistent styling.
 */
public final class UiUtils {

    private static final String DARK_PANEL_STYLE = "-fx-background-color: #1e293b;";

    private UiUtils() {
        // Prevent instantiation
    }

    /**
     * Creates a dialog with the current theme stylesheet applied.
     */
    public static <T> Dialog<T> createThemedDialog(Node ownerNode, String title, String headerText) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);
        dialog.initModality(Modality.WINDOW_MODAL);

        if (ownerNode != null
                && ownerNode.getScene() != null
                && ownerNode.getScene().getWindow() != null) {
            dialog.initOwner(ownerNode.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().setAll(ownerNode.getScene().getStylesheets());
        } else {
            URL themeUrl = UiUtils.class.getResource("/css/theme.css");
            if (themeUrl != null) {
                dialog.getDialogPane().getStylesheets().add(themeUrl.toExternalForm());
            }
        }

        return dialog;
    }

    /**
     * Applies the shared dark panel background style used by profile dialogs.
     */
    public static void applyDarkPanelStyle(Region region) {
        if (region != null) {
            region.setStyle(DARK_PANEL_STYLE);
        }
    }

    /**
     * Updates a label's message and visibility state in one step.
     */
    public static void setLabelMessage(Label label, String message) {
        if (label == null) {
            return;
        }

        boolean visible = message != null && !message.isBlank();
        label.setText(visible ? message : "");
        label.setManaged(visible);
        label.setVisible(visible);
    }

    /**
     * Creates a compact secondary label with the standard muted text style.
     */
    public static Label createSecondaryLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-secondary");
        label.setWrapText(true);
        return label;
    }

    /**
     * Creates a bold section label used for selection dialogs.
     */
    public static Label createSectionTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");
        return label;
    }

    /**
     * Configures an enum ComboBox with standard enum display behavior.
     */
    public static <T extends Enum<T>> void configureEnumComboBox(
            ComboBox<T> comboBox, T[] values, Function<T, String> displayNameFunc) {
        if (comboBox == null) {
            return;
        }

        comboBox.setItems(FXCollections.observableArrayList(values));
        comboBox.setConverter(createEnumStringConverter(displayNameFunc));
        comboBox.setButtonCell(createDisplayCell(displayNameFunc));
    }

    /**
     * Creates a StringConverter for enum types using a display name function.
     */
    public static <T extends Enum<T>> StringConverter<T> createEnumStringConverter(
            Function<T, String> displayNameFunc) {
        return new StringConverter<>() {
            @Override
            public String toString(T object) {
                return object == null ? "" : displayNameFunc.apply(object);
            }

            @Override
            public T fromString(String string) {
                return null; // Not needed for ComboBox
            }
        };
    }

    /**
     * Creates a ListCell that displays enum values using their display name.
     */
    public static <T extends Enum<T>> ListCell<T> createDisplayCell(Function<T, String> displayNameFunc) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayNameFunc.apply(item));
            }
        };
    }

    /**
     * Updates the style of a toggle button depending on its selection state.
     */
    public static void updateToggleStyle(Button btn, boolean selected, String baseStyle) {
        if (selected) {
            btn.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; " + baseStyle);
        } else {
            btn.setStyle("-fx-background-color: #334155; -fx-text-fill: #94a3b8; " + baseStyle);
        }
    }

    /**
     * Updates the style of a chip button (used for interests or dealbreakers).
     */
    public static void updateChipStyle(Button chip, boolean selected, String selectedColor) {
        if (selected) {
            chip.setStyle("-fx-background-color: " + selectedColor
                    + "; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 6 12;");
        } else {
            chip.setStyle(
                    "-fx-background-color: #334155; -fx-text-fill: #94a3b8; -fx-background-radius: 20; -fx-padding: 6 12;");
        }
    }

    /**
     * Creates a UI section for dealbreakers or similar multiple-choice properties.
     */
    public static <T extends Enum<T>> VBox createSelectionSection(
            String title,
            String subtitle,
            T[] values,
            Set<T> selected,
            Function<T, String> displayNameFunc,
            String selectedColor) {
        VBox section = new VBox(8);

        Label titleLabel = createSectionTitleLabel(title);

        Label subtitleLabel = createSecondaryLabel(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 12px;");
        subtitleLabel.setWrapText(false);

        FlowPane itemsPane = new FlowPane(8, 8);
        for (T value : values) {
            Button chip = new Button(displayNameFunc.apply(value));
            boolean isSelected = selected.contains(value);
            updateChipStyle(chip, isSelected, selectedColor);

            chip.setOnAction(event -> {
                event.consume();
                if (selected.contains(value)) {
                    selected.remove(value);
                    updateChipStyle(chip, false, selectedColor);
                } else {
                    selected.add(value);
                    updateChipStyle(chip, true, selectedColor);
                }
            });

            itemsPane.getChildren().add(chip);
        }

        section.getChildren().addAll(titleLabel, subtitleLabel, itemsPane);
        return section;
    }
}
