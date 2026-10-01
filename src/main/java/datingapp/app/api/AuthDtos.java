package datingapp.app.api;

import datingapp.app.usecase.auth.AuthUseCases;

final class AuthDtos {
    private AuthDtos() {}

    /** Response of {@code POST /api/auth/session}: the local profile behind the Clerk session. */
    static record AuthUserDto(java.util.UUID id, String email, String displayName, String profileCompletionState) {
        static AuthUserDto from(AuthUseCases.AuthUser user) {
            return new AuthUserDto(user.id(), user.email(), user.displayName(), user.profileCompletionState());
        }
    }
}
