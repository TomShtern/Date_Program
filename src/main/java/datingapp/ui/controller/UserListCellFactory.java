package datingapp.ui.controller;

import datingapp.core.AppClock;
import datingapp.core.ProfileCompletionService;
import datingapp.core.User;
import datingapp.core.User.UserState;
import datingapp.ui.util.UiSupport;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Factory for building styled user list cells on the login screen. */
public final class UserListCellFactory {
    private static final String TEXT_UNKNOWN = "Unknown";
    private static final String TEXT_ACTIVE_RECENTLY = "Active recently";
    private static final String TEXT_ACTIVE_JUST_NOW = "Active just now";
    private static final String TEXT_VERIFIED = " • Verified";
    private static final String TEXT_PROFILE_PREFIX = "Profile ";
    private static final String TEXT_PERCENT_SUFFIX = "%";
    private static final String TEXT_ACTIVE_PREFIX = "Active ";
    private static final String TEXT_MINUTES_SUFFIX = "m ago";
    private static final String TEXT_HOURS_SUFFIX = "h ago";
    private static final String TEXT_DAYS_SUFFIX = "d ago";
    private static final String TEXT_WEEKS_SUFFIX = "w ago";
    private static final String TEXT_MONTHS_SUFFIX = "mo ago";

    private static final String CSS_LOGIN_AVATAR_CONTAINER = "login-avatar-container";
    private static final String CSS_LOGIN_USER_CELL = "login-user-cell";
    private static final String CSS_LOGIN_USER_NAME = "login-user-name";
    private static final String CSS_TEXT_SECONDARY = "text-secondary";
    private static final String CSS_LOGIN_USER_DETAILS = "login-user-details";
    private static final String CSS_LOGIN_BADGE_ROW = "login-badge-row";
    private static final String CSS_LOGIN_BADGE = "login-badge";
    private static final String CSS_LOGIN_BADGE_PRIMARY = "login-badge-primary";
    private static final String CSS_LOGIN_BADGE_MUTED = "login-badge-muted";
    private static final String CSS_LOGIN_BADGE_SUCCESS = "login-badge-success";
    private static final String CSS_LOGIN_BADGE_WARNING = "login-badge-warning";

    private UserListCellFactory() {
        // Utility class
    }

    public static ListCell<User> create(ProfileCompletionService profileCompletionService) {
        return new UserListCell(profileCompletionService);
    }

    /** Custom list cell for displaying user accounts with avatars and badges. */
    private static class UserListCell extends ListCell<User> {
        private static final double AVATAR_SIZE = 44;
        private static final double SELECT_SCALE = 1.03;

        private final ProfileCompletionService profileCompletionService;
        private final HBox container = new HBox(15);
        private final StackPane avatarContainer = new StackPane();
        private final ImageView avatarView = new ImageView();
        private final Circle avatarClip = new Circle(AVATAR_SIZE / 2);
        private final VBox textBox = new VBox(2);
        private final Label nameLabel = new Label();
        private final Label detailsLabel = new Label();
        private final HBox badgeRow = new HBox(6);
        private final Label completionBadge = new Label();
        private final Label activityBadge = new Label();

        private UserListCell(ProfileCompletionService profileCompletionService) {
            this.profileCompletionService = profileCompletionService;
            avatarView.setFitWidth(AVATAR_SIZE);
            avatarView.setFitHeight(AVATAR_SIZE);
            avatarView.setPreserveRatio(true);
            avatarClip.setCenterX(AVATAR_SIZE / 2);
            avatarClip.setCenterY(AVATAR_SIZE / 2);
            avatarView.setClip(avatarClip);

            avatarContainer.getStyleClass().add(CSS_LOGIN_AVATAR_CONTAINER);
            avatarContainer.getChildren().add(avatarView);

            container.getStyleClass().add(CSS_LOGIN_USER_CELL);
            nameLabel.getStyleClass().add(CSS_LOGIN_USER_NAME);
            detailsLabel.getStyleClass().addAll(CSS_TEXT_SECONDARY, CSS_LOGIN_USER_DETAILS);

            badgeRow.getStyleClass().add(CSS_LOGIN_BADGE_ROW);
            completionBadge.getStyleClass().addAll(CSS_LOGIN_BADGE, CSS_LOGIN_BADGE_PRIMARY);
            activityBadge.getStyleClass().addAll(CSS_LOGIN_BADGE, CSS_LOGIN_BADGE_MUTED);
            badgeRow.getChildren().addAll(completionBadge, activityBadge);

            textBox.getChildren().addAll(nameLabel, detailsLabel, badgeRow);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(8, 12, 8, 12));
            container.getChildren().addAll(avatarContainer, textBox);

            selectedProperty().addListener((obs, oldVal, newVal) -> animateSelection(newVal));
        }

        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setText(null);
                setGraphic(null);
                container.setScaleX(1.0);
                container.setScaleY(1.0);
            } else {
                setText(null);
                nameLabel.setText(user.getName() + ", " + user.getAge());

                StringBuilder sb = new StringBuilder(formatState(user.getState()));
                if (user.isVerified()) {
                    sb.append(TEXT_VERIFIED);
                }
                detailsLabel.setText(sb.toString());

                updateCompletionBadge(user);
                activityBadge.setText(formatActivity(user.getUpdatedAt()));
                avatarView.setImage(UiSupport.getAvatar(resolveAvatarPath(user), AVATAR_SIZE));

                setGraphic(container);
            }
        }

        private void updateCompletionBadge(User user) {
            ProfileCompletionService.CompletionResult result = profileCompletionService.calculate(user);
            int score = result.score();
            completionBadge.setText(TEXT_PROFILE_PREFIX + score + TEXT_PERCENT_SUFFIX);

            completionBadge
                    .getStyleClass()
                    .removeAll(CSS_LOGIN_BADGE_PRIMARY, CSS_LOGIN_BADGE_SUCCESS, CSS_LOGIN_BADGE_WARNING);
            if (score >= 90) {
                completionBadge.getStyleClass().add(CSS_LOGIN_BADGE_SUCCESS);
            } else if (score >= 60) {
                completionBadge.getStyleClass().add(CSS_LOGIN_BADGE_PRIMARY);
            } else {
                completionBadge.getStyleClass().add(CSS_LOGIN_BADGE_WARNING);
            }
        }

        private void animateSelection(boolean selected) {
            double target = selected ? SELECT_SCALE : 1.0;
            ScaleTransition transition = new ScaleTransition(Duration.millis(140), container);
            transition.setInterpolator(Interpolator.EASE_OUT);
            transition.setToX(target);
            transition.setToY(target);
            transition.play();
        }

        private static String resolveAvatarPath(User user) {
            List<String> urls = user.getPhotoUrls();
            if (urls == null || urls.isEmpty()) {
                return null;
            }
            String first = urls.get(0);
            if (first == null || first.isBlank()) {
                return null;
            }
            if (first.startsWith("placeholder://")) {
                return null;
            }
            return first;
        }

        private static String formatState(UserState state) {
            if (state == null) {
                return TEXT_UNKNOWN;
            }
            String raw = state.name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }

        private static String formatActivity(Instant updatedAt) {
            if (updatedAt == null) {
                return TEXT_ACTIVE_RECENTLY;
            }

            java.time.Duration duration = java.time.Duration.between(updatedAt, AppClock.now());
            if (duration.isNegative()) {
                duration = java.time.Duration.ZERO;
            }

            long minutes = duration.toMinutes();
            if (minutes < 1) {
                return TEXT_ACTIVE_JUST_NOW;
            }
            if (minutes < 60) {
                return TEXT_ACTIVE_PREFIX + minutes + TEXT_MINUTES_SUFFIX;
            }

            long hours = duration.toHours();
            if (hours < 24) {
                return TEXT_ACTIVE_PREFIX + hours + TEXT_HOURS_SUFFIX;
            }

            long days = duration.toDays();
            if (days < 7) {
                return TEXT_ACTIVE_PREFIX + days + TEXT_DAYS_SUFFIX;
            }

            long weeks = days / 7;
            if (weeks < 5) {
                return TEXT_ACTIVE_PREFIX + weeks + TEXT_WEEKS_SUFFIX;
            }

            long months = days / 30;
            return TEXT_ACTIVE_PREFIX + months + TEXT_MONTHS_SUFFIX;
        }
    }
}
