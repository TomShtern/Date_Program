package datingapp.app.usecase.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies Clerk session tokens offline: RS256 signature against Clerk's published JWKS, then issuer, expiry,
 * not-before, subject and (when present) authorized party.
 *
 * <p>Time-based claims are checked here against {@link AppClock} rather than by Nimbus, whose default claims verifier
 * reads the system clock and would make tests with a pinned clock impossible.
 */
public final class ClerkJwtVerifier implements AccessTokenVerifier {

    private static final Logger logger = LoggerFactory.getLogger(ClerkJwtVerifier.class);

    private final String issuer;
    private final List<String> authorizedParties;
    private final long clockSkewSeconds;
    private final DefaultJWTProcessor<SecurityContext> processor;

    public ClerkJwtVerifier(AppConfig.AuthConfig config) {
        this(config, JWKSourceBuilder.DEFAULT_RATE_LIMIT_MIN_INTERVAL);
    }

    /** Test seam: lets a test shorten the minimum interval between JWKS refetches so key rotation is observable. */
    ClerkJwtVerifier(AppConfig.AuthConfig config, long jwksRefetchMinIntervalMillis) {
        Objects.requireNonNull(config, "config cannot be null");
        if (!config.clerkConfigured()) {
            throw new IllegalArgumentException("A Clerk issuer is required to verify tokens");
        }
        this.issuer = config.clerkIssuer();
        this.authorizedParties = config.clerkAuthorizedParties();
        this.clockSkewSeconds = config.clockSkewSeconds();

        JWKSource<SecurityContext> keys = JWKSourceBuilder.<SecurityContext>create(toUrl(config.clerkJwksUrl()))
                .retrying(true)
                .rateLimited(jwksRefetchMinIntervalMillis)
                .build();
        this.processor = new DefaultJWTProcessor<>();
        // RS256 only: HS256 and "none" tokens are rejected before any claim is looked at.
        this.processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        this.processor.setJWTClaimsSetVerifier(ClerkJwtVerifier::skipNimbusClaimChecks);
    }

    /** Replaces Nimbus's system-clock claim checks; {@link #checkClaims} does them against {@link AppClock}. */
    private static void skipNimbusClaimChecks(
            JWTClaimsSet claims, SecurityContext context) { // NOPMD UnusedFormalParameter - Nimbus callback signature
        // intentionally empty
    }

    @Override
    public Optional<VerifiedToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = processor.process(token, null);
            return checkClaims(claims);
        } catch (ParseException | BadJOSEException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Rejected Clerk token: {}", e.getMessage());
            }
            return Optional.empty();
        } catch (JOSEException e) {
            // Includes a JWKS endpoint that cannot be reached; the caller sees an ordinary 401.
            if (logger.isWarnEnabled()) {
                logger.warn("Could not verify Clerk token: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    private Optional<VerifiedToken> checkClaims(JWTClaimsSet claims) throws ParseException {
        if (!issuer.equals(claims.getIssuer())) {
            return reject("issuer mismatch");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            return reject("missing subject");
        }
        if (claims.getExpirationTime() == null) {
            return reject("missing exp");
        }
        Instant now = AppClock.now();
        if (!now.minusSeconds(clockSkewSeconds)
                .isBefore(claims.getExpirationTime().toInstant())) {
            return reject("expired");
        }
        if (claims.getNotBeforeTime() != null
                && now.plusSeconds(clockSkewSeconds)
                        .isBefore(claims.getNotBeforeTime().toInstant())) {
            return reject("not yet valid");
        }
        String authorizedParty = claims.getStringClaim("azp");
        if (!authorizedParties.isEmpty() && authorizedParty != null && !authorizedParties.contains(authorizedParty)) {
            return reject("azp not authorized");
        }
        return Optional.of(new VerifiedToken(subject, claims.getStringClaim("email")));
    }

    private static Optional<VerifiedToken> reject(String reason) {
        logger.debug("Rejected Clerk token: {}", reason);
        return Optional.empty();
    }

    private static URL toUrl(String jwksUrl) {
        try {
            return URI.create(jwksUrl).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Clerk JWKS URL: " + jwksUrl, e);
        }
    }
}
