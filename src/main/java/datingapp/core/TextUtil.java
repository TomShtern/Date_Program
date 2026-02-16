package datingapp.core;

/**
 * Utility class for text formatting and visualization.
 * Consolidates common text operations like progress bars.
 */
public final class TextUtil {

    private TextUtil() {
        // Utility class
    }

    /**
     * Render a simple ASCII progress bar with percentage (e.g.
     * {@code [####------] 40%}).
     *
     * @param percentage The percentage (0-100)
     * @param width      The width of the bar in characters (excluding brackets)
     * @return Formatted progress bar string
     */
    public static String renderProgressBar(int percentage, int width) {
        int filled = percentage * width / 100;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "#" : "-");
        }
        bar.append("] ").append(percentage).append("%");
        return bar.toString();
    }

    /**
     * Render a Unicode block progress bar (e.g. {@code ████░░░░}).
     *
     * @param fraction The fraction (0.0-1.0)
     * @param width    The width of the bar in characters
     * @return Formatted progress bar string
     */
    public static String renderProgressBar(double fraction, int width) {
        int filled = (int) Math.round(fraction * width);
        int empty = width - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }
}
