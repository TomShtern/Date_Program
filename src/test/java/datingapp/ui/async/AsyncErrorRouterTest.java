package datingapp.ui.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.ui.viewmodel.ViewModelErrorSink;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AsyncErrorRouter")
class AsyncErrorRouterTest {

    @Test
    @DisplayName("routes to sink on UI dispatcher when sink is present")
    void routesToSinkOnUiDispatcherWhenSinkPresent() {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        AtomicBoolean wasUiThread = new AtomicBoolean(false);

        ViewModelErrorSink sink = message -> {
            wasUiThread.set(dispatcher.isUiThread());
            receivedMessage.set(message);
        };

        AsyncErrorRouter router = new AsyncErrorRouter(dispatcher, () -> sink, (_, _) -> {
            throw new AssertionError("Fallback logger should not be used when sink exists");
        });

        router.onError("load profile", new IllegalStateException("boom"));

        assertTrue(wasUiThread.get());
        assertNotNull(receivedMessage.get());
        assertTrue(receivedMessage.get().contains("load profile"));
        assertTrue(receivedMessage.get().contains("boom"));
    }

    @Test
    @DisplayName("falls back to logger callback when sink is absent")
    void fallsBackToLoggerCallbackWhenSinkAbsent() {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        AtomicReference<String> fallbackMessage = new AtomicReference<>();
        AtomicReference<Throwable> fallbackThrowable = new AtomicReference<>();
        IllegalArgumentException failure = new IllegalArgumentException("bad state");

        AsyncErrorRouter router = new AsyncErrorRouter(dispatcher, () -> null, (message, error) -> {
            fallbackMessage.set(message);
            fallbackThrowable.set(error);
        });

        router.onError("save settings", failure);

        assertNotNull(fallbackMessage.get());
        assertTrue(fallbackMessage.get().contains("save settings"));
        assertEquals(failure, fallbackThrowable.get());
    }

    private static final class TestUiThreadDispatcher implements UiThreadDispatcher {
        private final ThreadLocal<Boolean> uiThread = ThreadLocal.withInitial(() -> false);

        @Override
        public boolean isUiThread() {
            return uiThread.get();
        }

        @Override
        public void dispatch(Runnable action) {
            boolean previous = uiThread.get();
            uiThread.set(true);
            try {
                action.run();
            } finally {
                uiThread.set(previous);
            }
        }
    }
}
