package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;

/**
 * Profile verification via a one-time code.
 *
 * <p>NOTE: This is currently simulated. The service generates a code and the CLI prints it to the
 * console instead of actually delivering it via SMS/email. A future enhancement can integrate a
 * real delivery provider without changing the domain model.
 */
public class VerificationService {

  public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

  private final Duration ttl;
  private final Random random;

  public VerificationService() {
    this(DEFAULT_TTL, new Random());
  }

  public VerificationService(Duration ttl, Random random) {
    this.ttl = Objects.requireNonNull(ttl, "ttl cannot be null");
    this.random = Objects.requireNonNull(random, "random cannot be null");
  }

  public String generateVerificationCode() {
    int value = random.nextInt(1_000_000);
    return String.format("%06d", value);
  }

  public boolean isExpired(Instant sentAt) {
    if (sentAt == null) {
      return true;
    }
    return sentAt.plus(ttl).isBefore(Instant.now());
  }

  public boolean verifyCode(User user, String inputCode) {
    Objects.requireNonNull(user, "user cannot be null");
    if (inputCode == null || inputCode.isBlank()) {
      return false;
    }

    String expected = user.getVerificationCode();
    if (expected == null) {
      return false;
    }

    if (isExpired(user.getVerificationSentAt())) {
      return false;
    }

    return expected.equals(inputCode.trim());
  }
}
