package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.*;
import datingapp.core.model.MatchPreferences.Dealbreakers;
import datingapp.core.model.MatchPreferences.Lifestyle;
import datingapp.core.service.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for Dealbreakers validation and builder. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DealbreakersTest {

    @Nested
    @DisplayName("Validation tests")
    class ValidationTests {

        @Test
        @DisplayName("minHeightCm cannot be below config minimum (50)")
        void minHeightTooLow() {
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Dealbreakers(null, null, null, null, null, 49, null, null));
            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("maxHeightCm cannot be above config maximum (300)")
        void maxHeightTooHigh() {
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Dealbreakers(null, null, null, null, null, null, 301, null));
            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("minHeightCm cannot be greater than maxHeightCm")
        void minGreaterThanMax() {
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Dealbreakers(null, null, null, null, null, 180, 170, null));
            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("maxAgeDifference cannot be negative")
        void negativeAgeDifference() {
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Dealbreakers(null, null, null, null, null, null, null, -1));
            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("Valid height range is accepted")
        void validHeightRange() {
            Dealbreakers db = new Dealbreakers(null, null, null, null, null, 160, 190, null);
            assertEquals(160, db.minHeightCm());
            assertEquals(190, db.maxHeightCm());
        }
    }

    @Nested
    @DisplayName("Factory and builder tests")
    class FactoryTests {

        @Test
        @DisplayName("none() factory creates dealbreakers that accept all")
        void noneAcceptsAll() {
            Dealbreakers db = Dealbreakers.none();

            assertFalse(db.hasSmokingDealbreaker());
            assertFalse(db.hasDrinkingDealbreaker());
            assertFalse(db.hasKidsDealbreaker());
            assertFalse(db.hasLookingForDealbreaker());
            assertFalse(db.hasEducationDealbreaker());
            assertFalse(db.hasHeightDealbreaker());
            assertFalse(db.hasAgeDealbreaker());
            assertFalse(db.hasAnyDealbreaker());
        }

        @Test
        @DisplayName("Builder creates valid dealbreakers")
        void builderCreatesValid() {
            Dealbreakers db = Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .acceptDrinking(Lifestyle.Drinking.NEVER, Lifestyle.Drinking.SOCIALLY)
                    .heightRange(160, 190)
                    .maxAgeDifference(5)
                    .build();

            assertTrue(db.hasSmokingDealbreaker());
            assertTrue(db.hasDrinkingDealbreaker());
            assertFalse(db.hasKidsDealbreaker());
            assertTrue(db.hasHeightDealbreaker());
            assertTrue(db.hasAgeDealbreaker());
            assertTrue(db.hasAnyDealbreaker());

            assertEquals(Set.of(Lifestyle.Smoking.NEVER), db.acceptableSmoking());
            assertEquals(Set.of(Lifestyle.Drinking.NEVER, Lifestyle.Drinking.SOCIALLY), db.acceptableDrinking());
            assertEquals(160, db.minHeightCm());
            assertEquals(190, db.maxHeightCm());
            assertEquals(5, db.maxAgeDifference());
        }

        @Test
        @DisplayName("Builder accumulates multiple calls")
        void builderAccumulates() {
            Dealbreakers db = Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .acceptSmoking(Lifestyle.Smoking.SOMETIMES)
                    .build();

            assertEquals(Set.of(Lifestyle.Smoking.NEVER, Lifestyle.Smoking.SOMETIMES), db.acceptableSmoking());
        }
    }

    @Nested
    @DisplayName("Defensive copy tests")
    class DefensiveCopyTests {

        @Test
        @DisplayName("Null sets are converted to empty sets")
        void nullSetsBecome() {
            Dealbreakers db = new Dealbreakers(null, null, null, null, null, null, null, null);

            assertNotNull(db.acceptableSmoking());
            assertTrue(db.acceptableSmoking().isEmpty());
        }

        @Test
        @DisplayName("Sets are immutable")
        void setsAreImmutable() {
            Dealbreakers db = Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build();
            var set = db.acceptableSmoking();

            var exception =
                    assertThrows(UnsupportedOperationException.class, () -> set.add(Lifestyle.Smoking.REGULARLY));
            assertNotNull(exception);
        }
    }
}
