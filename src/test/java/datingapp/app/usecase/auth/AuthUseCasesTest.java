package datingapp.app.usecase.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.auth.AccessTokenVerifier.VerifiedToken;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestAccessTokenVerifier;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthUseCases")
class AuthUseCasesTest {

    private static final String CLERK_ID = "user_clerk_alpha";

    private final TestStorages.Users userStorage = new TestStorages.Users();
    private final TestStorages.Auth authStorage = new TestStorages.Auth();
    private final AuthUseCases useCases = new AuthUseCases(userStorage, authStorage, new TestAccessTokenVerifier());

    private static String token(String clerkId) {
        return TestAccessTokenVerifier.tokenFor(clerkId);
    }

    @Test
    @DisplayName("first session call creates an incomplete profile with no email or birth date, the second reuses it")
    void provisionSessionCreatesOnceThenReuses() {
        var first = useCases.provisionSession(token(CLERK_ID));

        assertTrue(first.success());
        assertTrue(first.data().created());
        assertEquals(1, userStorage.findAll().size());
        User created = userStorage.findAll().getFirst();
        assertEquals(UserState.INCOMPLETE, created.getState());
        assertNull(created.getEmail());
        assertNull(created.getBirthDate());
        assertEquals(created.getId(), first.data().user().id());
        assertNull(first.data().user().displayName());
        assertTrue(first.data().user().profileCompletionState().startsWith("needs_"));
        // Discovery only lists ACTIVE users, and an incomplete profile cannot be activated.
        assertThrows(IllegalStateException.class, created::activate);

        var second = useCases.provisionSession(token(CLERK_ID));

        assertTrue(second.success());
        assertFalse(second.data().created());
        assertEquals(created.getId(), second.data().user().id());
        assertEquals(1, userStorage.findAll().size());
    }

    @Test
    @DisplayName("different Clerk users get different profiles")
    void differentClerkUsersGetDifferentProfiles() {
        UUID alpha = useCases.provisionSession(token("user_a")).data().user().id();
        UUID beta = useCases.provisionSession(token("user_b")).data().user().id();

        assertNotEquals(alpha, beta);
        assertEquals(2, userStorage.findAll().size());
    }

    @Test
    @DisplayName("provisionSession rejects a token the verifier does not accept")
    void provisionSessionRejectsInvalidToken() {
        var result = useCases.provisionSession("not-a-valid-token");

        assertFalse(result.success());
        assertEquals(UseCaseError.Code.UNAUTHORIZED, result.error().code());
        assertTrue(userStorage.findAll().isEmpty());
    }

    @Test
    @DisplayName("session response echoes the optional email claim without storing it")
    void sessionEchoesEmailClaimWithoutStoringIt() {
        AuthUseCases withEmail = new AuthUseCases(
                userStorage, authStorage, ignored -> Optional.of(new VerifiedToken(CLERK_ID, "alpha@example.com")));

        var result = withEmail.provisionSession("anything");

        assertEquals("alpha@example.com", result.data().user().email());
        assertNull(userStorage.findAll().getFirst().getEmail());
    }

    @Test
    @DisplayName("concurrent first calls for one Clerk user create exactly one profile")
    void concurrentFirstCallsCreateOneProfile() throws Exception {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return useCases.provisionSession(token(CLERK_ID))
                            .data()
                            .user()
                            .id();
                }));
            }
            start.countDown();
            Set<UUID> ids = new HashSet<>();
            for (Future<UUID> future : futures) {
                ids.add(future.get());
            }
            assertEquals(1, ids.size());
            assertEquals(1, userStorage.findAll().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("losing the link race to another process returns the winner's profile")
    void losingLinkRaceReturnsWinnersProfile() {
        User winner = new User(UUID.randomUUID(), "Winner");
        userStorage.save(winner);
        TestStorages.Auth racing = new TestStorages.Auth() {
            @Override
            public boolean linkClerkId(String clerkUserId, UUID userId, Instant createdAt) {
                // Another process links the Clerk id between our lookup and our insert.
                super.linkClerkId(clerkUserId, winner.getId(), createdAt);
                return super.linkClerkId(clerkUserId, userId, createdAt);
            }
        };
        AuthUseCases racingUseCases = new AuthUseCases(userStorage, racing, new TestAccessTokenVerifier());

        var result = racingUseCases.provisionSession(token(CLERK_ID));

        assertTrue(result.success());
        assertFalse(result.data().created());
        assertEquals(winner.getId(), result.data().user().id());
    }

    @Test
    @DisplayName("authenticate before provisioning reports not provisioned")
    void authenticateBeforeProvisioningThrowsNotProvisioned() {
        assertThrows(
                AuthUseCases.NotProvisionedException.class, () -> useCases.authenticateAccessToken(token(CLERK_ID)));
    }

    @Test
    @DisplayName("authenticate resolves the provisioned local user, and an invalid token is simply empty")
    void authenticateResolvesProvisionedUser() {
        UUID userId = useCases.provisionSession(token(CLERK_ID)).data().user().id();

        assertEquals(
                userId,
                useCases.authenticateAccessToken(token(CLERK_ID)).orElseThrow().userId());
        assertTrue(useCases.authenticateAccessToken("garbage").isEmpty());
        assertTrue(useCases.authenticateAccessToken(null).isEmpty());
    }

    @Test
    @DisplayName("banned users are rejected by both authenticate and provision")
    void bannedUsersAreRejected() {
        UUID userId = useCases.provisionSession(token(CLERK_ID)).data().user().id();
        User user = userStorage.get(userId).orElseThrow();
        user.ban();
        userStorage.save(user);

        assertTrue(useCases.authenticateAccessToken(token(CLERK_ID)).isEmpty());
        var result = useCases.provisionSession(token(CLERK_ID));
        assertFalse(result.success());
        assertEquals(UseCaseError.Code.UNAUTHORIZED, result.error().code());
    }

    @Test
    @DisplayName("after the profile is deleted the Clerk user is not provisioned and the next session starts fresh")
    void deletedProfileIsReplacedByFreshOneOnNextSession() {
        UUID oldId = useCases.provisionSession(token(CLERK_ID)).data().user().id();
        User user = userStorage.get(oldId).orElseThrow();
        user.markDeleted(AppClock.now());
        userStorage.save(user);

        assertThrows(
                AuthUseCases.NotProvisionedException.class, () -> useCases.authenticateAccessToken(token(CLERK_ID)));

        var again = useCases.provisionSession(token(CLERK_ID));
        assertTrue(again.success());
        assertTrue(again.data().created());
        assertNotEquals(oldId, again.data().user().id());
        assertEquals(
                again.data().user().id(),
                useCases.authenticateAccessToken(token(CLERK_ID)).orElseThrow().userId());
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

        AuthUseCases.AuthUser authUser = AuthUseCases.AuthUser.from(inconsistentUser, null);

        assertEquals("needs_unknown", authUser.profileCompletionState());
    }
}
