package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ProfileService")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfileCompletionServiceTest {

    private ProfileService service;
    private User user;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        service = new ProfileService(AppConfig.defaults());
        user = new User(UUID.randomUUID(), "TestUser");
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("calculate()")
    class Calculate {

        @Test
        @DisplayName("returns Starter tier for empty profile")
        void returnsStarterTierForEmptyProfile() {
            ProfileService.CompletionResult result = service.calculate(user);

            assertEquals("Starter", result.tier());
            assertTrue(result.score() < 25);
            assertFalse(result.nextSteps().isEmpty());
        }

        @Test
        @DisplayName("returns higher score with basic info filled")
        void returnsHigherScoreWithBasicInfo() {
            user.setBio("I love hiking and coffee");
            user.setBirthDate(LocalDate.of(1990, 5, 15));
            user.setGender(User.Gender.FEMALE);
            user.setInterestedIn(Set.of(User.Gender.MALE));
            user.addPhotoUrl("https://example.com/photo.jpg");

            ProfileService.CompletionResult result = service.calculate(user);

            assertTrue(result.score() > 30);
            assertTrue(result.tier().equals("Bronze") || result.tier().equals("Silver"));
        }

        @Test
        @DisplayName("returns Diamond tier for fully complete profile")
        void returnsDiamondTierForFullProfile() {
            // Fill all fields
            user.setBio("Complete bio");
            user.setBirthDate(LocalDate.of(1990, 5, 15));
            user.setGender(User.Gender.MALE);
            user.setInterestedIn(Set.of(User.Gender.FEMALE));
            user.addPhotoUrl("https://example.com/photo.jpg");
            user.setLocation(32.0, 34.0);
            user.setAgeRange(25, 35);

            // Lifestyle
            user.setHeightCm(180);
            user.setSmoking(Lifestyle.Smoking.NEVER);
            user.setDrinking(Lifestyle.Drinking.SOCIALLY);
            user.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
            user.setLookingFor(Lifestyle.LookingFor.LONG_TERM);

            // Interests (5+)
            user.setInterests(
                    EnumSet.of(Interest.HIKING, Interest.COOKING, Interest.READING, Interest.MOVIES, Interest.MUSIC));

            // Dealbreakers
            user.setDealbreakers(Dealbreakers.builder()
                    .acceptSmoking(Lifestyle.Smoking.NEVER)
                    .build());

            ProfileService.CompletionResult result = service.calculate(user);

            assertTrue(result.score() >= 90);
            assertEquals("Diamond", result.tier());
            assertEquals("💎", result.getTierEmoji());
        }

        @Test
        @DisplayName("includes all category breakdowns")
        void includesAllCategoryBreakdowns() {
            ProfileService.CompletionResult result = service.calculate(user);

            List<ProfileService.CategoryBreakdown> breakdown = result.breakdown();
            assertEquals(4, breakdown.size());

            List<String> categories = breakdown.stream()
                    .map(ProfileService.CategoryBreakdown::category)
                    .toList();
            assertTrue(categories.contains("Basic Info"));
            assertTrue(categories.contains("Interests"));
            assertTrue(categories.contains("Lifestyle"));
            assertTrue(categories.contains("Preferences"));
        }

        @Test
        @DisplayName("provides actionable next steps")
        void providesActionableNextSteps() {
            ProfileService.CompletionResult result = service.calculate(user);

            assertFalse(result.nextSteps().isEmpty());
            // Should suggest adding bio and photo at minimum
            boolean hasBioTip =
                    result.nextSteps().stream().anyMatch(s -> s.toLowerCase().contains("bio"));
            boolean hasPhotoTip =
                    result.nextSteps().stream().anyMatch(s -> s.toLowerCase().contains("photo"));
            assertTrue(hasBioTip || hasPhotoTip);
        }

        @Test
        @DisplayName("interests score scales with count")
        void interestsScoreScalesWithCount() {
            ProfileService.CompletionResult noInterests = service.calculate(user);
            int interestsScoreEmpty = noInterests.breakdown().stream()
                    .filter(b -> b.category().equals("Interests"))
                    .findFirst()
                    .orElseThrow()
                    .score();

            user.setInterests(EnumSet.of(Interest.HIKING, Interest.COOKING));
            ProfileService.CompletionResult someInterests = service.calculate(user);
            int interestsScoreSome = someInterests.breakdown().stream()
                    .filter(b -> b.category().equals("Interests"))
                    .findFirst()
                    .orElseThrow()
                    .score();

            user.setInterests(
                    EnumSet.of(Interest.HIKING, Interest.COOKING, Interest.READING, Interest.MOVIES, Interest.MUSIC));
            ProfileService.CompletionResult fullInterests = service.calculate(user);
            int interestsScoreFull = fullInterests.breakdown().stream()
                    .filter(b -> b.category().equals("Interests"))
                    .findFirst()
                    .orElseThrow()
                    .score();

            assertEquals(0, interestsScoreEmpty);
            assertTrue(interestsScoreSome > interestsScoreEmpty);
            assertEquals(100, interestsScoreFull);
        }
    }

    @Nested
    @DisplayName("CompletionResult")
    class CompletionResultTest {

        @Test
        @DisplayName("getDisplayString formats correctly")
        void getDisplayStringFormatsCorrectly() {
            ProfileService.CompletionResult result = service.calculate(user);

            String display = result.getDisplayString();
            assertTrue(display.contains("%"));
            assertTrue(display.contains(result.tier()));
        }

        @Test
        @DisplayName("getTierEmoji returns correct emoji")
        void getTierEmojiReturnsCorrectEmoji() {
            // Tier thresholds: <50=Starter, 50-69=Bronze, 70-84=Silver, 85-94=Gold,
            // 95+=Diamond
            assertEquals(
                    "🌱",
                    new ProfileService.CompletionResult(10, "Starter", 1, 10, List.of(), List.of()).getTierEmoji());
            assertEquals(
                    "🥉",
                    new ProfileService.CompletionResult(50, "Bronze", 5, 10, List.of(), List.of()).getTierEmoji());
            assertEquals(
                    "🥈",
                    new ProfileService.CompletionResult(70, "Silver", 7, 10, List.of(), List.of()).getTierEmoji());
            assertEquals(
                    "🥇", new ProfileService.CompletionResult(85, "Gold", 8, 10, List.of(), List.of()).getTierEmoji());
            assertEquals(
                    "💎",
                    new ProfileService.CompletionResult(95, "Diamond", 9, 10, List.of(), List.of()).getTierEmoji());
        }
    }

    @Nested
    @DisplayName("renderProgressBar()")
    class RenderProgressBar {

        @Test
        @DisplayName("renders empty bar for 0%")
        void rendersEmptyBarForZero() {
            String bar = ProfileService.renderProgressBar(0, 10);
            assertEquals("[----------] 0%", bar);
        }

        @Test
        @DisplayName("renders full bar for 100%")
        void rendersFullBarFor100() {
            String bar = ProfileService.renderProgressBar(100, 10);
            assertEquals("[##########] 100%", bar);
        }

        @Test
        @DisplayName("renders partial bar for 50%")
        void rendersPartialBarFor50() {
            String bar = ProfileService.renderProgressBar(50, 10);
            assertEquals("[#####-----] 50%", bar);
        }

        @Test
        @DisplayName("renders Unicode progress bar correctly")
        void rendersUnicodeProgressBar() {
            assertEquals("█████", ProfileService.renderProgressBar(1.0, 5));
            assertEquals("░░░░░", ProfileService.renderProgressBar(0.0, 5));
            assertEquals("██░░░", ProfileService.renderProgressBar(0.4, 5));
        }
    }

    @Nested
    @DisplayName("calculateCompleteness()")
    class CalculateCompleteness {

        @Test
        @DisplayName("returns 100% for fully complete user")
        void returns100PercentForFullUser() {
            User fullUser = createFullUser();
            ProfileService.ProfileCompleteness result = service.calculateCompleteness(fullUser);

            assertEquals(100, result.percentage(), "Full user should be 100% complete");
            assertTrue(result.missingFields().isEmpty(), "Full user should have no missing fields");
            assertFalse(result.filledFields().isEmpty(), "Full user should have filled fields");
        }

        @Test
        @DisplayName("tracks incremental progress")
        void tracksIncrementalProgress() {
            User progressUser = new User(UUID.randomUUID(), "Progress Pete");
            int score0 = service.calculateCompleteness(progressUser).percentage();

            progressUser.setBio("Just a short bio.");
            int score1 = service.calculateCompleteness(progressUser).percentage();
            assertTrue(score1 > score0, "Adding bio should increase score");

            progressUser.addPhotoUrl("http://example.com/p.jpg");
            int score2 = service.calculateCompleteness(progressUser).percentage();
            assertTrue(score2 > score1, "Adding photo should increase score");

            progressUser.setHeightCm(180);
            int score3 = service.calculateCompleteness(progressUser).percentage();
            assertTrue(score3 > score2, "Adding height should increase score");
        }
    }

    @Nested
    @DisplayName("generatePreview()")
    class GeneratePreview {

        @Test
        @DisplayName("returns valid preview structure")
        void returnsValidPreviewStructure() {
            User fullUser = createFullUser();
            ProfileService.ProfilePreview preview = service.generatePreview(fullUser);

            assertEquals(fullUser, preview.user());
            assertNotNull(preview.completeness());
            assertNotNull(preview.improvementTips());
            assertEquals(fullUser.getBio(), preview.displayBio());
            assertEquals("Long-term relationship", preview.displayLookingFor());
        }
    }

    @Nested
    @DisplayName("generateTips()")
    class GenerateTips {

        @Test
        @DisplayName("bio length boundary tips")
        void bioLengthBoundaryTips() {
            assertTrue(hasTip(user, "Add a bio"), "Should prompt for bio when missing");

            user.setBio("Short bio string.");
            assertTrue(hasTip(user, "Expand your bio"), "Should prompt to expand short bio");

            user.setBio("This bio is definitely long enough to suppress the tip about it being too short.");
            assertFalse(hasTip(user, "Expand your bio"), "Should NOT prompt to expand long bio");
        }

        @Test
        @DisplayName("photo count tips")
        void photoCountTips() {
            assertTrue(hasTip(user, "Add a photo"), "0 photos should prompt to add one");

            user.addPhotoUrl("http://img.com/1.jpg");
            assertFalse(hasTip(user, "Add a photo"), "1 photo satisfies requirement");
            assertTrue(hasTip(user, "Add a second photo"), "1 photo should prompt for second");

            user.addPhotoUrl("http://img.com/2.jpg");
            assertFalse(hasTip(user, "Add a second photo"), "2 photos should satisfy all photo tips");
        }

        @Test
        @DisplayName("interest count tips")
        void interestCountTips() {
            assertTrue(
                    hasTip(user, "Add at least " + Interest.MIN_FOR_COMPLETE), "0 interests should prompt to add some");

            user.addInterest(Interest.HIKING);
            assertTrue(hasTip(user, "Add 2 more interest"), "1 interest should prompt for more");

            user.addInterest(Interest.COFFEE);
            user.addInterest(Interest.TRAVEL);
            assertFalse(hasTip(user, "Add more interest"), "3 interests should satisfy minimum");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 9})
        @DisplayName("low distance values trigger tip")
        void lowDistanceValuesTriggertip(int distance) {
            user.setMaxDistanceKm(distance);
            assertTrue(hasTip(user, "distance"), "Distance " + distance + " should trigger tip");
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 50, 100})
        @DisplayName("high distance values do not trigger tip")
        void highDistanceValuesDoNotTriggerTip(int distance) {
            user.setMaxDistanceKm(distance);
            assertFalse(hasTip(user, "distance"), "Distance " + distance + " should NOT trigger tip");
        }
    }

    // === Helper Methods ===

    private boolean hasTip(User targetUser, String fragment) {
        return service.generateTips(targetUser).stream()
                .anyMatch(tip -> tip.toLowerCase().contains(fragment.toLowerCase()));
    }

    private User createFullUser() {
        User full = new User(UUID.randomUUID(), "Complete Alice");
        full.setBio("Detailed bio with more than 50 characters to pass the tip check. Located in New York.");
        full.setBirthDate(AppClock.today().minusYears(25));
        full.setGender(User.Gender.FEMALE);
        full.setInterestedIn(Set.of(User.Gender.MALE));
        full.setMaxDistanceKm(50);
        full.setAgeRange(20, 30);
        full.addPhotoUrl("http://example.com/photo1.jpg");
        full.addPhotoUrl("http://example.com/photo2.jpg");
        full.setHeightCm(170);
        full.setSmoking(Lifestyle.Smoking.NEVER);
        full.setDrinking(Lifestyle.Drinking.SOCIALLY);
        full.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
        full.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
        full.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
        full.setLocation(40.7128, -74.0060);
        return full;
    }
}
