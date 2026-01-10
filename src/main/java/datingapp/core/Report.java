package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Represents a report filed against a user. Immutable after creation. */
public record Report(
    UUID id,
    UUID reporterId, // Who filed the report
    UUID reportedUserId, // Who is being reported
    Reason reason,
    String description, // Optional free text (max 500 chars)
    Instant createdAt) {

  public enum Reason {
    SPAM,
    INAPPROPRIATE_CONTENT,
    HARASSMENT,
    FAKE_PROFILE,
    UNDERAGE,
    OTHER
  }

  public Report {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(reporterId, "reporterId cannot be null");
    Objects.requireNonNull(reportedUserId, "reportedUserId cannot be null");
    Objects.requireNonNull(reason, "reason cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");

    if (reporterId.equals(reportedUserId)) {
      throw new IllegalArgumentException("Cannot report yourself");
    }
    if (description != null && description.length() > 500) {
      throw new IllegalArgumentException("Description too long (max 500)");
    }
  }

  /** Creates a new Report with generated ID and current timestamp. */
  public static Report create(
      UUID reporterId, UUID reportedUserId, Reason reason, String description) {
    return new Report(
        UUID.randomUUID(), reporterId, reportedUserId, reason, description, Instant.now());
  }
}
