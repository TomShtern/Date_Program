package datingapp.core;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
        double normalized = Math.clamp(fraction, 0.0, 1.0);
        int filled = (int) Math.round(normalized * width);
        int empty = width - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }

    /**
     * Format a timestamp as a human-friendly relative phrase (e.g. "2 hours ago").
     *
     * @param timestamp the source timestamp
     * @return relative time text, or {@code "Unknown"} when timestamp is null
     */
    public static String formatTimeAgo(Instant timestamp) {
        if (timestamp == null) {
            return "Unknown";
        }

        Instant now = AppClock.now();
        long days = ChronoUnit.DAYS.between(timestamp, now);
        if (days == 0) {
            long hours = ChronoUnit.HOURS.between(timestamp, now);
            if (hours == 0) {
                return "Just now";
            }
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        if (days == 1) {
            return "Yesterday";
        }
        if (days < 7) {
            return days + " days ago";
        }
        if (days < 30) {
            long weeks = days / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }
        long months = days / 30;
        return months + (months == 1 ? " month ago" : " months ago");
    }
}
