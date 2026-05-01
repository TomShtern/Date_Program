package datingapp.app.usecase.auth;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenService {

    String issueAccessToken(AuthUseCases.AuthIdentity identity, Instant issuedAt);

    Optional<AuthUseCases.AuthIdentity> validateAccessToken(String token, Instant now);
}
