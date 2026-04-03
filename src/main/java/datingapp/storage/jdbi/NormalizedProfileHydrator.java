package datingapp.storage.jdbi;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Interest;
import java.util.List;
import java.util.Set;

final class NormalizedProfileHydrator {

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
            user.setInterests(NormalizedEnumParser.parseNames(
                    normalizedProfileData.interestsByUserId().getOrDefault(userId, Set.of()), Interest.class));
            user.setInterestedIn(NormalizedEnumParser.parseNames(
                    normalizedProfileData.interestedInByUserId().getOrDefault(userId, Set.of()), Gender.class));
            user.setDealbreakers(
                    dealbreakerAssembler.assemble(user, userId, normalizedProfileData.dealbreakerValuesByUserId()));
        }

        return users;
    }
}
