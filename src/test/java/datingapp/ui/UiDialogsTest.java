package datingapp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import datingapp.core.connection.ConnectionModels.Report;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression tests for report dialog payload propagation helpers. */
class UiDialogsTest {

    @Test
    @DisplayName("deliverReportSelection trims the description before invoking the callback")
    void deliverReportSelectionTrimsDescription() {
        AtomicReference<Report.Reason> reasonRef = new AtomicReference<>();
        AtomicReference<String> descriptionRef = new AtomicReference<>();

        UiDialogs.deliverReportSelection(
                (reason, description) -> {
                    reasonRef.set(reason);
                    descriptionRef.set(description);
                },
                Report.Reason.SPAM,
                "  needs review  ");

        assertEquals(Report.Reason.SPAM, reasonRef.get());
        assertEquals("needs review", descriptionRef.get());
    }

    @Test
    @DisplayName("deliverReportSelection converts blank descriptions to null")
    void deliverReportSelectionConvertsBlankDescriptionToNull() {
        AtomicReference<String> descriptionRef = new AtomicReference<>("sentinel");

        UiDialogs.deliverReportSelection(
                (reason, description) -> descriptionRef.set(description), Report.Reason.INAPPROPRIATE_CONTENT, "   ");

        assertNull(descriptionRef.get());
    }
}
