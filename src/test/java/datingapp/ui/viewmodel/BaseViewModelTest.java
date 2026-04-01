package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.ui.async.UiThreadDispatcher;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BaseViewModel late-bound error routing")
class BaseViewModelTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = datingapp.ui.JavaFxTestSupport.immediateUiDispatcher();

    @Test
    @DisplayName("late-bound error sink receives async failures after construction")
    void lateBoundErrorSinkReceivesAsyncFailuresAfterConstruction() throws InterruptedException {
        TestViewModel viewModel = new TestViewModel();
        AtomicReference<String> routedMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        viewModel.setErrorSink(message -> {
            routedMessage.set(message);
            latch.countDown();
        });

        viewModel.triggerAsyncFailure();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(routedMessage.get());
        assertTrue(routedMessage.get().contains("late-bound failure"));
        assertTrue(routedMessage.get().contains("boom"));

        viewModel.dispose();
    }

    private static final class TestViewModel extends BaseViewModel {
        private TestViewModel() {
            super("base-viewmodel-test", TEST_DISPATCHER);
        }

        private void triggerAsyncFailure() {
            asyncScope.runFireAndForget("late-bound failure", () -> {
                throw new IllegalStateException("boom");
            });
        }
    }
}
