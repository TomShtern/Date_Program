package datingapp.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Utility class for common UI animations and effects. */
public final class UiAnimations {

    private static final String PARALLAX_BINDING_KEY = UiAnimations.class.getName() + ".parallaxBinding";

    private UiAnimations() {
        // Utility class
    }

    private static final class ParallaxBinding {

        private final EventHandler<MouseEvent> mouseMovedHandler;
        private final ChangeListener<Scene> sceneListener;
        private final TranslateTransition parallaxMove;

        private ParallaxBinding(
                EventHandler<MouseEvent> mouseMovedHandler,
                ChangeListener<Scene> sceneListener,
                TranslateTransition parallaxMove) {
            this.mouseMovedHandler = mouseMovedHandler;
            this.sceneListener = sceneListener;
            this.parallaxMove = parallaxMove;
        }
    }

    /**
     * Fades in a node.
     */
    public static void fadeIn(Node node, double durationMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    /**
     * Slides a node up while fading in.
     */
    public static void slideUp(Node node, double durationMs, double distance) {
        node.setOpacity(0);
        node.setTranslateY(distance);

        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), node);
        tt.setToY(0);

        ft.play();
        tt.play();
    }

    /**
     * Adds a subtle pulse effect on hover.
     */
    public static void addPulseOnHover(Node node) {
        ScaleTransition st = new ScaleTransition(UiConstants.HOVER_PULSE_DURATION, node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(UiConstants.HOVER_PULSE_SCALE);
        st.setToY(UiConstants.HOVER_PULSE_SCALE);

        node.setOnMouseEntered(e -> {
            e.consume();
            st.setRate(1.0);
            st.play();
        });
        node.setOnMouseExited(e -> {
            e.consume();
            st.setRate(-1.0);
            st.play();
        });
    }

    /**
     * Quick scale pulse for button press feedback.
     * Per JavaFX Skill Cheatsheet Section 14.
     */
    public static void pulseScale(Node node) {
        ScaleTransition scale = new ScaleTransition(UiConstants.BUTTON_PULSE_DURATION, node);
        scale.setToX(UiConstants.BUTTON_PULSE_SCALE);
        scale.setToY(UiConstants.BUTTON_PULSE_SCALE);
        scale.setAutoReverse(true);
        scale.setCycleCount(UiConstants.BUTTON_PULSE_CYCLES);
        scale.play();
    }

    /**
     * Adds a continuous pulsing glow effect to a node.
     *
     * @param node      The node to apply the glow effect to
     * @param glowColor The color of the glow
     * @return the running Timeline so callers can stop it explicitly if needed
     */
    public static Timeline addPulsingGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow();
        glow.setColor(glowColor);
        glow.setRadius(UiConstants.GLOW_RADIUS_MIN);
        glow.setSpread(UiConstants.GLOW_SPREAD_INITIAL);

        Timeline timeline = new Timeline(
                new KeyFrame(
                        UiConstants.GLOW_START_TIME,
                        new KeyValue(glow.radiusProperty(), UiConstants.GLOW_RADIUS_MIN),
                        new KeyValue(glow.spreadProperty(), UiConstants.GLOW_SPREAD_MIN)),
                new KeyFrame(
                        UiConstants.GLOW_MID_TIME,
                        new KeyValue(glow.radiusProperty(), UiConstants.GLOW_RADIUS_MAX),
                        new KeyValue(glow.spreadProperty(), UiConstants.GLOW_SPREAD_MAX)),
                new KeyFrame(
                        UiConstants.GLOW_END_TIME,
                        new KeyValue(glow.radiusProperty(), UiConstants.GLOW_RADIUS_MIN),
                        new KeyValue(glow.spreadProperty(), UiConstants.GLOW_SPREAD_MIN)));
        timeline.setCycleCount(Animation.INDEFINITE);

        node.setEffect(glow);
        timeline.play();

        ChangeListener<Scene> cleanupListener = new ChangeListener<>() {
            @Override
            public void changed(
                    javafx.beans.value.ObservableValue<? extends Scene> observable, Scene oldScene, Scene newScene) {
                if (newScene == null) {
                    timeline.stop();
                    node.sceneProperty().removeListener(this);
                }
            }
        };
        node.sceneProperty().addListener(cleanupListener);
        return timeline;
    }

    /**
     * Creates a fade-in animation.
     *
     * @param node     The node to fade in
     * @param duration The duration of the fade
     * @return The FadeTransition (not yet played)
     */
    public static FadeTransition createFadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        return fade;
    }

    /**
     * Creates a fade-out animation.
     *
     * @param node     The node to fade out
     * @param duration The duration of the fade
     * @return The FadeTransition (not yet played)
     */
    public static FadeTransition createFadeOut(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(1);
        fade.setToValue(0);
        return fade;
    }

    /**
     * Creates a scale pulse animation (for buttons, icons, etc.).
     *
     * @param node The node to pulse
     * @return The ScaleTransition (not yet played)
     */
    public static ScaleTransition createPulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(UiConstants.ICON_PULSE_DURATION, node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(UiConstants.ICON_PULSE_SCALE);
        pulse.setToY(UiConstants.ICON_PULSE_SCALE);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(UiConstants.ICON_PULSE_CYCLES);
        return pulse;
    }

    /**
     * Plays a shake animation for validation errors.
     *
     * @param node The node to shake
     */
    public static void playShake(Node node) {
        TranslateTransition shake = new TranslateTransition(UiConstants.SHAKE_DURATION, node);
        shake.setFromX(-UiConstants.SHAKE_DISTANCE);
        shake.setToX(UiConstants.SHAKE_DISTANCE);
        shake.setCycleCount(UiConstants.SHAKE_CYCLES);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> {
            e.consume();
            node.setTranslateX(0);
        });
        shake.play();
    }

    /**
     * Bounce animation for elements entering the screen.
     *
     * @param node The node to bounce in
     */
    public static void playBounceIn(Node node) {
        node.setScaleX(0);
        node.setScaleY(0);

        ScaleTransition scale = new ScaleTransition(UiConstants.BOUNCE_IN_DURATION, node);
        scale.setFromX(0);
        scale.setFromY(0);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        // Overshoot effect
        ScaleTransition overshoot = new ScaleTransition(UiConstants.BOUNCE_OVERSHOOT_DURATION, node);
        overshoot.setFromX(1);
        overshoot.setFromY(1);
        overshoot.setToX(UiConstants.BOUNCE_OVERSHOOT_SCALE);
        overshoot.setToY(UiConstants.BOUNCE_OVERSHOOT_SCALE);
        overshoot.setAutoReverse(true);
        overshoot.setCycleCount(UiConstants.BOUNCE_OVERSHOOT_CYCLES);

        scale.setOnFinished(e -> {
            e.consume();
            overshoot.play();
        });
        scale.play();
    }

    /**
     * Creates a slide transition for a node.
     *
     * @param node     The node to slide
     * @param toX      Target X translation
     * @param duration The duration of the slide
     * @return The TranslateTransition (not yet played)
     */
    public static TranslateTransition createSlide(Node node, double toX, Duration duration) {
        TranslateTransition slide = new TranslateTransition(duration, node);
        slide.setToX(toX);
        slide.setInterpolator(Interpolator.EASE_OUT);
        return slide;
    }

    /**
     * Adds a parallax effect that moves a node based on mouse position.
     *
     * @param node      The node to apply parallax to
     * @param intensity The intensity of the parallax effect (5-20 recommended)
     */
    public static void addParallaxEffect(Node node, double intensity) {
        Object existingBinding = node.getProperties().remove(PARALLAX_BINDING_KEY);
        if (existingBinding instanceof ParallaxBinding binding) {
            node.sceneProperty().removeListener(binding.sceneListener);
            Scene currentScene = node.getScene();
            if (currentScene != null) {
                currentScene.removeEventHandler(MouseEvent.MOUSE_MOVED, binding.mouseMovedHandler);
            }
            binding.parallaxMove.stop();
        }

        TranslateTransition parallaxMove = new TranslateTransition(UiConstants.PARALLAX_MOVE_DURATION, node);
        parallaxMove.setInterpolator(Interpolator.EASE_BOTH);

        EventHandler<MouseEvent> mouseMovedHandler = e -> {
            Scene scene = node.getScene();
            if (scene == null) {
                return;
            }

            double centerX = scene.getWidth() / 2;
            double centerY = scene.getHeight() / 2;
            if (centerX == 0 || centerY == 0) {
                return;
            }

            double offsetX = (e.getSceneX() - centerX) / centerX * intensity;
            double offsetY = (e.getSceneY() - centerY) / centerY * intensity;

            parallaxMove.stop();
            parallaxMove.setToX(offsetX);
            parallaxMove.setToY(offsetY);
            parallaxMove.playFromStart();
        };

        ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            }
            if (newScene != null) {
                newScene.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
            }
        };

        ParallaxBinding binding = new ParallaxBinding(mouseMovedHandler, sceneListener, parallaxMove);
        node.getProperties().put(PARALLAX_BINDING_KEY, binding);
        node.sceneProperty().addListener(sceneListener);

        Scene scene = node.getScene();
        if (scene != null) {
            scene.addEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        }
    }

    /**
     * Confetti particle effect animation for celebration moments.
     * Uses a Canvas to render falling confetti particles.
     */
    public static final class ConfettiAnimation {

        private static final int PARTICLE_COUNT = 100;
        private static final Color[] COLORS = {
            Color.web("#f43f5e"), // Rose
            Color.web("#a855f7"), // Purple
            Color.web("#f59e0b"), // Amber
            Color.web("#10b981"), // Emerald
            Color.web("#3b82f6"), // Blue
            Color.web("#ec4899"), // Pink
            Color.web("#8b5cf6") // Violet
        };

        private final List<Particle> particles = new ArrayList<>();
        private AnimationTimer timer;
        private final Random random = new Random();
        private Canvas canvas;

        /** Starts the confetti animation on the specified canvas. */
        public void play(Canvas canvas) {
            stop(); // Stop any existing animation first
            this.canvas = canvas;
            initParticles();
            startAnimation();
        }

        /** Stops the confetti animation. */
        public void stop() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            particles.clear();
        }

        private void initParticles() {
            particles.clear();
            double width = canvas.getWidth();
            double height = canvas.getHeight();

            for (int i = 0; i < PARTICLE_COUNT; i++) {
                particles.add(new Particle(width, height, random));
            }
        }

        private void startAnimation() {
            GraphicsContext gc = canvas.getGraphicsContext2D();

            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    update();
                    render(gc);
                }
            };
            timer.start();
        }

        private void update() {
            double height = canvas.getHeight();
            double width = canvas.getWidth();

            for (Particle p : particles) {
                p.update();
                if (p.posY > height + 20) {
                    p.reset(width, random);
                }
            }
        }

        private void render(GraphicsContext gc) {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

            for (Particle p : particles) {
                gc.save();
                gc.translate(p.posX, p.posY);
                gc.rotate(p.rotation);
                gc.setFill(p.color);
                gc.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.6);
                gc.restore();
            }
        }

        private static class Particle {
            double posX;
            double posY;
            double vx;
            double vy;
            double rotation;
            double rotationSpeed;
            double size;
            Color color;

            Particle(double canvasWidth, double canvasHeight, Random random) {
                reset(canvasWidth, random);
                this.posY = random.nextDouble() * canvasHeight * 0.5;
            }

            void reset(double canvasWidth, Random random) {
                this.posX = random.nextDouble() * canvasWidth;
                this.posY = -20;
                this.vx = (random.nextDouble() - 0.5) * 2;
                this.vy = random.nextDouble() * 2 + 1.5;
                this.rotation = random.nextDouble() * 360;
                this.rotationSpeed = (random.nextDouble() - 0.5) * 10;
                this.size = random.nextDouble() * 8 + 4;
                this.color = COLORS[random.nextInt(COLORS.length)];
            }

            void update() {
                posX += vx;
                posY += vy;
                rotation += rotationSpeed;
                vy += 0.03;
                vx *= 0.99;
            }
        }
    }
}
