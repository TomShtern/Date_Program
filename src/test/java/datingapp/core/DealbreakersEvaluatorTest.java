package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for Dealbreakers.Evaluator. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DealbreakersEvaluatorTest {

    private User seeker;
    private User candidate;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);

        // Create seeker (30 years old)
        seeker = new User(UUID.randomUUID(), "Seeker");
        seeker.setBirthDate(AppClock.today().minusYears(30));
        seeker.setGender(User.Gender.FEMALE);
        seeker.setInterestedIn(EnumSet.of(User.Gender.MALE));

        // Create candidate (28 years old)
        candidate = new User(UUID.randomUUID(), "Candidate");
        candidate.setBirthDate(AppClock.today().minusYears(28));
        candidate.setGender(User.Gender.MALE);
        candidate.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("No dealbreakers")
    class NoDealbreakers {

        @Test
        @DisplayName("No dealbreakers means everyone passes")
        void noDealbreakersPassesAll() {
            // seeker has no dealbreakers set
            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Dealbreakers.none() accepts all")
        void explicitNonePasses() {
            seeker.setDealbreakers(Dealbreakers.none());
            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Smoking dealbreaker")
    class SmokingDealbreaker {

        @Test
        @DisplayName("Passes when candidate matches accepted smoking status")
        void passesMatchingSmoking() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.NEVER);

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate doesn't match accepted smoking status")
        void failsNonMatchingSmoking() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.REGULARLY);

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate has null smoking status")
        void failsNullSmoking() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build());

            // candidate.setSmoking is not called - remains null

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Passes when accepting multiple smoking values")
        void passesMultipleAccepted() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER, Lifestyle.Smoking.SOMETIMES)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.SOMETIMES);

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Drinking dealbreaker")
    class DrinkingDealbreaker {

        @Test
        @DisplayName("Passes when candidate matches accepted drinking status")
        void passesMatchingDrinking() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptDrinking(Lifestyle.Drinking.SOCIALLY)
                    .build());

            candidate.setDrinking(Lifestyle.Drinking.SOCIALLY);

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate doesn't match drinking status")
        void failsNonMatchingDrinking() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptDrinking(Lifestyle.Drinking.NEVER)
                    .build());

            candidate.setDrinking(Lifestyle.Drinking.REGULARLY);

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Kids stance dealbreaker")
    class KidsDealbreaker {

        @Test
        @DisplayName("Passes matching kids stance")
        void passesMatchingKids() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptKidsStance(Lifestyle.WantsKids.SOMEDAY, Lifestyle.WantsKids.OPEN)
                    .build());

            candidate.setWantsKids(Lifestyle.WantsKids.SOMEDAY);

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails non-matching kids stance")
        void failsNonMatchingKids() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptKidsStance(Lifestyle.WantsKids.SOMEDAY)
                    .build());

            candidate.setWantsKids(Lifestyle.WantsKids.NO);

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Looking for dealbreaker")
    class LookingForDealbreaker {

        @Test
        @DisplayName("Passes matching relationship goal")
        void passesMatchingGoal() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptLookingFor(Lifestyle.LookingFor.LONG_TERM, Lifestyle.LookingFor.MARRIAGE)
                    .build());

            candidate.setLookingFor(Lifestyle.LookingFor.MARRIAGE);

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails non-matching relationship goal")
        void failsNonMatchingGoal() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptLookingFor(Lifestyle.LookingFor.MARRIAGE)
                    .build());

            candidate.setLookingFor(Lifestyle.LookingFor.CASUAL);

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Height dealbreaker")
    class HeightDealbreaker {

        @Test
        @DisplayName("Passes when height is within range")
        void passesWithinRange() {
            seeker.setDealbreakers(Dealbreakers.builder().heightRange(170, 190).build());

            candidate.setHeightCm(180);

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when height is below minimum")
        void failsBelowMin() {
            seeker.setDealbreakers(Dealbreakers.builder().heightRange(175, null).build());

            candidate.setHeightCm(170);

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when height is above maximum")
        void failsAboveMax() {
            seeker.setDealbreakers(Dealbreakers.builder().heightRange(null, 180).build());

            candidate.setHeightCm(185);

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate height is null (not entered)")
        void failsNullHeight() {
            seeker.setDealbreakers(Dealbreakers.builder().heightRange(170, 190).build());

            // candidate height not set

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Age difference dealbreaker")
    class AgeDealbreaker {

        @Test
        @DisplayName("Passes when age difference is within limit")
        void passesWithinLimit() {
            seeker.setDealbreakers(Dealbreakers.builder().maxAgeDifference(5).build());

            // seeker is 30, candidate is 28 - difference is 2

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when age difference exceeds limit")
        void failsExceedsLimit() {
            seeker.setDealbreakers(Dealbreakers.builder().maxAgeDifference(1).build());

            // seeker is 30, candidate is 28 - difference is 2

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Edge case: exactly at limit passes")
        void passesExactlyAtLimit() {
            seeker.setDealbreakers(Dealbreakers.builder().maxAgeDifference(2).build());

            // seeker is 30, candidate is 28 - difference is 2

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Multiple dealbreakers")
    class MultipleDealbreakers {

        @Test
        @DisplayName("Must pass ALL dealbreakers")
        void mustPassAll() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .acceptDrinking(Lifestyle.Drinking.SOCIALLY)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.NEVER);
            candidate.setDrinking(Lifestyle.Drinking.REGULARLY); // fails this one

            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Passes when all dealbreakers satisfied")
        void passesAllSatisfied() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .acceptDrinking(Lifestyle.Drinking.NEVER, Lifestyle.Drinking.SOCIALLY)
                    .maxAgeDifference(5)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.NEVER);
            candidate.setDrinking(Lifestyle.Drinking.SOCIALLY);
            // age difference is 2, within limit

            assertTrue(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Null candidate fields")
    class NullCandidateFields {

        @Test
        @DisplayName("Fails when candidate has null drinking with dealbreaker")
        void failsNullDrinking() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptDrinking(Lifestyle.Drinking.NEVER)
                    .build());

            // candidate drinking is null
            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate has null wantsKids with dealbreaker")
        void failsNullWantsKids() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptKidsStance(Lifestyle.WantsKids.OPEN)
                    .build());

            // candidate wantsKids is null
            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate has null lookingFor with dealbreaker")
        void failsNullLookingFor() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptLookingFor(Lifestyle.LookingFor.LONG_TERM)
                    .build());

            // candidate lookingFor is null
            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate has null education with dealbreaker")
        void failsNullEducation() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .requireEducation(Lifestyle.Education.BACHELORS)
                    .build());

            // candidate education is null
            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }

        @Test
        @DisplayName("Fails when candidate has null height with height dealbreaker")
        void failsNullHeight() {
            seeker.setDealbreakers(Dealbreakers.builder().minHeight(160).build());

            // height is null by default and should not be a blocker
            assertFalse(Dealbreakers.Evaluator.passes(seeker, candidate));
        }
    }

    @Nested
    @DisplayName("Failed dealbreakers list")
    class FailedDealbreakers {

        @Test
        @DisplayName("Returns empty list when all pass")
        void emptyWhenAllPass() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.NEVER);

            List<String> failures = Dealbreakers.Evaluator.getFailedDealbreakers(seeker, candidate);
            assertTrue(failures.isEmpty());
        }

        @Test
        @DisplayName("Returns failure reasons when dealbreakers fail")
        void returnsFailureReasons() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .acceptDrinking(Lifestyle.Drinking.NEVER)
                    .build());

            candidate.setSmoking(Lifestyle.Smoking.REGULARLY);
            candidate.setDrinking(Lifestyle.Drinking.REGULARLY);

            List<String> failures = Dealbreakers.Evaluator.getFailedDealbreakers(seeker, candidate);
            assertEquals(2, failures.size());
        }

        @Test
        @DisplayName("Indicates when field is not specified")
        void indicatesNotSpecified() {
            seeker.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build());

            // candidate smoking not set

            List<String> failures = Dealbreakers.Evaluator.getFailedDealbreakers(seeker, candidate);
            assertEquals(1, failures.size());
            assertTrue(failures.get(0).contains("not specified"));
        }
    }
}
