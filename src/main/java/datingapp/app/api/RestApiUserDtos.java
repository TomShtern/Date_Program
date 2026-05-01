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
import java.util.function.UnaryOperator;

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
            return from(user, userTimeZone, null, UnaryOperator.identity());
        }

        static UserSummary from(User user, ZoneId userTimeZone, String approximateLocation) {
            return from(user, userTimeZone, approximateLocation, UnaryOperator.identity());
        }

        static UserSummary from(
                User user, ZoneId userTimeZone, String approximateLocation, UnaryOperator<String> photoUrlResolver) {
            UserDtoMapper.UserFields fields = UserDtoMapper.map(user, userTimeZone, null);
            return new UserSummary(
                    user.getId(),
                    user.getName(),
                    fields.age(),
                    fields.state(),
                    personPrimaryPhotoUrl(user, photoUrlResolver),
                    personPhotoUrls(user, photoUrlResolver),
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
            String primaryPhotoUrl,
            String state) {
        static UserDetail from(User user, ZoneId userTimeZone, String approximateLocation) {
            return from(user, userTimeZone, approximateLocation, UnaryOperator.identity());
        }

        static UserDetail from(
                User user, ZoneId userTimeZone, String approximateLocation, UnaryOperator<String> photoUrlResolver) {
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
                    personPhotoUrls(user, photoUrlResolver),
                    personPrimaryPhotoUrl(user, photoUrlResolver),
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
            return from(dailyPick, userTimeZone, null, UnaryOperator.identity());
        }

        static DailyPickDto from(DailyPick dailyPick, ZoneId userTimeZone, String approximateLocation) {
            return from(dailyPick, userTimeZone, approximateLocation, UnaryOperator.identity());
        }

        static DailyPickDto from(
                DailyPick dailyPick,
                ZoneId userTimeZone,
                String approximateLocation,
                UnaryOperator<String> photoUrlResolver) {
            User user = dailyPick.user();
            return new DailyPickDto(
                    user.getId(),
                    user.getName(),
                    UserDtoMapper.age(user, userTimeZone),
                    dailyPick.date(),
                    dailyPick.reason(),
                    dailyPick.alreadySeen(),
                    personPrimaryPhotoUrl(user, photoUrlResolver),
                    personPhotoUrls(user, photoUrlResolver),
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
            return from(pendingLiker, userTimeZone, null, UnaryOperator.identity());
        }

        static PendingLikerDto from(
                MatchingService.PendingLiker pendingLiker, ZoneId userTimeZone, String approximateLocation) {
            return from(pendingLiker, userTimeZone, approximateLocation, UnaryOperator.identity());
        }

        static PendingLikerDto from(
                MatchingService.PendingLiker pendingLiker,
                ZoneId userTimeZone,
                String approximateLocation,
                UnaryOperator<String> photoUrlResolver) {
            User user = pendingLiker.user();
            return new PendingLikerDto(
                    user.getId(),
                    user.getName(),
                    UserDtoMapper.age(user, userTimeZone),
                    pendingLiker.likedAt(),
                    personPrimaryPhotoUrl(user, photoUrlResolver),
                    personPhotoUrls(user, photoUrlResolver),
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
            return from(standout, usersById, userTimeZone, null, UnaryOperator.identity());
        }

        static StandoutDto from(
                Standout standout, Map<UUID, User> usersById, ZoneId userTimeZone, String approximateLocation) {
            return from(standout, usersById, userTimeZone, approximateLocation, UnaryOperator.identity());
        }

        static StandoutDto from(
                Standout standout,
                Map<UUID, User> usersById,
                ZoneId userTimeZone,
                String approximateLocation,
                UnaryOperator<String> photoUrlResolver) {
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
                    personPrimaryPhotoUrl(user, photoUrlResolver),
                    personPhotoUrls(user, photoUrlResolver),
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
        return personPrimaryPhotoUrl(user, UnaryOperator.identity());
    }

    static String personPrimaryPhotoUrl(User user, UnaryOperator<String> photoUrlResolver) {
        if (user == null) {
            return null;
        }
        return user.getPhotoUrls().stream()
                .filter(User::isRealPhotoUrl)
                .findFirst()
                .map(photoUrlResolver)
                .orElse(null);
    }

    static List<String> personPhotoUrls(User user, UnaryOperator<String> photoUrlResolver) {
        return user == null
                ? List.of()
                : user.getPhotoUrls().stream().map(photoUrlResolver).toList();
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
