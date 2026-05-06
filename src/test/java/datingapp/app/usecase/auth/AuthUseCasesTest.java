package datingapp.app.usecase.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthUseCases")
class AuthUseCasesTest {

    @Test
    @DisplayName("signup stores BCrypt hashes with cost factor 12")
    void signupStoresBcryptHashesWithCostFactorTwelve() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Auth authStorage = new TestStorages.Auth();
        AppConfig config = AppConfig.defaults();
        AuthUseCases useCases = new AuthUseCases(config, userStorage, authStorage, new AuthTokenService(config.auth()));

        var result = useCases.signup(new AuthUseCases.SignupCommand(
                "alpha@example.com", "correct horse battery staple", LocalDate.of(1998, 4, 30), "Alpha"));
        assertTrue(result.success());

        User createdUser = userStorage.findAll().getFirst();
        String passwordHash = authStorage.findPasswordHash(createdUser.getId()).orElseThrow();
        assertTrue(
                passwordHash.startsWith("$2a$12$")
                        || passwordHash.startsWith("$2b$12$")
                        || passwordHash.startsWith("$2y$12$"),
                passwordHash);
    }

    @Test
    @DisplayName("auth user falls back to needs_unknown when profile is incomplete but no missing fields are exposed")
    void authUserFallsBackToNeedsUnknownWhenProfileLooksInconsistent() {
        User inconsistentUser = new User(UUID.randomUUID(), "Ghost") {
            @Override
            public boolean isComplete() {
                return false;
            }

            @Override
            public List<String> getMissingProfileFields() {
                return List.of();
            }
        };
        inconsistentUser.setEmail("ghost@example.com");

        AuthUseCases.AuthUser authUser = AuthUseCases.AuthUser.from(inconsistentUser);

        assertEquals("needs_unknown", authUser.profileCompletionState());
    }

    @Test
    @DisplayName("authenticateAccessToken resolves the current stored user identity")
    void authenticateAccessTokenResolvesCurrentStoredUserIdentity() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.Auth authStorage = new TestStorages.Auth();
        AppConfig config = AppConfig.defaults();
        AuthUseCases useCases = new AuthUseCases(config, userStorage, authStorage, new AuthTokenService(config.auth()));

        User user = new User(UUID.randomUUID(), "Alpha");
        user.setEmail("alpha@example.com");
        userStorage.save(user);

        String token = new AuthTokenService(config.auth())
                .issueAccessToken(
                        new AuthUseCases.AuthIdentity(user.getId(), "ignored@example.com"), java.time.Instant.now());

        AuthUseCases.AuthIdentity identity =
                useCases.authenticateAccessToken(token).orElseThrow();

        assertEquals(user.getId(), identity.userId());
        assertEquals("alpha@example.com", identity.email());
    }

    @Test
    @DisplayName("issue and validate access token preserves the email claim")
    void issueAndValidateAccessTokenPreservesTheEmailClaim() {
        AppConfig config = AppConfig.defaults();
        AuthTokenService tokenService = new AuthTokenService(config.auth());
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-05-06T00:00:00Z");

        String token =
                tokenService.issueAccessToken(new AuthUseCases.AuthIdentity(userId, "alpha@example.com"), issuedAt);

        AuthUseCases.AuthIdentity identity =
                tokenService.validateAccessToken(token, issuedAt.plusSeconds(1)).orElseThrow();

        assertEquals(userId, identity.userId());
        assertEquals("alpha@example.com", identity.email());
    }
}
