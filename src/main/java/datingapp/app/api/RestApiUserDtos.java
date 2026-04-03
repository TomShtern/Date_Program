package datingapp.app.api;

import datingapp.core.matching.DailyPickService.DailyPick;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.Standout;
import datingapp.core.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RestApiUserDtos {
    private static final String UNKNOWN_USER = "Unknown";

    private RestApiUserDtos() {}

    static record UserSummary(UUID id, String name, int age, String state) {
        static UserSummary from(User user, ZoneId userTimeZone) {
            UserDtoMapper.UserFields fields = UserDtoMapper.map(user, userTimeZone, null);
            return new UserSummary(user.getId(), user.getName(), fields.age(), fields.state());
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
                    user.getPhotoUrls(),
                    fields.state());
        }
    }

    static record DailyPickDto(
            UUID userId, String userName, int userAge, LocalDate date, String reason, boolean alreadySeen) {
        static DailyPickDto from(DailyPick dailyPick, ZoneId userTimeZone) {
            return new DailyPickDto(
                    dailyPick.user().getId(),
                    dailyPick.user().getName(),
                    UserDtoMapper.age(dailyPick.user(), userTimeZone),
                    dailyPick.date(),
                    dailyPick.reason(),
                    dailyPick.alreadySeen());
        }
    }

    static record PendingLikerDto(UUID userId, String name, int age, Instant likedAt) {
        static PendingLikerDto from(MatchingService.PendingLiker pendingLiker, ZoneId userTimeZone) {
            return new PendingLikerDto(
                    pendingLiker.user().getId(),
                    pendingLiker.user().getName(),
                    UserDtoMapper.age(pendingLiker.user(), userTimeZone),
                    pendingLiker.likedAt());
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
            Instant interactedAt) {
        static StandoutDto from(Standout standout, Map<UUID, User> usersById, ZoneId userTimeZone) {
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
                    standout.interactedAt());
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
}
