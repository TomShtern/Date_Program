package datingapp.ui;

import datingapp.core.i18n.I18n;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;

/** Shared JavaFX test helpers for controller and resource verification. */
public final class JavaFxTestSupport {

    private static final AtomicBoolean JAVA_FX_INITIALIZED = new AtomicBoolean(false);
    private static final UiThreadDispatcher IMMEDIATE_UI_DISPATCHER = new ImmediateUiThreadDispatcher();
    private static final UiThreadDispatcher BLOCKING_UI_DISPATCHER = new BlockingJavaFxUiThreadDispatcher();

    private JavaFxTestSupport() {}

    public static UiThreadDispatcher immediateUiDispatcher() {
        return IMMEDIATE_UI_DISPATCHER;
    }

    public static UiThreadDispatcher blockingUiDispatcher() {
        return BLOCKING_UI_DISPATCHER;
    }

    public static void initJfx() throws InterruptedException {
        if (JAVA_FX_INITIALIZED.get()) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                JAVA_FX_INITIALIZED.set(true);
                latch.countDown();
            });
        } catch (IllegalStateException _) {
            JAVA_FX_INITIALIZED.set(true);
            latch.countDown();
        }

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out initializing JavaFX toolkit");
        }
    }

    public static void runOnFxAndWait(Runnable action) throws InterruptedException {
        Objects.requireNonNull(action, "action cannot be null");
        callOnFxAndWait(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T callOnFxAndWait(Callable<T> action) throws InterruptedException {
        Objects.requireNonNull(action, "action cannot be null");
        if (Platform.isFxApplicationThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new IllegalStateException("JavaFX action failed", e);
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for JavaFX action");
        }
        if (error.get() != null) {
            throw new IllegalStateException("JavaFX action failed", error.get());
        }
        return result.get();
    }

    public static LoadedFxml loadFxml(String resourcePath, Supplier<?> controllerSupplier) throws InterruptedException {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        Objects.requireNonNull(controllerSupplier, "controllerSupplier cannot be null");
        return callOnFxAndWait(() -> {
            FXMLLoader loader = new FXMLLoader(JavaFxTestSupport.class.getResource(resourcePath), I18n.bundle());
            Object controller = controllerSupplier.get();
            loader.setControllerFactory(_ -> controller);
            Parent root = loader.load();
            Scene scene = new Scene(root);
            root.applyCss();
            root.layout();
            return new LoadedFxml(root, scene, loader.getController());
        });
    }

    public static <T extends Node> T lookup(Parent root, String selector, Class<T> expectedType)
            throws InterruptedException {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(selector, "selector cannot be null");
        Objects.requireNonNull(expectedType, "expectedType cannot be null");
        return callOnFxAndWait(() -> {
            Node node = root.lookup(selector);
            if (node == null) {
                throw new IllegalStateException("Node not found for selector: " + selector);
            }
            if (!expectedType.isInstance(node)) {
                throw new IllegalStateException(
                        "Node for selector " + selector + " is not a " + expectedType.getSimpleName());
            }
            return expectedType.cast(node);
        });
    }

    public static Button findButtonByText(Parent root, String text) throws InterruptedException {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        return callOnFxAndWait(() -> {
            Queue<Node> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                Node node = queue.remove();
                if (node instanceof Button button && text.equals(button.getText())) {
                    return button;
                }
                if (node instanceof Parent parent) {
                    queue.addAll(parent.getChildrenUnmodifiable());
                }
            }
            throw new IllegalStateException("Button not found with text: " + text);
        });
    }

    public static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        waitForFxEvents();
        return condition.getAsBoolean();
    }

    public static void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for JavaFX events");
        }
    }

    private static final class ImmediateUiThreadDispatcher implements UiThreadDispatcher {

        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void dispatch(Runnable action) {
            Objects.requireNonNull(action, "action cannot be null");
            action.run();
        }
    }

    private static final class BlockingJavaFxUiThreadDispatcher implements UiThreadDispatcher {

        @Override
        public boolean isUiThread() {
            try {
                return Platform.isFxApplicationThread();
            } catch (IllegalStateException _) {
                return false;
            }
        }

        @Override
        public void dispatch(Runnable action) {
            Objects.requireNonNull(action, "action cannot be null");
            if (isUiThread()) {
                action.run();
                return;
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Platform.runLater(() -> {
                try {
                    action.run();
                } catch (Throwable throwable) {
                    error.set(throwable);
                } finally {
                    latch.countDown();
                }
            });

            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out dispatching to JavaFX thread");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted dispatching to JavaFX thread", e);
            }

            Throwable throwable = error.get();
            if (throwable == null) {
                return;
            }
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("JavaFX action failed", throwable);
        }
    }

    public record LoadedFxml(Parent root, Scene scene, Object controller) {}
}
