package datingapp.core;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class EnumSetUtil {

    private EnumSetUtil() {
        // Utility class
    }

    /**
     * Creates a defensive copy of the given collection as an {@link EnumSet}.
     * Returns an empty EnumSet if the input is null or empty.
     *
     * @param <E>       The enum type
     * @param source    The source collection (may be null or empty)
     * @param enumClass The enum class (used for empty set creation)
     * @return A new EnumSet containing the source elements, or an empty EnumSet
     */
    public static <E extends Enum<E>> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass cannot be null");
        if (source == null || source.isEmpty()) {
            return EnumSet.noneOf(enumClass);
        }
        return EnumSet.copyOf(source);
    }

    /**
     * Creates a defensive copy of the given set as an {@link EnumSet}.
     * Returns an empty EnumSet if the input is null or empty.
     *
     * @param <E>       The enum type
     * @param source    The source set (may be null or empty)
     * @param enumClass The enum class (used for empty set creation)
     * @return A new EnumSet containing the source elements, or an empty EnumSet
     */
    public static <E extends Enum<E>> EnumSet<E> safeCopy(Set<E> source, Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass cannot be null");
        if (source == null || source.isEmpty()) {
            return EnumSet.noneOf(enumClass);
        }
        return EnumSet.copyOf(source);
    }

    /**
     * Returns a defensive copy of an EnumSet for safe return from getters.
     * Returns an empty EnumSet if the input is null or empty.
     *
     * @param <E>       The enum type
     * @param source    The source EnumSet (may be null or empty)
     * @param enumClass The enum class (used for empty set creation)
     * @return A new EnumSet copy, or an empty EnumSet
     */
    public static <E extends Enum<E>> EnumSet<E> defensiveCopy(EnumSet<E> source, Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass cannot be null");
        if (source == null || source.isEmpty()) {
            return EnumSet.noneOf(enumClass);
        }
        return EnumSet.copyOf(source);
    }
}
