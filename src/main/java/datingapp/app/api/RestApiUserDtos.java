package datingapp.app.api;

import datingapp.core.matching.DailyPickService.DailyPick;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.Standout;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RestApiUserDtos {
    private static final String UNKNOWN_USER = "Unknown";

    private RestApiUserDtos() {}

    static record UserSummary(
            UUID id,
            String name,
            int age,
            String state,
            String primaryPhotoUrl,
            List<String> photoUrls,
            String approximateLocation,
            String summaryLine) {
        static UserSummary from(User user, ZoneId userTimeZone) {
            return from(user, userTimeZone, null);
        }

        static UserSummary from(User user, ZoneId userTimeZone, String approximateLocation) {
            UserDtoMapper.UserFields fields = UserDtoMapper.map(user, userTimeZone, null);
            return new UserSummary(
                    user.getId(),
                    user.getName(),
                    fields.age(),
                    fields.state(),
                    personPrimaryPhotoUrl(user),
                    personPhotoUrls(user),
                    approximateLocation,
                    personSummaryLine(user));
        }

        static List<UserSummary> fromUsers(List<User> users, ZoneId userTimeZone) {
            return users.stream().map(user -> from(user, userTimeZone)).toList();
        }
    }

    static record UserDetail(
            UUID id,
            String name,
            int age,
            String bio,
            String gender,
            List<String> interestedIn,
            String approximateLocation,
            int maxDistanceKm,
            List<String> photoUrls,
            String state) {
        static UserDetail from(User user, ZoneId userTimeZone, String approximateLocation) {
            UserDtoMapper.UserFields fields = UserDtoMapper.map(user, userTimeZone, approximateLocation);
            return new UserDetail(
                    user.getId(),
                    user.getName(),
                    fields.age(),
                    user.getBio(),
                    fields.gender(),
                    fields.interestedIn(),
                    fields.approximateLocation(),
                    fields.maxDistanceKm(),
                    personPhotoUrls(user),
                    fields.state());
        }
    }

    static record DailyPickDto(
            UUID userId,
            String userName,
            int userAge,
            LocalDate date,
            String reason,
            boolean alreadySeen,
            String primaryPhotoUrl,
            List<String> photoUrls,
            String approximateLocation,
            String summaryLine) {
        static DailyPickDto from(DailyPick dailyPick, ZoneId userTimeZone) {
            return from(dailyPick, userTimeZone, null);
        }

        static DailyPickDto from(DailyPick dailyPick, ZoneId userTimeZone, String approximateLocation) {
            User user = dailyPick.user();
            return new DailyPickDto(
                    user.getId(),
                    user.getName(),
                    UserDtoMapper.age(user, userTimeZone),
                    dailyPick.date(),
                    dailyPick.reason(),
                    dailyPick.alreadySeen(),
                    personPrimaryPhotoUrl(user),
                    personPhotoUrls(user),
                    approximateLocation,
                    personSummaryLine(user));
        }
    }

    static record PendingLikerDto(
            UUID userId,
            String name,
            int age,
            Instant likedAt,
            String primaryPhotoUrl,
            List<String> photoUrls,
            String approximateLocation,
            String summaryLine) {
        static PendingLikerDto from(MatchingService.PendingLiker pendingLiker, ZoneId userTimeZone) {
            return from(pendingLiker, userTimeZone, null);
        }

        static PendingLikerDto from(
                MatchingService.PendingLiker pendingLiker, ZoneId userTimeZone, String approximateLocation) {
            User user = pendingLiker.user();
            return new PendingLikerDto(
                    user.getId(),
                    user.getName(),
                    UserDtoMapper.age(user, userTimeZone),
                    pendingLiker.likedAt(),
                    personPrimaryPhotoUrl(user),
                    personPhotoUrls(user),
                    approximateLocation,
                    personSummaryLine(user));
        }
    }

    static record StandoutDto(
            UUID id,
            UUID standoutUserId,
            String standoutUserName,
            int standoutUserAge,
            int rank,
            int score,
            String reason,
            Instant createdAt,
            Instant interactedAt,
            String primaryPhotoUrl,
            List<String> photoUrls,
            String approximateLocation,
            String summaryLine) {
        static StandoutDto from(Standout standout, Map<UUID, User> usersById, ZoneId userTimeZone) {
            return from(standout, usersById, userTimeZone, null);
        }

        static StandoutDto from(
                Standout standout, Map<UUID, User> usersById, ZoneId userTimeZone, String approximateLocation) {
            User user = usersById.get(standout.standoutUserId());
            return new StandoutDto(
                    standout.id(),
                    standout.standoutUserId(),
                    user != null ? user.getName() : UNKNOWN_USER,
                    UserDtoMapper.age(user, userTimeZone),
                    standout.rank(),
                    standout.score(),
                    standout.reason(),
                    standout.createdAt(),
                    standout.interactedAt(),
                    personPrimaryPhotoUrl(user),
                    personPhotoUrls(user),
                    approximateLocation,
                    personSummaryLine(user));
        }
    }

    static record ProfileUpdateResponse(
            UUID id,
            String name,
            int age,
            String bio,
            String gender,
            List<String> interestedIn,
            String approximateLocation,
            int maxDistanceKm,
            String state,
            boolean activated) {
        static ProfileUpdateResponse from(
                User user, boolean activated, ZoneId userTimeZone, String approximateLocation) {
            UserDtoMapper.UserFields fields = UserDtoMapper.map(user, userTimeZone, approximateLocation);
            return new ProfileUpdateResponse(
                    user.getId(),
                    user.getName(),
                    fields.age(),
                    user.getBio(),
                    fields.gender(),
                    fields.interestedIn(),
                    fields.approximateLocation(),
                    fields.maxDistanceKm(),
                    fields.state(),
                    activated);
        }
    }

    static String personPrimaryPhotoUrl(User user) {
        if (user == null) {
            return null;
        }
        return user.getPhotoUrls().stream()
                .filter(User::isRealPhotoUrl)
                .findFirst()
                .orElse(null);
    }

    static List<String> personPhotoUrls(User user) {
        return user == null ? List.of() : List.copyOf(user.getPhotoUrls());
    }

    static String personSummaryLine(User user) {
        if (user == null) {
            return null;
        }
        if (user.getBio() != null && !user.getBio().isBlank()) {
            return user.getBio();
        }
        List<String> interests = user.getInterests().stream()
                .sorted(Comparator.comparing(Enum::name))
                .limit(3)
                .map(Interest::getDisplayName)
                .toList();
        return interests.isEmpty() ? null : String.join(", ", interests);
    }
}
