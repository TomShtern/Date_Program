package datingapp.storage.jdbi;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DealbreakerAssembler {

    Dealbreakers assemble(
            User user,
            UUID userId,
            Map<UUID, Map<NormalizedProfileRepository.DealbreakerTable, Set<String>>> dealbreakerValuesByUserId) {
        Map<UUID, Map<NormalizedProfileRepository.DealbreakerTable, Set<String>>> safeDealbreakerValuesByUserId =
                dealbreakerValuesByUserId == null ? Map.of() : dealbreakerValuesByUserId;
        Dealbreakers existing = user != null ? user.getDealbreakers() : null;
        Dealbreakers.Builder builder = (existing != null ? existing : Dealbreakers.none()).toBuilder();
        Map<NormalizedProfileRepository.DealbreakerTable, Set<String>> valuesByTable =
                safeDealbreakerValuesByUserId.getOrDefault(userId, Map.of());

        Set<Lifestyle.Smoking> smoking = NormalizedEnumParser.parseNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.SMOKING, Set.of()),
                Lifestyle.Smoking.class);
        builder.clearSmoking();
        builder.acceptSmoking(smoking.toArray(Lifestyle.Smoking[]::new));

        Set<Lifestyle.Drinking> drinking = NormalizedEnumParser.parseNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.DRINKING, Set.of()),
                Lifestyle.Drinking.class);
        builder.clearDrinking();
        builder.acceptDrinking(drinking.toArray(Lifestyle.Drinking[]::new));

        Set<Lifestyle.WantsKids> kids = NormalizedEnumParser.parseNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.WANTS_KIDS, Set.of()),
                Lifestyle.WantsKids.class);
        builder.clearKids();
        builder.acceptKidsStance(kids.toArray(Lifestyle.WantsKids[]::new));

        Set<Lifestyle.LookingFor> lookingFor = NormalizedEnumParser.parseNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.LOOKING_FOR, Set.of()),
                Lifestyle.LookingFor.class);
        builder.clearLookingFor();
        builder.acceptLookingFor(lookingFor.toArray(Lifestyle.LookingFor[]::new));

        Set<Lifestyle.Education> education = NormalizedEnumParser.parseNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.EDUCATION, Set.of()),
                Lifestyle.Education.class);
        builder.clearEducation();
        builder.requireEducation(education.toArray(Lifestyle.Education[]::new));

        return builder.build();
    }
}
