package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VerificationUseCases")
class VerificationUseCasesTest {

    @Test
    @DisplayName("startVerification persists contact details and generated code")
    void startVerificationPersistsContactDetailsAndGeneratedCode() {
        TestStorages.Users users = new TestStorages.Users();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);

        var result = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "verified@example.com"));

        assertTrue(result.success());
        User stored = users.get(user.getId()).orElseThrow();
        assertEquals("verified@example.com", stored.getEmail());
        assertEquals("123456", result.data().generatedCode());
        assertEquals(VerificationUseCases.hashCode("123456"), stored.getVerificationCode());
        assertEquals(64, stored.getVerificationCode().length());
        assertEquals(VerificationMethod.EMAIL, stored.getVerificationMethod());
    }

    @Test
    @DisplayName("startVerification rejects blank contact details")
    void startVerificationRejectsBlankContactDetails() {
        TestStorages.Users users = new TestStorages.Users();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);

        var result = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.PHONE, "   "));

        assertFalse(result.success());
        assertEquals("Phone required.", result.error().message());
    }

    @Test
    @DisplayName("startVerification rejects malformed contact details as validation failures")
    void startVerificationRejectsMalformedContactDetailsAsValidationFailures() {
        TestStorages.Users users = new TestStorages.Users();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);

        var result = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "not-an-email"));

        assertFalse(result.success());
        assertEquals("Invalid email format", result.error().message());
    }

    @Test
    @DisplayName("confirmVerification marks the user verified for a matching code")
    void confirmVerificationMarksTheUserVerifiedForMatchingCode() {
        TestStorages.Users users = new TestStorages.Users();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);
        useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "verified@example.com"));

        var result = useCases.confirmVerification(
                new VerificationUseCases.ConfirmVerificationCommand(UserContext.cli(user.getId()), "123456"));

        assertTrue(result.success());
        assertTrue(users.get(user.getId()).orElseThrow().isVerified());
    }

    @Test
    @DisplayName("confirmVerification rejects an incorrect code")
    void confirmVerificationRejectsIncorrectCode() {
        TestStorages.Users users = new TestStorages.Users();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);
        useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "verified@example.com"));

        var result = useCases.confirmVerification(
                new VerificationUseCases.ConfirmVerificationCommand(UserContext.cli(user.getId()), "000000"));

        assertFalse(result.success());
        assertEquals("Incorrect code.", result.error().message());
    }

    @Test
    @DisplayName("startVerification enforces resend cooldown")
    void startVerificationEnforcesResendCooldown() {
        TestStorages.Users users = new TestStorages.Users();
        User user = createActiveUser("Cooldown");
        users.save(user);
        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);
        var first = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "c@example.com"));
        assertTrue(first.success());
        var second = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "c@example.com"));
        assertFalse(second.success());
        assertEquals(
                datingapp.app.usecase.common.UseCaseError.Code.VALIDATION,
                second.error().code());
    }

    @Test
    @DisplayName("confirmVerification locks out after max attempts")
    void confirmVerificationLocksOutAfterMaxAttempts() {
        TestStorages.Users users = new TestStorages.Users();
        User user = createActiveUser("Lockout");
        users.save(user);
        VerificationUseCases useCases = new VerificationUseCases(
                users, fixedVerificationRandom(), VerificationUseCases.DEFAULT_VERIFICATION_TTL);
        useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "l@example.com"));
        for (int i = 0; i < VerificationUseCases.MAX_ATTEMPTS; i++) {
            useCases.confirmVerification(
                    new VerificationUseCases.ConfirmVerificationCommand(UserContext.cli(user.getId()), "000000"));
        }
        var locked = useCases.confirmVerification(
                new VerificationUseCases.ConfirmVerificationCommand(UserContext.cli(user.getId()), "000000"));
        assertFalse(locked.success());
        assertEquals(
                datingapp.app.usecase.common.UseCaseError.Code.RATE_LIMITED,
                locked.error().code());
    }

    @Test
    @DisplayName("startVerification cannot be used to clear an active lockout")
    void startVerificationIsRefusedWhileLockedOut() {
        TestStorages.Users users = new TestStorages.Users();
        User user = createActiveUser("LockedResend");
        users.save(user);
        VerificationUseCases useCases = new VerificationUseCases(
                users,
                fixedVerificationRandom(),
                VerificationUseCases.DEFAULT_VERIFICATION_TTL,
                (method, contact, code) -> {},
                java.time.Duration.ZERO,
                VerificationUseCases.MAX_ATTEMPTS,
                java.time.Duration.ofMinutes(15));
        var start = new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "lr@example.com");
        assertTrue(useCases.startVerification(start).success());
        for (int i = 0; i < VerificationUseCases.MAX_ATTEMPTS; i++) {
            useCases.confirmVerification(
                    new VerificationUseCases.ConfirmVerificationCommand(UserContext.cli(user.getId()), "000000"));
        }

        var resend = useCases.startVerification(start);

        assertFalse(resend.success());
        assertEquals(
                datingapp.app.usecase.common.UseCaseError.Code.RATE_LIMITED,
                resend.error().code());
        assertTrue(users.get(user.getId()).orElseThrow().getVerificationLockedUntil() != null);
    }

    private static SecureRandom fixedVerificationRandom() {
        return new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 123456;
            }
        };
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(LocalDate.of(1995, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(32.0853, 34.7818);
        user.addPhotoUrl("http://example.com/photo.jpg");
        user.setBio("Verification test user");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
