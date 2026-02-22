package datingapp.core.storage;

import java.util.List;
import java.util.Objects;

/**
 * Immutable pagination envelope that wraps a single page of query results.
 *
 * <p>
 * This type is intentionally dependency-free (no framework, no DB imports) so
 * it can
 * live in {@code core/storage/} without violating the domain-purity rule. Any
 * layer —
 * storage, service, API, or UI — may use it.
 *
 * <p>
 * Usage example:
 *
 * <pre>{@code
 * PageData<Match> page = storage.getPageOfMatchesFor(userId, offset, limit);
 * if (page.hasMore()) {
 *     int nextOffset = page.offset() + page.items().size();
 *     PageData<Match> nextPage = storage.getPageOfMatchesFor(userId, nextOffset, limit);
 * }
 * }</pre>
 *
 * @param <T>        the element type contained in the page
 * @param items      the elements on this page (never null, may be empty)
 * @param totalCount the total number of items across all pages
 * @param offset     zero-based index of the first item in this page
 * @param limit      the maximum number of items requested per page (must be
 *                   &gt; 0)
 */
public record PageData<T>(List<T> items, int totalCount, int offset, int limit) {

    /*
     * Compact constructor — validates all invariants.
     *
     * <ul>
     * <li>{@code items} must not be null.
     * <li>{@code totalCount} must be &ge; 0.
     * <li>{@code offset} must be &ge; 0.
     * <li>{@code limit} must be &gt; 0.
     * </ul>
     */
    public PageData {
        Objects.requireNonNull(items, "items cannot be null");
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must be >= 0, got: " + totalCount);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, got: " + limit);
        }
        // Defensive copy — callers cannot mutate the list after construction.
        items = List.copyOf(items);
    }

    /**
     * Returns {@code true} if there are more items beyond this page.
     *
     * <p>
     * Derived from: {@code offset + items.size() < totalCount}.
     */
    public boolean hasMore() {
        return offset + items.size() < totalCount;
    }

    /**
     * Returns the zero-based page number derived from offset and limit.
     *
     * <p>
     * For example, {@code offset=20, limit=10} → page number {@code 2}.
     */
    public int pageNumber() {
        return offset / limit;
    }

    /**
     * Returns {@code true} if this page contains no items.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Factory: creates an empty first page with the given limit and total count.
     *
     * <p>
     * Useful for returning a typed empty result without constructing a mutable
     * list.
     */
    public static <T> PageData<T> empty(int limit, int totalCount) {
        return new PageData<>(List.of(), totalCount, 0, limit);
    }

    /**
     * Factory: creates an empty first page with zero total count (nothing in
     * storage).
     */
    public static <T> PageData<T> emptyFirstPage(int limit) {
        return empty(limit, 0);
    }
}
