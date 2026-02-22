package datingapp.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for the {@link PageData} pagination wrapper record.
 *
 * <p>
 * Verifies invariant enforcement, factory methods, and the {@code hasMore()},
 * {@code pageNumber()}, and {@code isEmpty()} helpers.
 */
@SuppressWarnings("unused") // @Nested test structure
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PageDataTest {

    // ══════════════════════════ Construction ══════════════════════════

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("Valid page construction succeeds")
        void validPageSucceeds() {
            PageData<String> page = new PageData<>(List.of("a", "b", "c"), 10, 0, 5);

            assertEquals(List.of("a", "b", "c"), page.items());
            assertEquals(10, page.totalCount());
            assertEquals(0, page.offset());
            assertEquals(5, page.limit());
        }

        @Test
        @DisplayName("Null items throws NullPointerException")
        void nullItemsThrows() {
            assertThrows(NullPointerException.class, () -> new PageData<>(null, 10, 0, 5));
        }

        @Test
        @DisplayName("Negative totalCount throws IllegalArgumentException")
        void negativeTotalCountThrows() {
            assertThrows(IllegalArgumentException.class, () -> new PageData<>(List.of(), -1, 0, 5));
        }

        @Test
        @DisplayName("Negative offset throws IllegalArgumentException")
        void negativeOffsetThrows() {
            assertThrows(IllegalArgumentException.class, () -> new PageData<>(List.of(), 10, -1, 5));
        }

        @Test
        @DisplayName("Zero limit throws IllegalArgumentException")
        void zeroLimitThrows() {
            assertThrows(IllegalArgumentException.class, () -> new PageData<>(List.of(), 10, 0, 0));
        }

        @Test
        @DisplayName("Negative limit throws IllegalArgumentException")
        void negativeLimitThrows() {
            assertThrows(IllegalArgumentException.class, () -> new PageData<>(List.of(), 10, 0, -1));
        }

        @Test
        @DisplayName("Items list is defensively copied — mutation of source does not affect PageData")
        void itemsAreDefensivelyCopied() {
            java.util.List<String> mutable = new java.util.ArrayList<>(List.of("x", "y"));
            PageData<String> page = new PageData<>(mutable, 5, 0, 10);

            mutable.add("z"); // mutate source after construction

            assertEquals(2, page.items().size(), "PageData should not reflect mutation of source list");
        }

        @Test
        @DisplayName("Zero totalCount with empty items is valid (empty result set)")
        void zeroCounts() {
            PageData<String> page = new PageData<>(List.of(), 0, 0, 10);
            assertTrue(page.isEmpty());
            assertFalse(page.hasMore());
            assertEquals(0, page.pageNumber());
        }
    }

    // ══════════════════════════ hasMore() ══════════════════════════

    @Nested
    @DisplayName("hasMore()")
    class HasMoreTests {

        @Test
        @DisplayName("hasMore() is true when offset + items.size() < totalCount")
        void hasMoreTrueWhenMoreItemsExist() { // 3 items loaded, starting at 0, total is 10 → hasMore = true
            PageData<String> page = new PageData<>(List.of("a", "b", "c"), 10, 0, 5);
            assertTrue(page.hasMore());
        }

        @Test
        @DisplayName("hasMore() is false when offset + items.size() == totalCount")
        void hasMoreFalseAtEnd() {
            // 5 items loaded, starting at 5, total is 10 → exactly last page
            PageData<String> page = new PageData<>(List.of("f", "g", "h", "i", "j"), 10, 5, 5);
            assertFalse(page.hasMore());
        }

        @Test
        @DisplayName("hasMore() is false on empty page with no total")
        void hasMoreFalseOnEmptyPage() {
            PageData<String> page = PageData.emptyFirstPage(10);
            assertFalse(page.hasMore());
        }

        @Test
        @DisplayName("hasMore() is false when last page has fewer items than limit")
        void hasMoreFalseOnPartialLastPage() {
            // offset 10, 3 items, total 13 → last page, no more
            PageData<String> page = new PageData<>(List.of("k", "l", "m"), 13, 10, 5);
            assertFalse(page.hasMore());
        }
    }

    // ══════════════════════════ pageNumber() ══════════════════════════

    @Nested
    @DisplayName("pageNumber()")
    class PageNumberTests {

        @Test
        @DisplayName("First page is page 0")
        void firstPageIsZero() {
            PageData<String> page = new PageData<>(List.of("a"), 5, 0, 5);
            assertEquals(0, page.pageNumber());
        }

        @Test
        @DisplayName("Second page: offset=5, limit=5 → pageNumber=1")
        void secondPage() {
            PageData<String> page = new PageData<>(List.of("f"), 10, 5, 5);
            assertEquals(1, page.pageNumber());
        }

        @Test
        @DisplayName("pageNumber with limit=20, offset=40 → page 2")
        void thirdPageWith20PerPage() {
            PageData<String> page = new PageData<>(List.of("x"), 100, 40, 20);
            assertEquals(2, page.pageNumber());
        }
    }

    // ══════════════════════════ Factory Methods ══════════════════════════

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("empty(limit, totalCount) creates correct page with hasMore=true when total > 0")
        void emptyFactory() {
            // empty(10, 42) represents: 0 items loaded, total=42 → more to load
            PageData<Integer> page = PageData.empty(10, 42);
            assertTrue(page.isEmpty());
            assertEquals(42, page.totalCount());
            assertEquals(0, page.offset());
            assertEquals(10, page.limit());
            // hasMore = 0 + 0 < 42 → true (there are items but none loaded yet)
            assertTrue(page.hasMore(), "empty page with non-zero total should indicate more to load");
        }

        @Test
        @DisplayName("emptyFirstPage(limit) creates zeroed page")
        void emptyFirstPageFactory() {
            PageData<Integer> page = PageData.emptyFirstPage(5);
            assertTrue(page.isEmpty());
            assertEquals(0, page.totalCount());
            assertFalse(page.hasMore());
            assertEquals(0, page.pageNumber());
        }
    }
}
