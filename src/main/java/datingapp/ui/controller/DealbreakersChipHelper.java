package datingapp.ui.controller;

import java.util.Set;
import java.util.function.Function;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Helper for rendering and styling dealbreaker chips in the Profile controller. */
public final class DealbreakersChipHelper {

    private DealbreakersChipHelper() {
        // Utility class
    }

    public static <T extends Enum<T>> VBox createSection(
            String title, String subtitle, T[] values, Set<T> selected, Function<T, String> displayNameFunc) {
        VBox section = new VBox(8);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        FlowPane itemsPane = new FlowPane(8, 8);
        for (T value : values) {
            Button chip = new Button(displayNameFunc.apply(value));
            boolean isSelected = selected.contains(value);
            updateChipStyle(chip, isSelected);

            chip.setOnAction(event -> {
                event.consume();
                if (selected.contains(value)) {
                    selected.remove(value);
                    updateChipStyle(chip, false);
                } else {
                    selected.add(value);
                    updateChipStyle(chip, true);
                }
            });

            itemsPane.getChildren().add(chip);
        }

        section.getChildren().addAll(titleLabel, subtitleLabel, itemsPane);
        return section;
    }

    public static void updateChipStyle(Button chip, boolean selected) {
        if (selected) {
            chip.setStyle(
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 6 12;");
        } else {
            chip.setStyle(
                    "-fx-background-color: #334155; -fx-text-fill: #94a3b8; -fx-background-radius: 20; -fx-padding: 6 12;");
        }
    }
}
