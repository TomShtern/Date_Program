package datingapp.ui.util;

/**
 * Interface for controllers that support responsive layout changes.
 * Implement this in controllers that need to adapt their UI based on window
 * size.
 */
public interface ResponsiveController {

    /**
     * Called when the window enters compact mode (width < 900px).
     * Controllers should hide non-essential UI elements and use single-column
     * layouts.
     *
     * @param compact true to enable compact mode, false for normal mode
     */
    void setCompactMode(boolean compact);

    /**
     * Called when the window enters expanded mode (width > 1100px).
     * Controllers can show additional UI elements and use wider layouts.
     *
     * @param expanded true to enable expanded mode, false for normal mode
     */
    default void setExpandedMode(boolean expanded) {
        // Default implementation does nothing
    }
}
