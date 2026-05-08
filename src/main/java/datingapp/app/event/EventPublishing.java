package datingapp.app.event;

import org.slf4j.Logger;

/**
 * Shared helper for publishing events with a warn-level log on failure.
 *
 * <p>Replaces the four identical inline try/catch blocks that previously existed in
 * {@code ProfileMutationUseCases}, {@code MessagingUseCases}, {@code ProfileNotesUseCases},
 * and {@code SocialUseCases}.
 */
public final class EventPublishing {

    private EventPublishing() {}

    public static void publishOrWarn(AppEventBus bus, AppEvent event, String failureMessage, Logger logger) {
        try {
            bus.publish(event);
        } catch (RuntimeException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("{}: {}", failureMessage, e.getMessage(), e);
            }
        }
    }
}
