package datingapp.ui.viewmodel;

import datingapp.app.support.UserPresentationSupport;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.PendingLikersQuery;
import datingapp.app.usecase.matching.MatchingUseCases.SentLikesQuery;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.ListBlockedUsersQuery;
import datingapp.core.AppConfig;
import datingapp.core.TextUtil;
import datingapp.core.matching.MatchingService.PendingLiker;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads and maps match-screen data from the use-case layer. */
final class MatchListLoader {
    private final MatchingUseCases matchingUseCases;
    private final ProfileUseCases profileUseCases;
    private final SocialUseCases socialUseCases;
    private final AppConfig config;

    MatchListLoader(
            MatchingUseCases matchingUseCases,
            ProfileUseCases profileUseCases,
            SocialUseCases socialUseCases,
            AppConfig config) {
        this.matchingUseCases = Objects.requireNonNull(matchingUseCases, "matchingUseCases cannot be null");
        this.profileUseCases = Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
        this.socialUseCases = Objects.requireNonNull(socialUseCases, "socialUseCases cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    MatchPageResult loadMatchPage(UUID userId, int pageSize, int offset) {
        var matchesResult = matchingUseCases.listPagedMatches(
                new MatchingUseCases.ListPagedMatchesQuery(UserContext.ui(userId), pageSize, offset));
        if (!matchesResult.success()) {
            throw new IllegalStateException(
                    matchesResult.error() != null ? matchesResult.error().message() : "Failed to load matches");
        }

        var page = matchesResult.data().page();
        Map<UUID, User> otherUsers = matchesResult.data().usersById();

        List<MatchesViewModel.MatchCardData> cards = new ArrayList<>();
        for (Match match : page.items()) {
            UUID otherUserId = match.getOtherUser(userId);
            User otherUser = otherUsers.get(otherUserId);
            if (otherUser != null) {
                MatchQualitySummary qualitySummary = resolveMatchQualitySummary(userId, match);
                cards.add(new MatchesViewModel.MatchCardData(
                        match.getId(),
                        otherUser.getId(),
                        otherUser.getName(),
                        TextUtil.formatTimeAgo(match.getCreatedAt()),
                        match.getCreatedAt(),
                        qualitySummary.score(),
                        qualitySummary.label()));
            }
        }
        return new MatchPageResult(List.copyOf(cards), page.totalCount(), page.hasMore());
    }

    List<MatchesViewModel.LikeCardData> loadReceivedLikes(UUID userId) {
        List<MatchesViewModel.LikeCardData> received = new ArrayList<>();
        var result = matchingUseCases.pendingLikers(new PendingLikersQuery(UserContext.ui(userId)));
        if (!result.success()) {
            throw new IllegalStateException(
                    result.error() != null ? result.error().message() : "Failed to load likes received");
        }

        for (PendingLiker pending : result.data()) {
            User liker = pending.user();
            if (liker != null && liker.getState() == UserState.ACTIVE) {
                int age = UserPresentationSupport.safeAge(liker, config.safety().userTimeZone());
                received.add(new MatchesViewModel.LikeCardData(
                        liker.getId(),
                        null,
                        liker.getName(),
                        age,
                        summarizeBio(liker),
                        TextUtil.formatTimeAgo(pending.likedAt()),
                        pending.likedAt()));
            }
        }
        received.sort(likeTimeComparator());
        return List.copyOf(received);
    }

    List<MatchesViewModel.LikeCardData> loadSentLikes(UUID userId) {
        var blockedUsersResult = socialUseCases.listBlockedUsers(new ListBlockedUsersQuery(UserContext.ui(userId)));
        if (!blockedUsersResult.success()) {
            throw new IllegalStateException(
                    blockedUsersResult.error() != null
                            ? blockedUsersResult.error().message()
                            : "Failed to load blocked users");
        }

        Set<UUID> blockedUserIds = blockedUsersResult.data().stream()
                .map(SocialUseCases.BlockedUserSummary::userId)
                .collect(Collectors.toSet());
        var sentLikesResult = matchingUseCases.sentLikes(new SentLikesQuery(UserContext.ui(userId)));
        if (!sentLikesResult.success()) {
            throw new IllegalStateException(
                    sentLikesResult.error() != null ? sentLikesResult.error().message() : "Failed to load sent likes");
        }

        List<MatchingUseCases.SentLikeSnapshot> sentLikeSnapshots = sentLikesResult.data().stream()
                .filter(sentLike -> !blockedUserIds.contains(sentLike.userId()))
                .toList();
        Map<UUID, User> potentialUsers = loadUsersByIds(sentLikeSnapshots.stream()
                .map(MatchingUseCases.SentLikeSnapshot::userId)
                .toList());
        List<MatchesViewModel.LikeCardData> sent = new ArrayList<>();

        for (MatchingUseCases.SentLikeSnapshot sentLike : sentLikeSnapshots) {
            User otherUser = potentialUsers.get(sentLike.userId());
            if (otherUser != null && otherUser.getState() == UserState.ACTIVE) {
                int age = UserPresentationSupport.safeAge(
                        otherUser, config.safety().userTimeZone());
                sent.add(new MatchesViewModel.LikeCardData(
                        otherUser.getId(),
                        sentLike.likeId(),
                        otherUser.getName(),
                        age,
                        summarizeBio(otherUser),
                        TextUtil.formatTimeAgo(sentLike.likedAt()),
                        sentLike.likedAt()));
            }
        }
        sent.sort(likeTimeComparator());
        return List.copyOf(sent);
    }

    private MatchQualitySummary resolveMatchQualitySummary(UUID userId, Match match) {
        var quality =
                matchingUseCases.matchQuality(new MatchingUseCases.MatchQualityQuery(UserContext.ui(userId), match));
        if (!quality.success()) {
            return MatchQualitySummary.empty();
        }
        return new MatchQualitySummary(
                quality.data().compatibilityScore(), quality.data().getCompatibilityLabel());
    }

    private Map<UUID, User> loadUsersByIds(List<UUID> userIds) {
        var usersResult = profileUseCases.getUsersByIds(new ProfileUseCases.GetUsersByIdsQuery(userIds));
        if (!usersResult.success()) {
            throw new IllegalStateException(
                    usersResult.error() != null ? usersResult.error().message() : "Failed to load users");
        }
        return usersResult.data();
    }

    private Comparator<MatchesViewModel.LikeCardData> likeTimeComparator() {
        return (left, right) -> {
            if (left.likedAt() == null && right.likedAt() == null) {
                return 0;
            }
            if (left.likedAt() == null) {
                return 1;
            }
            if (right.likedAt() == null) {
                return -1;
            }
            return right.likedAt().compareTo(left.likedAt());
        };
    }

    private static String summarizeBio(User user) {
        return UserPresentationSupport.fallbackBio(user, "No bio yet.", 77);
    }

    record MatchPageResult(List<MatchesViewModel.MatchCardData> cards, int totalCount, boolean hasMore) {}

    private record MatchQualitySummary(Integer score, String label) {
        private static MatchQualitySummary empty() {
            return new MatchQualitySummary(null, null);
        }
    }
}
