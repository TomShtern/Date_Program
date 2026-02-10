package datingapp.ui.controller;

import datingapp.ui.viewmodel.MatchesViewModel;
import datingapp.ui.viewmodel.MatchesViewModel.LikeCardData;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

/** Renders the Likes tab cards for MatchesController. */
public final class LikesTabRenderer {
    private static final String ICON_HEART = "mdi2h-heart";
    private static final String COLOR_SLATE = "#e2e8f0";

    private final MatchesViewModel viewModel;

    public LikesTabRenderer(MatchesViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel cannot be null");
    }

    public VBox createIncomingLikeCard(LikeCardData like) {
        VBox card = buildLikeCardBase(like, "Liked you ");
        card.getStyleClass().add("like-card-received");

        Button likeBackBtn = new Button("Like back");
        likeBackBtn.getStyleClass().add("like-action-primary");
        FontIcon likeIcon = new FontIcon(ICON_HEART);
        likeIcon.setIconSize(14);
        likeIcon.setIconColor(Color.WHITE);
        likeBackBtn.setGraphic(likeIcon);
        likeBackBtn.setOnAction(event -> {
            event.consume();
            viewModel.likeBack(like);
        });

        Button passBtn = new Button("Pass");
        passBtn.getStyleClass().add("like-action-secondary");
        FontIcon passIcon = new FontIcon("mdi2c-close");
        passIcon.setIconSize(14);
        passIcon.setIconColor(Color.web(COLOR_SLATE));
        passBtn.setGraphic(passIcon);
        passBtn.setOnAction(event -> {
            event.consume();
            viewModel.passOn(like);
        });

        HBox actions = new HBox(10, likeBackBtn, passBtn);
        actions.getStyleClass().add("like-action-row");

        card.getChildren().add(actions);
        return card;
    }

    public VBox createOutgoingLikeCard(LikeCardData like) {
        VBox card = buildLikeCardBase(like, "You liked ");
        card.getStyleClass().add("like-card-sent");

        Label status = new Label("Pending reply");
        status.getStyleClass().add("like-status-label");

        Button withdrawBtn = new Button("Withdraw");
        withdrawBtn.getStyleClass().add("like-action-secondary");
        FontIcon withdrawIcon = new FontIcon("mdi2a-arrow-left");
        withdrawIcon.setIconSize(14);
        withdrawIcon.setIconColor(Color.web(COLOR_SLATE));
        withdrawBtn.setGraphic(withdrawIcon);
        withdrawBtn.setOnAction(event -> {
            event.consume();
            viewModel.withdrawLike(like);
        });

        HBox actions = new HBox(10, status, withdrawBtn);
        actions.getStyleClass().add("like-action-row");

        card.getChildren().add(actions);
        return card;
    }

    private VBox buildLikeCardBase(LikeCardData like, String timePrefix) {
        VBox card = new VBox(12);
        card.getStyleClass().add("like-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(240);
        card.setPadding(new Insets(24, 20, 24, 20));

        StackPane avatarContainer = new StackPane();
        avatarContainer.getStyleClass().add("like-avatar-container");
        FontIcon avatarIcon = new FontIcon("mdi2a-account");
        avatarIcon.setIconSize(34);
        avatarIcon.setIconColor(Color.web(COLOR_SLATE));
        avatarIcon.getStyleClass().add("like-avatar-icon");
        avatarContainer.getChildren().add(avatarIcon);

        Label nameLabel = new Label(like.userName() + ", " + like.age());
        nameLabel.getStyleClass().add("like-user-name");

        Label bioLabel = new Label(like.bioSnippet());
        bioLabel.getStyleClass().add("like-bio-label");
        bioLabel.setWrapText(true);
        bioLabel.setMaxWidth(200);

        Label timeLabel = new Label(timePrefix + like.likedTimeAgo());
        timeLabel.getStyleClass().add("like-time-label");

        card.getChildren().addAll(avatarContainer, nameLabel, bioLabel, timeLabel);
        return card;
    }
}
