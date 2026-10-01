package datingapp.app.usecase.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import datingapp.app.usecase.auth.AccessTokenVerifier.VerifiedToken;
import datingapp.core.AppConfig;
import datingapp.core.testutil.TestClock;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClerkJwtVerifier")
class ClerkJwtVerifierTest {

    private static final String ISSUER = "https://clerk.example.test";
    private static final String SUBJECT = "user_2abc";
    private static final int SKEW_SECONDS = 5;
    /** Deliberately far from the real clock: proves expiry is judged by AppClock, not by Nimbus's own clock. */
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private HttpServer jwksServer;
    private volatile String jwksJson;
    private RSAKey signingKey;
    private ClerkJwtVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = newKey("key-1");
        jwksJson = jwksOf(signingKey);
        jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();
        verifier = verifierWithParties(List.of());
        TestClock.setFixed(NOW);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
        jwksServer.stop(0);
    }

    @Test
    @DisplayName("accepts a valid token and returns subject and optional email")
    void acceptsValidToken() throws Exception {
        String token = sign(signingKey, claims().claim("email", "alpha@example.com"));

        Optional<VerifiedToken> result = verifier.verify(token);

        assertEquals(new VerifiedToken(SUBJECT, "alpha@example.com"), result.orElseThrow());
    }

    @Test
    @DisplayName("email is optional")
    void emailIsOptional() throws Exception {
        VerifiedToken result = verifier.verify(sign(signingKey, claims())).orElseThrow();

        assertNull(result.email());
    }

    @Test
    @DisplayName("expiry is judged against AppClock, within the configured skew")
    void expiryUsesAppClockWithSkew() throws Exception {
        assertTrue(verifier.verify(sign(signingKey, claims().expirationTime(at(NOW.minusSeconds(3)))))
                .isPresent());
        assertTrue(verifier.verify(sign(signingKey, claims().expirationTime(at(NOW.minusSeconds(10)))))
                .isEmpty());
    }

    @Test
    @DisplayName("not-before is judged against AppClock, within the configured skew")
    void notBeforeUsesAppClockWithSkew() throws Exception {
        assertTrue(verifier.verify(sign(signingKey, claims().notBeforeTime(at(NOW.plusSeconds(3)))))
                .isPresent());
        assertTrue(verifier.verify(sign(signingKey, claims().notBeforeTime(at(NOW.plusSeconds(60)))))
                .isEmpty());
    }

    @Test
    @DisplayName("rejects a token with no expiry")
    void rejectsMissingExpiry() throws Exception {
        JWTClaimsSet.Builder noExp = new JWTClaimsSet.Builder().issuer(ISSUER).subject(SUBJECT);

        assertTrue(verifier.verify(sign(signingKey, noExp)).isEmpty());
    }

    @Test
    @DisplayName("rejects a different issuer")
    void rejectsWrongIssuer() throws Exception {
        assertTrue(verifier.verify(sign(signingKey, claims().issuer("https://other.example.test")))
                .isEmpty());
    }

    @Test
    @DisplayName("rejects a missing or blank subject")
    void rejectsMissingSubject() throws Exception {
        JWTClaimsSet.Builder noSub = new JWTClaimsSet.Builder().issuer(ISSUER).expirationTime(at(NOW.plusSeconds(60)));

        assertTrue(verifier.verify(sign(signingKey, noSub)).isEmpty());
        assertTrue(verifier.verify(sign(signingKey, claims().subject(" "))).isEmpty());
    }

    @Test
    @DisplayName("rejects a token signed by a key that is not in the JWKS")
    void rejectsUnknownKey() throws Exception {
        RSAKey stranger = newKey("key-unknown");

        assertTrue(verifier.verify(sign(stranger, claims())).isEmpty());
    }

    @Test
    @DisplayName("rejects a token whose signature was tampered with")
    void rejectsTamperedToken() throws Exception {
        String token = sign(signingKey, claims());
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"iss\":\"" + ISSUER + "\",\"sub\":\"user_attacker\",\"exp\":"
                                + NOW.plusSeconds(60).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));

        assertTrue(
                verifier.verify(parts[0] + "." + forgedPayload + "." + parts[2]).isEmpty());
    }

    @Test
    @DisplayName("rejects an HS256 token signed with the public key as secret (algorithm confusion)")
    void rejectsAlgorithmConfusion() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID(signingKey.getKeyID())
                        .build(),
                claims().build());
        jwt.sign(new MACSigner(signingKey.toRSAPublicKey().getEncoded()));

        assertTrue(verifier.verify(jwt.serialize()).isEmpty());
    }

    @Test
    @DisplayName("rejects an unsigned (alg none) token")
    void rejectsUnsignedToken() {
        assertTrue(verifier.verify(new PlainJWT(claims().build()).serialize()).isEmpty());
    }

    @Test
    @DisplayName("rejects blank, null and garbage input")
    void rejectsGarbage() {
        assertTrue(verifier.verify(null).isEmpty());
        assertTrue(verifier.verify("  ").isEmpty());
        assertTrue(verifier.verify("not.a.jwt").isEmpty());
    }

    @Test
    @DisplayName("picks up a rotated key when a token arrives with an unknown kid")
    void picksUpRotatedKey() throws Exception {
        assertTrue(verifier.verify(sign(signingKey, claims())).isPresent());

        RSAKey rotated = newKey("key-2");
        jwksJson = jwksOf(rotated);
        Thread.sleep(20); // let the 1 ms refetch rate limit elapse

        assertTrue(verifier.verify(sign(rotated, claims())).isPresent());
    }

    @Test
    @DisplayName("when configured, azp must match, and a token without azp is still accepted")
    void authorizedPartyIsChecked() throws Exception {
        ClerkJwtVerifier strict = verifierWithParties(List.of("https://app.example.test"));

        assertTrue(strict.verify(sign(signingKey, claims().claim("azp", "https://app.example.test")))
                .isPresent());
        assertTrue(strict.verify(sign(signingKey, claims().claim("azp", "https://evil.example.test")))
                .isEmpty());
        assertTrue(strict.verify(sign(signingKey, claims())).isPresent());
    }

    @Test
    @DisplayName("an unreachable JWKS endpoint rejects the token instead of throwing")
    void unreachableJwksRejects() throws Exception {
        String token = sign(signingKey, claims());
        ClerkJwtVerifier fresh = verifierWithParties(List.of());
        jwksServer.stop(0);

        assertTrue(fresh.verify(token).isEmpty());
    }

    @Test
    @DisplayName("construction requires a configured issuer")
    void constructionRequiresIssuer() {
        AppConfig.AuthConfig unconfigured = new AppConfig.AuthConfig("", "", List.of(), SKEW_SECONDS);

        assertThrows(IllegalArgumentException.class, () -> new ClerkJwtVerifier(unconfigured));
    }

    private ClerkJwtVerifier verifierWithParties(List<String> parties) {
        String jwksUrl = "http://localhost:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
        return new ClerkJwtVerifier(new AppConfig.AuthConfig(ISSUER, jwksUrl, parties, SKEW_SECONDS), 1L);
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(SUBJECT)
                .issueTime(at(NOW))
                .notBeforeTime(at(NOW.minusSeconds(10)))
                .expirationTime(at(NOW.plusSeconds(60)));
    }

    private static Date at(Instant instant) {
        return Date.from(instant);
    }

    private static RSAKey newKey(String keyId) throws JOSEException {
        return new RSAKeyGenerator(2048).keyID(keyId).generate();
    }

    private static String jwksOf(RSAKey key) {
        return new JWKSet(key.toPublicJWK()).toString();
    }

    private static String sign(RSAKey key, JWTClaimsSet.Builder claims) throws JOSEException, IOException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
