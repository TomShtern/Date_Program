# Phase 4: Major Features — Matching Experience

> **Enhancements**: E01 (Match Popup), E03 (Card Stack), E07 (Swipe Gestures), E08 (Undo Animation)
> **Duration**: ~5 hours
> **Status**: [ ] Not Started
> **Prerequisites**: Phase 1, Phase 2, Phase 3 Complete

---

## Goal

Create a premium Tinder-like matching experience with swipe gestures, card stacking, and celebration animations.

---

## Deliverables

| Type | File | Description |
|------|------|-------------|
| [NEW] | `fxml/match_popup.fxml` | "It's a Match!" celebration dialog |
| [NEW] | `controller/MatchPopupController.java` | Match popup controller |
| [NEW] | `ui/util/ConfettiAnimation.java` | Confetti particle effect |
| [MOD] | `matching.fxml` | Card stack structure |
| [MOD] | `MatchingController.java` | Swipe gestures + undo animation |
| [MOD] | `theme.css` | Card stack styles |

---

## E01: "It's a Match!" Celebration Animation

**Description**: Display an animated modal with avatars, confetti, and celebratory message when mutual like occurs.

### FXML: `match_popup.fxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.canvas.Canvas?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.image.ImageView?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<?import org.kordamp.ikonli.javafx.FontIcon?>

<StackPane fx:id="rootPane" styleClass="match-popup-overlay"
    xmlns="http://javafx.com/javafx/21" xmlns:fx="http://javafx.com/fxml/1"
    fx:controller="datingapp.ui.controller.MatchPopupController">

    <!-- Confetti Canvas (background) -->
    <Canvas fx:id="confettiCanvas" />

    <!-- Content -->
    <VBox alignment="CENTER" spacing="30" maxWidth="450">
        <padding>
            <Insets top="60" right="40" bottom="60" left="40"/>
        </padding>

        <!-- Avatars + Heart -->
        <HBox alignment="CENTER" spacing="30">
            <StackPane styleClass="match-avatar-wrapper">
                <ImageView fx:id="leftAvatar" fitWidth="100" fitHeight="100" preserveRatio="true"/>
                <FontIcon iconLiteral="mdi2a-account" iconSize="48" iconColor="white"
                          visible="${leftAvatar.image == null}"/>
            </StackPane>

            <FontIcon fx:id="heartIcon" iconLiteral="mdi2h-heart" iconSize="64"
                      styleClass="match-heart"/>

            <StackPane styleClass="match-avatar-wrapper">
                <ImageView fx:id="rightAvatar" fitWidth="100" fitHeight="100" preserveRatio="true"/>
                <FontIcon iconLiteral="mdi2a-account" iconSize="48" iconColor="white"
                          visible="${rightAvatar.image == null}"/>
            </StackPane>
        </HBox>

        <!-- Title -->
        <Label text="It's a Match!" styleClass="match-title"/>

        <!-- Subtitle -->
        <Label fx:id="matchMessage" styleClass="match-subtitle"
               text="You and Sarah liked each other!"/>

        <!-- Action Buttons -->
        <VBox spacing="12" alignment="CENTER">
            <Button text="Send Message" onAction="#handleMessage" styleClass="match-message-btn" maxWidth="280">
                <graphic>
                    <FontIcon iconLiteral="mdi2c-chat" iconSize="20" iconColor="white"/>
                </graphic>
            </Button>
            <Button text="Keep Browsing" onAction="#handleContinue" styleClass="match-continue-btn"/>
        </VBox>
    </VBox>
</StackPane>
```

### Controller: `MatchPopupController.java`

```java
package datingapp.ui.controller;

import datingapp.core.model.User;
import datingapp.ui.util.AnimationHelper;
import datingapp.ui.util.ConfettiAnimation;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

public class MatchPopupController {
    @FXML private StackPane rootPane;
    @FXML private Canvas confettiCanvas;
    @FXML private ImageView leftAvatar;
    @FXML private ImageView rightAvatar;
    @FXML private FontIcon heartIcon;
    @FXML private Label matchMessage;

    private ConfettiAnimation confetti;
    private Runnable onMessageCallback;
    private Runnable onContinueCallback;

    @FXML
    public void initialize() {
        // Size canvas to parent
        confettiCanvas.widthProperty().bind(rootPane.widthProperty());
        confettiCanvas.heightProperty().bind(rootPane.heightProperty());
    }

    public void setMatchedUser(User currentUser, User matchedUser) {
        matchMessage.setText("You and " + matchedUser.getName() + " liked each other!");
        // Set avatar images if available

        playEntranceAnimation();
    }

    private void playEntranceAnimation() {
        // Fade in overlay
        rootPane.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), rootPane);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Avatars fly in from sides
        leftAvatar.setTranslateX(-200);
        rightAvatar.setTranslateX(200);

        TranslateTransition leftFly = new TranslateTransition(Duration.millis(400), leftAvatar);
        leftFly.setToX(0);
        leftFly.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition rightFly = new TranslateTransition(Duration.millis(400), rightAvatar);
        rightFly.setToX(0);
        rightFly.setInterpolator(Interpolator.EASE_OUT);

        // Heart pulse
        heartIcon.setScaleX(0);
        heartIcon.setScaleY(0);
        ScaleTransition heartPop = new ScaleTransition(Duration.millis(300), heartIcon);
        heartPop.setToX(1.2);
        heartPop.setToY(1.2);
        heartPop.setDelay(Duration.millis(300));
        heartPop.setOnFinished(e -> {
            ScaleTransition settle = new ScaleTransition(Duration.millis(150), heartIcon);
            settle.setToX(1);
            settle.setToY(1);
            settle.play();
        });

        // Start confetti
        confetti = new ConfettiAnimation();
        confetti.play(confettiCanvas);

        new ParallelTransition(fadeIn, leftFly, rightFly, heartPop).play();
    }

    @FXML
    private void handleMessage() {
        close();
        if (onMessageCallback != null) onMessageCallback.run();
    }

    @FXML
    private void handleContinue() {
        close();
        if (onContinueCallback != null) onContinueCallback.run();
    }

    private void close() {
        if (confetti != null) confetti.stop();

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootPane);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            ((StackPane) rootPane.getParent()).getChildren().remove(rootPane);
        });
        fadeOut.play();
    }

    public void setOnMessage(Runnable callback) { this.onMessageCallback = callback; }
    public void setOnContinue(Runnable callback) { this.onContinueCallback = callback; }
}
```

### CSS Styles

```css
/* ============================================
   Match Popup Styles
   ============================================ */
.match-popup-overlay {
    -fx-background-color: rgba(0, 0, 0, 0.9);
}

.match-avatar-wrapper {
    -fx-min-width: 100;
    -fx-min-height: 100;
    -fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2);
    -fx-background-radius: 50;
    -fx-alignment: center;
}

.match-title {
    -fx-font-size: 48px;
    -fx-font-weight: bold;
    -fx-text-fill: white;
    -fx-effect: dropshadow(gaussian, rgba(244, 63, 94, 0.8), 25, 0.6, 0, 0);
}

.match-subtitle {
    -fx-font-size: 16px;
    -fx-text-fill: #94a3b8;
}

.match-heart {
    -fx-icon-color: #f43f5e;
    -fx-effect: dropshadow(gaussian, rgba(244, 63, 94, 0.8), 30, 0.6, 0, 0);
}

.match-message-btn {
    -fx-background-color: linear-gradient(to right, #f43f5e, #a855f7);
    -fx-text-fill: white;
    -fx-font-size: 16px;
    -fx-font-weight: bold;
    -fx-padding: 16 40;
    -fx-background-radius: 28;
}

.match-continue-btn {
    -fx-background-color: transparent;
    -fx-text-fill: #94a3b8;
    -fx-font-size: 14px;
}

.match-continue-btn:hover {
    -fx-text-fill: white;
}
```

---

## E03: Card Stack Effect

**Description**: Show 2-3 cards stacked behind the current candidate card.

### Modified `matching.fxml` Structure

```xml
<StackPane fx:id="cardStackContainer" alignment="CENTER">
    <!-- Background card 3 (furthest back) -->
    <VBox fx:id="card3" styleClass="candidate-card, card-stack-3"
          visible="false" managed="false"/>

    <!-- Background card 2 -->
    <VBox fx:id="card2" styleClass="candidate-card, card-stack-2"
          visible="false" managed="false"/>

    <!-- Active card (front) -->
    <VBox fx:id="candidateCard" styleClass="candidate-card, card-stack-1">
        <!-- Card content here -->
    </VBox>
</StackPane>
```

### CSS Styles

```css
/* ============================================
   Card Stack Effect
   ============================================ */
.card-stack-3 {
    -fx-scale-x: 0.88;
    -fx-scale-y: 0.88;
    -fx-translate-y: -24px;
    -fx-opacity: 0.4;
}

.card-stack-2 {
    -fx-scale-x: 0.94;
    -fx-scale-y: 0.94;
    -fx-translate-y: -12px;
    -fx-opacity: 0.7;
}

.card-stack-1 {
    -fx-scale-x: 1.0;
    -fx-scale-y: 1.0;
    -fx-translate-y: 0px;
    -fx-opacity: 1.0;
}
```

---

## E07: Swipe Gestures

**Description**: Allow drag-to-swipe candidate cards left (pass) or right (like).

### Implementation in MatchingController

```java
private double startX, startY;
private static final double DRAG_THRESHOLD = 150;

private void setupSwipeGestures() {
    candidateCard.setOnMousePressed(e -> {
        startX = e.getSceneX();
        startY = e.getSceneY();
        candidateCard.setCursor(Cursor.CLOSED_HAND);
    });

    candidateCard.setOnMouseDragged(e -> {
        double deltaX = e.getSceneX() - startX;
        double rotation = deltaX * 0.04;

        candidateCard.setTranslateX(deltaX);
        candidateCard.setRotate(rotation);

        // Update overlay opacity based on drag distance
        double progress = Math.min(Math.abs(deltaX) / DRAG_THRESHOLD, 1.0);

        if (deltaX > 30) {
            showLikeOverlay(progress);
            hidePassOverlay();
        } else if (deltaX < -30) {
            showPassOverlay(progress);
            hideLikeOverlay();
        } else {
            hideAllOverlays();
        }
    });

    candidateCard.setOnMouseReleased(e -> {
        candidateCard.setCursor(Cursor.HAND);
        double deltaX = e.getSceneX() - startX;

        if (Math.abs(deltaX) > DRAG_THRESHOLD) {
            if (deltaX > 0) {
                animateCardExit(true, this::performLike);
            } else {
                animateCardExit(false, this::performPass);
            }
        } else {
            animateSnapBack();
        }
    });
}

private void animateCardExit(boolean toRight, Runnable onComplete) {
    double targetX = toRight ? 800 : -800;
    double targetRotation = toRight ? 30 : -30;

    ParallelTransition exit = new ParallelTransition(
        createTranslate(candidateCard, targetX, 300),
        createRotate(candidateCard, targetRotation, 300),
        createFadeOut(candidateCard, 300)
    );

    exit.setOnFinished(e -> {
        resetCardPosition();
        hideAllOverlays();
        onComplete.run();
    });
    exit.play();
}

private void animateSnapBack() {
    ParallelTransition snapBack = new ParallelTransition(
        createTranslate(candidateCard, 0, 200),
        createRotate(candidateCard, 0, 200)
    );
    snapBack.setInterpolator(Interpolator.EASE_OUT);
    snapBack.setOnFinished(e -> hideAllOverlays());
    snapBack.play();
}
```

---

## E08: Undo Animation

**Description**: Animate the previous card flying back when undo is pressed.

### Implementation

```java
private Candidate lastSwipedCandidate;
private boolean lastWasLike;

private void storeLastAction(Candidate candidate, boolean wasLike) {
    this.lastSwipedCandidate = candidate;
    this.lastWasLike = wasLike;
    undoButton.setDisable(false);
}

@FXML
private void handleUndo() {
    if (lastSwipedCandidate == null) {
        ToastService.getInstance().showWarning("Nothing to undo");
        return;
    }

    // Undo in backend
    viewModel.undo();

    // Animate card return
    double startX = lastWasLike ? 800 : -800;
    double startRotation = lastWasLike ? 30 : -30;

    candidateCard.setTranslateX(startX);
    candidateCard.setRotate(startRotation);
    candidateCard.setOpacity(0);

    populateCard(lastSwipedCandidate);
    candidateCard.setVisible(true);

    ParallelTransition returnAnim = new ParallelTransition(
        createTranslate(candidateCard, 0, 400),
        createRotate(candidateCard, 0, 400),
        createFadeIn(candidateCard, 400)
    );
    returnAnim.setInterpolator(Interpolator.EASE_OUT);
    returnAnim.play();

    lastSwipedCandidate = null;
    undoButton.setDisable(true);
    ToastService.getInstance().showSuccess("Undo successful!");
}
```

---

## Verification Checklist

- [ ] Card stack shows 2-3 cards behind current candidate
- [ ] Cards have proper scale/opacity depth effect
- [ ] Drag card left → shows "PASS" overlay with increasing opacity
- [ ] Drag card right → shows "LIKE" overlay with increasing opacity
- [ ] Release within threshold → card snaps back smoothly
- [ ] Release beyond threshold → card animates off-screen
- [ ] Card rotates slightly during drag (natural feel)
- [ ] Mutual like triggers "It's a Match!" popup
- [ ] Popup has confetti animation
- [ ] Avatars fly in from sides
- [ ] Heart icon pulses/scales
- [ ] "Send Message" button navigates to chat
- [ ] "Keep Browsing" closes popup and continues
- [ ] Undo button animates card flying back from off-screen
- [ ] Undo only available when there's a recent action
- [ ] `mvn clean compile` succeeds
- [ ] `mvn test` passes
