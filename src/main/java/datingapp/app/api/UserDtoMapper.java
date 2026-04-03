package datingapp.app.api;

import datingapp.app.support.UserPresentationSupport;
import datingapp.core.model.User;
import java.time.ZoneId;
import java.util.List;

final class UserDtoMapper {
    private UserDtoMapper() {}

    static UserFields map(User user, ZoneId userTimeZone, String approximateLocation) {
        return new UserFields(
                UserPresentationSupport.safeAge(user, userTimeZone),
                user != null && user.getGender() != null ? user.getGender().name() : null,
                user != null && user.getInterestedIn() != null
                        ? user.getInterestedIn().stream().map(Enum::name).toList()
                        : List.of(),
                approximateLocation,
                user != null ? user.getMaxDistanceKm() : 0,
                user != null && user.getState() != null ? user.getState().name() : null);
    }

    static int age(User user, ZoneId userTimeZone) {
        return UserPresentationSupport.safeAge(user, userTimeZone);
    }

    static record UserFields(
            int age,
            String gender,
            List<String> interestedIn,
            String approximateLocation,
            int maxDistanceKm,
            String state) {}
}
