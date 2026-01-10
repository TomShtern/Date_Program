package datingapp.core;

import java.util.List;
import java.util.UUID;

/** Storage interface for Report entities. Defined in core, implemented in storage layer. */
public interface ReportStorage {

  /** Saves a report. */
  void save(Report report);

  /** Count reports against a user. */
  int countReportsAgainst(UUID userId);

  /** Check if reporter has already reported this user. */
  boolean hasReported(UUID reporterId, UUID reportedUserId);

  /** Get all reports against a user (for admin review later). */
  List<Report> getReportsAgainst(UUID userId);

  // === Statistics Methods (Phase 0.5b) ===

  /** Count reports MADE BY a user (reports they filed). */
  int countReportsBy(UUID userId);
}
