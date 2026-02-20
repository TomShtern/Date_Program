package datingapp.ui;

import java.util.Set;
import java.util.function.Function;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Utility class for common JavaFX UI operations.
 * Helps reduce controller bloat and ensures consistent styling.
 */
public final class UiUtils {

    private UiUtils() {
        // Prevent instantiation
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

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

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
