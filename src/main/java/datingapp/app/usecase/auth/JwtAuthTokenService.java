package datingapp.app.usecase.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtAuthTokenService implements AuthTokenService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final AppConfig.AuthConfig config;

    public JwtAuthTokenService(AppConfig.AuthConfig config) {
        this.config = config;
    }

    @Override
    public String issueAccessToken(AuthUseCases.AuthIdentity identity, Instant issuedAt) {
        long issuedAtSeconds = issuedAt.getEpochSecond();
        long expiresAtSeconds = issuedAtSeconds + config.accessTokenTtlSeconds();
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "sub", identity.userId().toString(),
                "email", identity.email(),
                "iss", config.tokenIssuer(),
                "iat", issuedAtSeconds,
                "exp", expiresAtSeconds));
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    @Override
    public Optional<AuthUseCases.AuthIdentity> validateAccessToken(String token, Instant now) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expectedSignature = signBytes(signingInput);
            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                return Optional.empty();
            }

            Map<String, Object> payload = OBJECT_MAPPER.readValue(decode(parts[1]), MAP_TYPE);
            if (!config.tokenIssuer().equals(String.valueOf(payload.get("iss")))) {
                return Optional.empty();
            }
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (expiresAt <= now.getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new AuthUseCases.AuthIdentity(
                    UUID.fromString(String.valueOf(payload.get("sub"))), String.valueOf(payload.get("email"))));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encode JWT payload", ex);
        }
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String sign(String signingInput) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signBytes(signingInput));
    }

    private byte[] signBytes(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }
}
