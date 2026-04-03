package datingapp.storage.jdbi;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NormalizedEnumParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(NormalizedEnumParser.class);

    private NormalizedEnumParser() {}

    static <E extends Enum<E>> Set<E> parseNames(Collection<String> values, Class<E> enumType) {
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
