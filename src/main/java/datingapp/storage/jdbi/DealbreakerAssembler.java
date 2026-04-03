package datingapp.storage.jdbi;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DealbreakerAssembler {

    private static final Logger LOGGER = Logger.getLogger(DealbreakerAssembler.class.getName());

    Dealbreakers assemble(
            User user,
            UUID userId,
            Map<UUID, Map<NormalizedProfileRepository.DealbreakerTable, Set<String>>> dealbreakerValuesByUserId) {
        Dealbreakers existing = user != null ? user.getDealbreakers() : null;
        Dealbreakers.Builder builder = (existing != null ? existing : Dealbreakers.none()).toBuilder();
        Map<NormalizedProfileRepository.DealbreakerTable, Set<String>> valuesByTable =
                dealbreakerValuesByUserId.getOrDefault(userId, Map.of());

        Set<Lifestyle.Smoking> smoking = parseEnumNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.SMOKING, Set.of()),
                Lifestyle.Smoking.class);
        builder.clearSmoking();
        builder.acceptSmoking(smoking.toArray(Lifestyle.Smoking[]::new));

        Set<Lifestyle.Drinking> drinking = parseEnumNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.DRINKING, Set.of()),
                Lifestyle.Drinking.class);
        builder.clearDrinking();
        builder.acceptDrinking(drinking.toArray(Lifestyle.Drinking[]::new));

        Set<Lifestyle.WantsKids> kids = parseEnumNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.WANTS_KIDS, Set.of()),
                Lifestyle.WantsKids.class);
        builder.clearKids();
        builder.acceptKidsStance(kids.toArray(Lifestyle.WantsKids[]::new));

        Set<Lifestyle.LookingFor> lookingFor = parseEnumNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.LOOKING_FOR, Set.of()),
                Lifestyle.LookingFor.class);
        builder.clearLookingFor();
        builder.acceptLookingFor(lookingFor.toArray(Lifestyle.LookingFor[]::new));

        Set<Lifestyle.Education> education = parseEnumNames(
                valuesByTable.getOrDefault(NormalizedProfileRepository.DealbreakerTable.EDUCATION, Set.of()),
                Lifestyle.Education.class);
        builder.clearEducation();
        builder.requireEducation(education.toArray(Lifestyle.Education[]::new));

        return builder.build();
    }

    private static <E extends Enum<E>> Set<E> parseEnumNames(Collection<String> values, Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType cannot be null");
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
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Ignoring invalid " + enumType.getSimpleName() + " value '" + value
                            + "' during compatibility read");
                }
            }
        }
        return parsed;
    }
}
