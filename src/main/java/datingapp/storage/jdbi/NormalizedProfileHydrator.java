package datingapp.storage.jdbi;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Interest;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NormalizedProfileHydrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NormalizedProfileHydrator.class);

    private final DealbreakerAssembler dealbreakerAssembler;

    NormalizedProfileHydrator(DealbreakerAssembler dealbreakerAssembler) {
        this.dealbreakerAssembler = dealbreakerAssembler;
    }

    List<User> hydrate(List<User> users, NormalizedProfileRepository.NormalizedProfileData normalizedProfileData) {
        if (users == null || users.isEmpty()) {
            return users;
        }

        for (User user : users) {
            var userId = user.getId();
            user.setPhotoUrls(normalizedProfileData.photoUrlsByUserId().getOrDefault(userId, List.of()));
            user.setInterests(parseEnumNames(
                    normalizedProfileData.interestsByUserId().getOrDefault(userId, Set.of()), Interest.class));
            user.setInterestedIn(parseEnumNames(
                    normalizedProfileData.interestedInByUserId().getOrDefault(userId, Set.of()), Gender.class));
            user.setDealbreakers(
                    dealbreakerAssembler.assemble(user, userId, normalizedProfileData.dealbreakerValuesByUserId()));
        }

        return users;
    }

    private static <E extends Enum<E>> Set<E> parseEnumNames(Collection<String> values, Class<E> enumType) {
        if (values == null || values.isEmpty()) {
            return EnumSet.noneOf(enumType);
        }
        EnumSet<E> parsed = EnumSet.noneOf(enumType);
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                parsed.add(Enum.valueOf(enumType, value.trim()));
            } catch (IllegalArgumentException _) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Ignoring invalid {} value '{}' during compatibility read",
                            enumType.getSimpleName(),
                            value);
                }
            }
        }
        return parsed;
    }
}
