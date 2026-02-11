package datingapp.core;

import org.slf4j.Logger;

public interface LoggingSupport {

    /**
     * Implementations must return the logger to use.
     */
    Logger logger();

    default void logTrace(String message, Object... args) {
        Logger log = logger();
        if (log != null && log.isTraceEnabled()) {
            log.trace(message, args);
        }
    }

    default void logDebug(String message, Object... args) {
        Logger log = logger();
        if (log != null && log.isDebugEnabled()) {
            log.debug(message, args);
        }
    }

    default void logInfo(String message, Object... args) {
        Logger log = logger();
        if (log != null && log.isInfoEnabled()) {
            log.info(message, args);
        }
    }

    default void logWarn(String message, Object... args) {
        Logger log = logger();
        if (log != null && log.isWarnEnabled()) {
            log.warn(message, args);
        }
    }

    default void logError(String message, Object... args) {
        Logger log = logger();
        if (log != null && log.isErrorEnabled()) {
            log.error(message, args);
        }
    }

    static void logTrace(Logger log, String message, Object... args) {
        if (log != null && log.isTraceEnabled()) {
            log.trace(message, args);
        }
    }

    static void logDebug(Logger log, String message, Object... args) {
        if (log != null && log.isDebugEnabled()) {
            log.debug(message, args);
        }
    }

    static void logInfo(Logger log, String message, Object... args) {
        if (log != null && log.isInfoEnabled()) {
            log.info(message, args);
        }
    }

    static void logWarn(Logger log, String message, Object... args) {
        if (log != null && log.isWarnEnabled()) {
            log.warn(message, args);
        }
    }

    static void logError(Logger log, String message, Object... args) {
        if (log != null && log.isErrorEnabled()) {
            log.error(message, args);
        }
    }
}
