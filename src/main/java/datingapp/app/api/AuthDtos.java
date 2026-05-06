package datingapp.app.api;

import datingapp.app.usecase.auth.AuthUseCases;
import java.time.LocalDate;

final class AuthDtos {
    private AuthDtos() {}

    /** Request body for signup. */
    static record SignupRequest(String email, String password, LocalDate dateOfBirth, String name) {}

    /** Request body for login. */
    static record LoginRequest(String email, String password) {}

    /** Request body for refresh/logout token flows. */
    static record RefreshTokenRequest(String refreshToken) {}

    /** Authenticated user DTO. */
    static record AuthUserDto(java.util.UUID id, String email, String displayName, String profileCompletionState) {
        static AuthUserDto from(AuthUseCases.AuthUser user) {
            return new AuthUserDto(user.id(), user.email(), user.displayName(), user.profileCompletionState());
        }
    }

    /** Auth session response. */
    static record AuthResponse(String accessToken, String refreshToken, int expiresInSeconds, AuthUserDto user) {
        static AuthResponse from(AuthUseCases.AuthSession session) {
            return new AuthResponse(
                    session.accessToken(),
                    session.refreshToken(),
                    session.expiresInSeconds(),
                    AuthUserDto.from(session.user()));
        }
    }
}
