package datingapp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Null-preserving immutable snapshots for record components and other value carriers.
 *
 * <p>Unlike {@code List.copyOf} and friends, these helpers tolerate {@code null} containers and
 * {@code null} elements so snapshotting never changes a carrier's null contract — it only severs
 * aliasing with the caller's mutable collection.
 */
public final class Snapshots {

    private Snapshots() {}

    /** Returns an unmodifiable snapshot of {@code values}, or {@code null} when {@code values} is null. */
    public static <T> List<T> listOf(List<T> values) {
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }

    /** Returns an unmodifiable snapshot of {@code values}, or {@code null} when {@code values} is null. */
    public static <T> Set<T> setOf(Set<T> values) {
        return values == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    /** Returns an unmodifiable snapshot of {@code values}, or {@code null} when {@code values} is null. */
    public static <K, V> Map<K, V> mapOf(Map<K, V> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
