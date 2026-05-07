package datingapp.app.api;

import datingapp.app.usecase.social.SocialUseCases.RelationshipTransitionOutcome;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Report;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class SocialDtos {
    private SocialDtos() {}

    /** Friend request DTO for API responses. */
    static record FriendRequestDto(
            UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, String status, Instant respondedAt) {
        static FriendRequestDto from(FriendRequest request) {
            return new FriendRequestDto(
                    request.id(),
                    request.fromUserId(),
                    request.toUserId(),
                    request.createdAt(),
                    request.status().name(),
                    request.respondedAt());
        }
    }

    /** Friend request listing response. */
    static record FriendRequestsResponse(List<FriendRequestDto> friendRequests) {
        FriendRequestsResponse {
            friendRequests = friendRequests == null ? List.of() : List.copyOf(friendRequests);
        }

        static FriendRequestsResponse from(List<FriendRequest> requests) {
            return new FriendRequestsResponse(
                    requests.stream().map(FriendRequestDto::from).toList());
        }
    }

    /** Response for relationship transitions. */
    static record TransitionResponse(boolean success, UUID friendRequestId, String errorMessage) {
        static TransitionResponse from(RelationshipTransitionOutcome outcome) {
            FriendRequest request = outcome.friendRequest();
            return new TransitionResponse(true, request != null ? request.id() : null, null);
        }
    }

    /** Response for moderation operations. */
    static record ModerationResponse(boolean success, boolean alreadyHandled, String errorMessage) {}

    /** Blocked user DTO for safety-related responses. */
    static record BlockedUserDto(UUID userId, String name, String statusLabel) {
        static BlockedUserDto from(datingapp.app.usecase.social.SocialUseCases.BlockedUserSummary blockedUser) {
            return new BlockedUserDto(blockedUser.userId(), blockedUser.name(), "Blocked profile");
        }
    }

    /** Blocked users response. */
    static record BlockedUsersResponse(List<BlockedUserDto> blockedUsers) {
        BlockedUsersResponse {
            blockedUsers = blockedUsers == null ? List.of() : List.copyOf(blockedUsers);
        }

        static BlockedUsersResponse from(
                List<datingapp.app.usecase.social.SocialUseCases.BlockedUserSummary> blockedUsers) {
            return new BlockedUsersResponse(
                    blockedUsers.stream().map(BlockedUserDto::from).toList());
        }
    }

    /** Response for reporting a user. */
    static record ReportResponse(boolean success, boolean autoBanned, String errorMessage, boolean blockedByReporter) {}

    /** Request body for reporting a user. */
    static record ReportUserRequest(Report.Reason reason, String description, boolean blockUser) {}
}
