package datingapp.ui.screen;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.ui.UiConstants;
import datingapp.ui.UiUtils;
import datingapp.ui.viewmodel.LoginViewModel;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class CreateAccountDialogFactory {

    private static final String DIALOG_TITLE = "Create New Account";
    private static final String BUTTON_CREATE = "Create";
    private static final String LABEL_NAME = "Name:";
    private static final String LABEL_AGE = "Age:";
    private static final String LABEL_GENDER = "Gender:";
    private static final String LABEL_INTERESTED_IN = "Interested In:";
    private static final String PROMPT_ENTER_NAME = "Enter your name";
    private static final int AGE_DEFAULT = 25;
    private static final String GENDER_OTHER_LABEL = "Other / Non-binary";

    private static final String STYLE_TITLE =
            "-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-text-primary;";
    private static final String STYLE_FIELD_BACKGROUND =
            "-fx-background-color: -fx-surface-elevated; -fx-text-fill: white; "
                    + "-fx-prompt-text-fill: -fx-text-muted; -fx-background-radius: 8;";
    private static final String STYLE_COMBO_BACKGROUND = "-fx-background-color: #1e293b; -fx-background-radius: 8;";
    private static final String STYLE_CELL_BACKGROUND = "-fx-background-color: #1e293b;";
    private static final String STYLE_CELL_WITH_TEXT =
            "-fx-background-color: #1e293b; -fx-text-fill: white; -fx-padding: 8 12;";
    private static final String STYLE_ERROR_LABEL = "-fx-text-fill: #ef4444; -fx-font-size: 13px;";
    private static final String SECONDARY_TEXT_STYLE = "-fx-text-fill: -fx-text-secondary;";

    private CreateAccountDialogFactory() {}

    static Dialog<User> create(StackPane owner, LoginViewModel viewModel) {
        Objects.requireNonNull(viewModel, "viewModel cannot be null");

        Dialog<User> dialog = UiUtils.createThemedDialog(owner, DIALOG_TITLE, null);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        ButtonType createButtonType = new ButtonType(BUTTON_CREATE, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(buildContent(viewModel));
        dialog.getDialogPane().setMinWidth(360);
        dialog.getDialogPane().setMinHeight(450);

        TextField nameField = lookupRequired(dialog, "#createAccountNameField", TextField.class);
        Spinner<Integer> ageSpinner = lookupSpinner(dialog, "#createAccountAgeSpinner");
        ComboBox<Gender> genderCombo = lookupGenderCombo(dialog, "#createAccountGenderCombo");
        ComboBox<Gender> interestedInCombo = lookupGenderCombo(dialog, "#createAccountInterestedInCombo");
        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);

        createButton.setDisable(true);
        nameField
                .textProperty()
                .addListener((obs, oldVal, newVal) ->
                        createButton.setDisable(newVal.trim().isEmpty()));
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.setResultConverter(dialogButton -> Objects.equals(dialogButton, createButtonType)
                ? viewModel.createUser(
                        nameField.getText().trim(),
                        ageSpinner.getValue(),
                        genderCombo.getValue(),
                        interestedInCombo.getValue())
                : null);
        return dialog;
    }

    private static VBox buildContent(LoginViewModel viewModel) {
        VBox content = new VBox(UiConstants.SPACING_XLARGE);
        content.setPadding(new Insets(
                UiConstants.PADDING_DIALOG_TOP,
                UiConstants.PADDING_DIALOG_HORIZONTAL,
                UiConstants.PADDING_XLARGE,
                UiConstants.PADDING_DIALOG_HORIZONTAL));
        UiUtils.applyDarkPanelStyle(content);

        Label titleLabel = new Label(DIALOG_TITLE);
        titleLabel.setStyle(STYLE_TITLE);

        TextField nameField = new TextField();
        nameField.setId("createAccountNameField");
        nameField.setPromptText(PROMPT_ENTER_NAME);
        nameField.setPrefWidth(280);
        nameField.setStyle(STYLE_FIELD_BACKGROUND);

        Spinner<Integer> ageSpinner = createAgeSpinner(viewModel);
        ageSpinner.setId("createAccountAgeSpinner");

        ComboBox<Gender> genderCombo = createGenderCombo();
        genderCombo.setId("createAccountGenderCombo");

        ComboBox<Gender> interestedInCombo = createGenderCombo();
        interestedInCombo.setId("createAccountInterestedInCombo");

        Label errorLabel = new Label();
        errorLabel.setStyle(STYLE_ERROR_LABEL);
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());

        content.getChildren()
                .addAll(
                        titleLabel,
                        labeledField(LABEL_NAME, nameField),
                        labeledField(LABEL_AGE, ageSpinner),
                        labeledField(LABEL_GENDER, genderCombo),
                        labeledField(LABEL_INTERESTED_IN, interestedInCombo),
                        errorLabel);
        return content;
    }

    private static Spinner<Integer> createAgeSpinner(LoginViewModel viewModel) {
        Spinner<Integer> ageSpinner = new Spinner<>();
        int ageMin = viewModel.getMinAge();
        int ageMax = viewModel.getMaxAge();
        SpinnerValueFactory<Integer> ageValueFactory = new SpinnerValueFactory<>() {
            @Override
            public void decrement(int steps) {
                int current = getValue() == null ? AGE_DEFAULT : getValue();
                setValue(Math.max(ageMin, current - steps));
            }

            @Override
            public void increment(int steps) {
                int current = getValue() == null ? AGE_DEFAULT : getValue();
                setValue(Math.min(ageMax, current + steps));
            }
        };
        ageValueFactory.setValue(AGE_DEFAULT);
        ageSpinner.setValueFactory(ageValueFactory);
        ageSpinner.setEditable(true);
        ageSpinner.setPrefWidth(120);
        return ageSpinner;
    }

    private static ComboBox<Gender> createGenderCombo() {
        ComboBox<Gender> combo = new ComboBox<>();
        combo.getItems().addAll(Gender.values());
        combo.setValue(Gender.OTHER);
        combo.setPrefWidth(200);
        combo.setStyle(STYLE_COMBO_BACKGROUND);
        combo.setCellFactory(list -> createStyledCell());
        combo.setButtonCell(createStyledCell());
        return combo;
    }

    private static VBox labeledField(String labelText, Node input) {
        VBox box = new VBox(8);
        Label label = new Label(labelText);
        label.setStyle(SECONDARY_TEXT_STYLE);
        box.getChildren().addAll(label, input);
        return box;
    }

    private static ListCell<Gender> createStyledCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Gender item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(STYLE_CELL_BACKGROUND);
                    return;
                }
                setText(
                        switch (item) {
                            case MALE -> "MALE";
                            case FEMALE -> "FEMALE";
                            case OTHER -> GENDER_OTHER_LABEL;
                        });
                setStyle(STYLE_CELL_WITH_TEXT);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T lookupRequired(Dialog<User> dialog, String selector, Class<T> type) {
        Node node = dialog.getDialogPane().lookup(selector);
        if (node == null || !type.isInstance(node)) {
            throw new IllegalStateException("Required dialog control not found: " + selector);
        }
        return (T) node;
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> lookupSpinner(Dialog<User> dialog, String selector) {
        return lookupRequired(dialog, selector, Spinner.class);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<Gender> lookupGenderCombo(Dialog<User> dialog, String selector) {
        return lookupRequired(dialog, selector, ComboBox.class);
    }
}
