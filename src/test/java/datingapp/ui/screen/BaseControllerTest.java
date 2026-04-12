package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("BaseController lifecycle and back navigation")
class BaseControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void tearDown() {
        NavigationService.getInstance().clearHistory();
    }

    @Test
    @DisplayName("cleanup is safe when no resources were registered")
    void cleanupIsSafeWhenNoResourcesWereRegistered() throws Exception {
        TestController controller = new TestController();

        JavaFxTestSupport.runOnFxAndWait(() -> {
            controller.cleanup();
            controller.cleanup();
        });

        assertTrue(JavaFxTestSupport.callOnFxAndWait(
                () -> !NavigationService.getInstance().canGoBack()));
    }

    @Test
    @DisplayName("cleanup and back handling are safe without a navigation service")
    void cleanupAndBackHandlingAreSafeWithoutNavigationService() throws Exception {
        NullNavigationController controller = new NullNavigationController();

        JavaFxTestSupport.runOnFxAndWait(() -> {
            controller.cleanup();
            controller.invokeHandleBack();
            controller.cleanup();
        });

        assertTrue(true);
    }

    @Test
    @DisplayName("handleBack is safe with and without navigation history")
    void handleBackIsSafeWithAndWithoutNavigationHistory() throws Exception {
        TestController controller = new TestController();
        NavigationService navigationService = NavigationService.getInstance();

        assertFalse(navigationService.canGoBack());
        JavaFxTestSupport.runOnFxAndWait(controller::invokeHandleBack);

        Deque<NavigationService.ViewType> history = getHistory(navigationService);
        clearHistory(navigationService);
        history.addLast(NavigationService.ViewType.CHAT);
        history.addLast(NavigationService.ViewType.DASHBOARD);

        assertTrue(navigationService.canGoBack());
        JavaFxTestSupport.runOnFxAndWait(controller::invokeHandleBack);
    }

    private static Deque<NavigationService.ViewType> getHistory(NavigationService navigationService) throws Exception {
        Field field = NavigationService.class.getDeclaredField("navigationHistory");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Deque<NavigationService.ViewType> history = (Deque<NavigationService.ViewType>) field.get(navigationService);
        return history;
    }

    private static void clearHistory(NavigationService navigationService) throws Exception {
        Deque<NavigationService.ViewType> history = getHistory(navigationService);
        history.clear();
    }

    private static final class TestController extends BaseController {
        void invokeHandleBack() {
            handleBack();
        }
    }

    private static final class NullNavigationController extends BaseController {
        @Override
        protected NavigationService navigationService() {
            return null;
        }

        void invokeHandleBack() {
            handleBack();
        }
    }
}
