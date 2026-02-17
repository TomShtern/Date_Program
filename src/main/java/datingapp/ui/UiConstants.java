package datingapp.ui;

import javafx.util.Duration;

/** Shared UI constants: animation timings, cache sizes, and resource defaults. */
public final class UiConstants {

    private UiConstants() {
        // Utility class
    }

    // ── Cache & resources ───────────────────────────────────────────────────

    /** Maximum number of images to cache before eviction. */
    public static final int IMAGE_CACHE_MAX_SIZE = 100;

    /** Default avatar resource path. */
    public static final String DEFAULT_AVATAR_PATH = "/images/default-avatar.png";

    // ── Toast durations ─────────────────────────────────────────────────────

    public static final Duration TOAST_SUCCESS_DURATION = Duration.seconds(3);
    public static final Duration TOAST_WARNING_DURATION = Duration.seconds(4);
    public static final Duration TOAST_ERROR_DURATION = Duration.seconds(5);
    public static final Duration TOAST_INFO_DURATION = Duration.seconds(3);
    public static final Duration TOAST_ENTRANCE_DURATION = Duration.millis(200);
    public static final Duration TOAST_EXIT_DURATION = Duration.millis(300);

    // ── Hover/press pulses ──────────────────────────────────────────────────

    public static final Duration HOVER_PULSE_DURATION = Duration.millis(200);
    public static final double HOVER_PULSE_SCALE = 1.05;

    public static final Duration BUTTON_PULSE_DURATION = Duration.millis(100);
    public static final double BUTTON_PULSE_SCALE = 1.15;
    public static final int BUTTON_PULSE_CYCLES = 2;

    public static final Duration ICON_PULSE_DURATION = Duration.millis(150);
    public static final double ICON_PULSE_SCALE = 1.1;
    public static final int ICON_PULSE_CYCLES = 2;

    // ── Glow animation ──────────────────────────────────────────────────────

    public static final Duration GLOW_START_TIME = Duration.ZERO;
    public static final Duration GLOW_MID_TIME = Duration.millis(1000);
    public static final Duration GLOW_END_TIME = Duration.millis(2000);
    public static final double GLOW_RADIUS_MIN = 15;
    public static final double GLOW_RADIUS_MAX = 25;
    public static final double GLOW_SPREAD_INITIAL = 0.3;
    public static final double GLOW_SPREAD_MIN = 0.2;
    public static final double GLOW_SPREAD_MAX = 0.4;

    // ── Shake/bounce ────────────────────────────────────────────────────────

    public static final Duration SHAKE_DURATION = Duration.millis(50);
    public static final double SHAKE_DISTANCE = 10;
    public static final int SHAKE_CYCLES = 6;

    public static final Duration BOUNCE_IN_DURATION = Duration.millis(400);
    public static final Duration BOUNCE_OVERSHOOT_DURATION = Duration.millis(100);
    public static final double BOUNCE_OVERSHOOT_SCALE = 1.05;
    public static final int BOUNCE_OVERSHOOT_CYCLES = 2;

    // ── Parallax and sliding ────────────────────────────────────────────────

    public static final Duration PARALLAX_MOVE_DURATION = Duration.millis(100);

    // ── Typing indicator ────────────────────────────────────────────────────

    public static final int TYPING_DOT_COUNT = 3;
    public static final double TYPING_DOT_RADIUS = 4;
    public static final double TYPING_DOT_SPACING = 4;
    public static final Duration TYPING_BOUNCE_DURATION = Duration.millis(400);
    public static final double TYPING_BOUNCE_DISTANCE = 6;
    public static final Duration TYPING_BOUNCE_DELAY_STEP = Duration.millis(150);

    // ── Progress ring and skeleton loading ───────────────────────────────────

    public static final Duration PROGRESS_RING_ANIMATION_DURATION = Duration.millis(800);
    public static final Duration SKELETON_SHIMMER_FRAME = Duration.millis(50);
    public static final double SKELETON_SHIMMER_STEP = 0.033;
    public static final double SKELETON_GRADIENT_START = -0.5;
    public static final double SKELETON_GRADIENT_WIDTH = 0.5;
}
