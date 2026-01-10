package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
class VerificationServiceTest {

  @Nested
  @DisplayName("Code Verification")
  class CodeVerification {

    @Test
    @DisplayName("Returns false when code expired")
    void returnsFalseWhenCodeExpired() {
      VerificationService verificationService =
          new VerificationService(Duration.ofMinutes(15), new Random(123));

      User user = createActiveUser();
      user.startVerification(User.VerificationMethod.EMAIL, "123456");

      // Override the sent time using the DB factory to avoid adding test-only mutators.
      User expired =
          User.fromDatabase(
              user.getId(),
              user.getName(),
              user.getBio(),
              user.getBirthDate(),
              user.getGender(),
              user.getInterestedIn(),
              user.getLat(),
              user.getLon(),
              user.getMaxDistanceKm(),
              user.getMinAge(),
              user.getMaxAge(),
              user.getPhotoUrls(),
              user.getState(),
              user.getCreatedAt(),
              user.getUpdatedAt(),
              user.getInterests(),
              user.getEmail(),
              user.getPhone(),
              user.isVerified(),
              user.getVerificationMethod(),
              user.getVerificationCode(),
              java.time.Instant.now().minus(Duration.ofMinutes(16)),
              user.getVerifiedAt());

      assertFalse(verificationService.verifyCode(expired, "123456"));
      assertTrue(verificationService.isExpired(expired.getVerificationSentAt()));
    }

    @Test
    @DisplayName("Returns false when code mismatches")
    void returnsFalseWhenCodeMismatches() {
      VerificationService verificationService = new VerificationService();

      User user = createActiveUser();
      user.startVerification(User.VerificationMethod.PHONE, "123456");

      assertFalse(verificationService.verifyCode(user, "000000"));
      assertFalse(Boolean.TRUE.equals(user.isVerified()));
    }
  }

  private static User createActiveUser() {
    User user = new User(UUID.randomUUID(), "Test");
    user.setBio("Bio");
    user.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
    user.setGender(User.Gender.MALE);
    user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
    user.setLocation(32.0, 34.0);
    user.setMaxDistanceKm(50);
    user.setAgeRange(20, 40);
    user.addPhotoUrl("photo.jpg");
    user.activate();
    return user;
  }
}
