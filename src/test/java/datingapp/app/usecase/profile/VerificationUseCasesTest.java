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
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        AppConfig config = AppConfig.defaults();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases =
                new VerificationUseCases(users, trustSafetyService(users, trustSafetyStorage, interactions, config));

        var result = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "verified@example.com"));

        assertTrue(result.success());
        User stored = users.get(user.getId()).orElseThrow();
        assertEquals("verified@example.com", stored.getEmail());
        assertEquals("123456", result.data().generatedCode());
        assertEquals("123456", stored.getVerificationCode());
        assertEquals(VerificationMethod.EMAIL, stored.getVerificationMethod());
    }

    @Test
    @DisplayName("startVerification rejects blank contact details")
    void startVerificationRejectsBlankContactDetails() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        AppConfig config = AppConfig.defaults();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases =
                new VerificationUseCases(users, trustSafetyService(users, trustSafetyStorage, interactions, config));

        var result = useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.PHONE, "   "));

        assertFalse(result.success());
        assertEquals("Phone required.", result.error().message());
    }

    @Test
    @DisplayName("confirmVerification marks the user verified for a matching code")
    void confirmVerificationMarksTheUserVerifiedForMatchingCode() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        AppConfig config = AppConfig.defaults();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases =
                new VerificationUseCases(users, trustSafetyService(users, trustSafetyStorage, interactions, config));
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
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        AppConfig config = AppConfig.defaults();

        User user = createActiveUser("Verifier");
        users.save(user);

        VerificationUseCases useCases =
                new VerificationUseCases(users, trustSafetyService(users, trustSafetyStorage, interactions, config));
        useCases.startVerification(new VerificationUseCases.StartVerificationCommand(
                UserContext.cli(user.getId()), VerificationMethod.EMAIL, "verified@example.com"));

        var result = useCases.confirmVerification(
                new VerificationUseCases.ConfirmVerificationCommand(UserContext.cli(user.getId()), "000000"));

        assertFalse(result.success());
        assertEquals("Incorrect code.", result.error().message());
    }

    private static datingapp.core.matching.TrustSafetyService trustSafetyService(
            TestStorages.Users users,
            TestStorages.TrustSafety trustSafetyStorage,
            TestStorages.Interactions interactions,
            AppConfig config) {
        return datingapp.core.matching.TrustSafetyService.builder(trustSafetyStorage, interactions, users, config)
                .random(fixedVerificationRandom())
                .build();
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
