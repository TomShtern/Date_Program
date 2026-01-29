package datingapp.module;

/**
 * Base interface for dependency injection modules. Provides lifecycle hooks for resource management.
 *
 * <p>Modules group related services and storage interfaces together, enabling: - Clean separation of
 * concerns - Easy testing with mock implementations - Proper resource lifecycle management
 */
public interface Module extends AutoCloseable {

    /**
     * Called after construction. Validates wiring is correct, connections work, required resources
     * exist. Should fail fast on any misconfiguration.
     */
    default void validate() {}

    /**
     * Called once at application startup. Initialize resources, start background tasks.
     */
    default void start() {}

    /**
     * Called at application shutdown. Release resources, close connections, stop tasks.
     */
    @Override
    default void close() {}
}
