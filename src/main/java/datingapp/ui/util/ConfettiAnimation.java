package datingapp.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Confetti particle effect animation for celebration moments.
 * Uses a Canvas to render falling confetti particles.
 */
public class ConfettiAnimation {

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
            // Reset particle if it falls off screen
            if (p.posY > height + 20) {
                p.reset(width, random);
            }
        }
    }

    private void render(GraphicsContext gc) {
        // Clear canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw particles
        for (Particle p : particles) {
            gc.save();
            gc.translate(p.posX, p.posY);
            gc.rotate(p.rotation);
            gc.setFill(p.color);
            gc.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.6);
            gc.restore();
        }
    }

    /** Represents a single confetti particle. */
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
            // Start from random height for initial burst effect
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
            // Add slight gravity and air resistance
            vy += 0.03;
            vx *= 0.99;
        }
    }
}
