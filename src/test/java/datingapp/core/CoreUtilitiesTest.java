package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.*;
import datingapp.core.model.MatchPreferences.Interest;
import datingapp.core.service.*;
import datingapp.core.service.CandidateFinder.GeoUtils;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Consolidated unit tests for core utility classes and value objects.
 *
 * <p>Includes tests for:
 * <ul>
 *   <li>{@link GeoUtils} - geographic distance calculations</li>
 *   <li>{@link Interest} - interest enum and category lookups</li>
 *   <li>{@link Match} - match domain model</li>
 * </ul>
 */
@SuppressWarnings("unused") // Test class with @Nested
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class CoreUtilitiesTest {

    // ==================== GeoUtils TESTS ====================

    @Nested
    @DisplayName("GeoUtils - Geographic Distance Calculations")
    class GeoUtilsTests {

        @Test
        @DisplayName("Distance between same point is zero")
        void samePointDistanceIsZero() {
            double distance = GeoUtils.distanceKm(32.0853, 34.7818, 32.0853, 34.7818);
            assertEquals(0.0, distance, 0.001);
        }

        @Test
        @DisplayName("Distance Tel Aviv to Jerusalem is approximately 54km")
        void telAvivToJerusalemDistance() {
            // Tel Aviv: 32.0853, 34.7818
            // Jerusalem: 31.7683, 35.2137
            double distance = GeoUtils.distanceKm(32.0853, 34.7818, 31.7683, 35.2137);

            // Should be approximately 54km
            assertTrue(distance > 50 && distance < 60, "Distance should be between 50-60km, was: " + distance);
        }

        @Test
        @DisplayName("Distance New York to London is approximately 5570km")
        void newYorkToLondonDistance() {
            // New York: 40.7128, -74.0060
            // London: 51.5074, -0.1278
            double distance = GeoUtils.distanceKm(40.7128, -74.0060, 51.5074, -0.1278);

            // Should be approximately 5570km
            assertTrue(distance > 5500 && distance < 5600, "Distance should be between 5500-5600km, was: " + distance);
        }
    }

    // ==================== INTEREST TESTS ====================

    @Nested
    @DisplayName("Interest - Enum and Category Lookups")
    class InterestTests {

        @Test
        @DisplayName("byCategory returns correct interests for OUTDOORS")
        void byCategory_outdoors_returnsCorrectInterests() {
            List<Interest> outdoors = Interest.byCategory(Interest.Category.OUTDOORS);
            assertTrue(outdoors.contains(Interest.HIKING));
            assertTrue(outdoors.contains(Interest.CAMPING));
            assertEquals(6, outdoors.size());
        }

        @Test
        @DisplayName("byCategory returns empty list for null")
        void byCategory_null_returnsEmptyList() {
            List<Interest> result = Interest.byCategory(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("All interests have display names")
        void allInterests_haveDisplayNames() {
            for (Interest interest : Interest.values()) {
                assertNotNull(interest.getDisplayName());
                assertFalse(interest.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("All interests have categories")
        void allInterests_haveCategories() {
            for (Interest interest : Interest.values()) {
                assertNotNull(interest.getCategory());
            }
        }

        @Test
        @DisplayName("count returns correct total")
        void count_returnsCorrectTotal() {
            assertEquals(Interest.values().length, Interest.count());
        }

        @Test
        @DisplayName("Constants are correctly defined")
        void constants_areDefined() {
            assertEquals(10, Interest.MAX_PER_USER);
            assertEquals(3, Interest.MIN_FOR_COMPLETE);
        }
    }

    // ==================== MATCH TESTS ====================

    @Nested
    @DisplayName("Match - Domain Model")
    class MatchTests {

        @Test
        @DisplayName("Match ID is deterministic regardless of UUID order")
        void matchIdIsDeterministic() {
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

            Match match1 = Match.create(a, b);
            Match match2 = Match.create(b, a);

            assertEquals(match1.getId(), match2.getId());
        }

        @Test
        @DisplayName("userA is always the lexicographically smaller UUID")
        void userAIsAlwaysSmaller() {
            UUID a = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

            Match match = Match.create(a, b);

            assertTrue(match.getUserA().toString().compareTo(match.getUserB().toString()) < 0);
        }

        @Test
        @DisplayName("Cannot create match with same user")
        void cannotMatchWithSelf() {
            UUID a = UUID.randomUUID();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Match.create(a, a));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("getOtherUser returns correct user")
        void getOtherUserReturnsCorrectly() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            Match match = Match.create(a, b);

            assertEquals(b, match.getOtherUser(a));
            assertEquals(a, match.getOtherUser(b));
        }
    }
}
