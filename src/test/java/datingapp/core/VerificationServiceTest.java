package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.PacePreferences.CommunicationStyle;
import datingapp.core.PacePreferences.DepthPreference;
import datingapp.core.PacePreferences.MessagingFrequency;
import datingapp.core.PacePreferences.TimeToFirstDate;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VerificationServiceTest {

    @Nested
    @DisplayName("Code Verification")
    class CodeVerification {

        @Test
        @DisplayName("Returns false when code expired")
        void returnsFalseWhenCodeExpired() {
            TrustSafetyService trustSafetyService = new TrustSafetyService(Duration.ofMinutes(15), new Random(123));

            User user = createActiveUser();
            user.startVerification(User.VerificationMethod.EMAIL, "123456");

            User expired = copyWithVerificationSentAt(
                    user,
                    user.getVerificationSentAt() != null
                            ? user.getVerificationSentAt().minus(Duration.ofMinutes(16))
                            : java.time.Instant.now().minus(Duration.ofMinutes(16)));

            assertFalse(trustSafetyService.verifyCode(expired, "123456"));
            assertTrue(trustSafetyService.isExpired(expired.getVerificationSentAt()));
        }

        @Test
        @DisplayName("Returns false when code mismatches")
        void returnsFalseWhenCodeMismatches() {
            TrustSafetyService trustSafetyService = new TrustSafetyService();

            User user = createActiveUser();
            user.startVerification(User.VerificationMethod.PHONE, "123456");

            assertFalse(trustSafetyService.verifyCode(user, "000000"));
            assertNotEquals(Boolean.TRUE, user.isVerified());
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
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static User copyWithVerificationSentAt(User user, java.time.Instant sentAt) {
        User.DatabaseRecord data = User.DatabaseRecord.builder()
                .id(user.getId())
                .name(user.getName())
                .bio(user.getBio())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .interestedIn(user.getInterestedIn())
                .lat(user.getLat())
                .lon(user.getLon())
                .maxDistanceKm(user.getMaxDistanceKm())
                .minAge(user.getMinAge())
                .maxAge(user.getMaxAge())
                .photoUrls(user.getPhotoUrls())
                .state(user.getState())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .interests(user.getInterests())
                .smoking(user.getSmoking())
                .drinking(user.getDrinking())
                .wantsKids(user.getWantsKids())
                .lookingFor(user.getLookingFor())
                .education(user.getEducation())
                .heightCm(user.getHeightCm())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isVerified(user.isVerified())
                .verificationMethod(user.getVerificationMethod())
                .verificationCode(user.getVerificationCode())
                .verificationSentAt(sentAt)
                .verifiedAt(user.getVerifiedAt())
                .pacePreferences(user.getPacePreferences())
                .build();

        return User.fromDatabase(data);
    }
}
