package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for Report domain model. */
class ReportTest {

    @Test
    @DisplayName("Cannot report yourself")
    void cannotReportSelf() {
        UUID userId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Report.create(userId, userId, Report.Reason.SPAM, null),
                "Should throw when trying to report yourself");
        assertNotNull(ex);
    }

    @Test
    @DisplayName("Report creation succeeds with valid data")
    void reportCreationSucceeds() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();

        Report report = Report.create(reporterId, reportedId, Report.Reason.HARASSMENT, "Sent threatening messages");

        assertNotNull(report.id(), "Report should have an ID");
        assertEquals(reporterId, report.reporterId(), "Reporter ID should match");
        assertEquals(reportedId, report.reportedUserId(), "Reported user ID should match");
        assertEquals(Report.Reason.HARASSMENT, report.reason(), "Reason should match");
        assertEquals("Sent threatening messages", report.description(), "Description should match");
        assertNotNull(report.createdAt(), "Created timestamp should be set");
    }

    @Test
    @DisplayName("Report works without description")
    void reportWorksWithoutDescription() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();

        Report report = Report.create(reporterId, reportedId, Report.Reason.SPAM, null);

        assertNull(report.description(), "Description should be null when not provided");
    }

    @Test
    @DisplayName("Description exceeding 500 characters throws")
    void descriptionTooLongThrows() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();
        String longDescription = "x".repeat(501);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Report.create(reporterId, reportedId, Report.Reason.OTHER, longDescription),
                "Should throw when description exceeds 500 characters");
        assertNotNull(ex);
    }

    @Test
    @DisplayName("Description at exactly 500 characters is allowed")
    void descriptionAtLimitIsAllowed() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();
        String maxDescription = "x".repeat(500);

        Report report = Report.create(reporterId, reportedId, Report.Reason.OTHER, maxDescription);

        assertEquals(500, report.description().length(), "Description should be exactly 500 chars");
    }

    @Test
    @DisplayName("All report reasons are valid")
    void allReasonsAreValid() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();

        for (Report.Reason reason : Report.Reason.values()) {
            Report report = Report.create(reporterId, reportedId, reason, null);
            assertEquals(reason, report.reason(), "Each reason should be creatable");
        }
    }

    @Test
    @DisplayName("Report with null reason throws")
    void nullReasonThrows() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();

        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> Report.create(reporterId, reportedId, null, null));
        assertNotNull(ex);
    }
}
