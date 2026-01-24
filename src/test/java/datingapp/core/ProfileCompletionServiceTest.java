package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileCompletionService")
class ProfileCompletionServiceTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(UUID.randomUUID(), "TestUser");
    }

    @Nested
    @DisplayName("calculate()")
    class Calculate {

        @Test
        @DisplayName("returns Starter tier for empty profile")
        void returnsStarterTierForEmptyProfile() {
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);

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

            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);

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

            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);

            assertTrue(result.score() >= 90);
            assertEquals("Diamond", result.tier());
            assertEquals("💎", result.getTierEmoji());
        }

        @Test
        @DisplayName("includes all category breakdowns")
        void includesAllCategoryBreakdowns() {
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);

            List<ProfileCompletionService.CategoryBreakdown> breakdown = result.breakdown();
            assertEquals(4, breakdown.size());

            List<String> categories = breakdown.stream()
                    .map(ProfileCompletionService.CategoryBreakdown::category)
                    .toList();
            assertTrue(categories.contains("Basic Info"));
            assertTrue(categories.contains("Interests"));
            assertTrue(categories.contains("Lifestyle"));
            assertTrue(categories.contains("Preferences"));
        }

        @Test
        @DisplayName("provides actionable next steps")
        void providesActionableNextSteps() {
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);

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
            ProfileCompletionService.CompletionResult noInterests = ProfileCompletionService.calculate(user);
            int interestsScoreEmpty = noInterests.breakdown().stream()
                    .filter(b -> b.category().equals("Interests"))
                    .findFirst()
                    .orElseThrow()
                    .score();

            user.setInterests(EnumSet.of(Interest.HIKING, Interest.COOKING));
            ProfileCompletionService.CompletionResult someInterests = ProfileCompletionService.calculate(user);
            int interestsScoreSome = someInterests.breakdown().stream()
                    .filter(b -> b.category().equals("Interests"))
                    .findFirst()
                    .orElseThrow()
                    .score();

            user.setInterests(
                    EnumSet.of(Interest.HIKING, Interest.COOKING, Interest.READING, Interest.MOVIES, Interest.MUSIC));
            ProfileCompletionService.CompletionResult fullInterests = ProfileCompletionService.calculate(user);
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
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);

            String display = result.getDisplayString();
            assertTrue(display.contains("%"));
            assertTrue(display.contains(result.tier()));
        }

        @Test
        @DisplayName("getTierEmoji returns correct emoji")
        void getTierEmojiReturnsCorrectEmoji() {
            assertEquals(
                    "🌱",
                    new ProfileCompletionService.CompletionResult(10, "Starter", 1, 10, List.of(), List.of())
                            .getTierEmoji());
            assertEquals(
                    "🥉",
                    new ProfileCompletionService.CompletionResult(30, "Bronze", 3, 10, List.of(), List.of())
                            .getTierEmoji());
            assertEquals(
                    "🥈",
                    new ProfileCompletionService.CompletionResult(60, "Silver", 6, 10, List.of(), List.of())
                            .getTierEmoji());
            assertEquals(
                    "🥇",
                    new ProfileCompletionService.CompletionResult(80, "Gold", 8, 10, List.of(), List.of())
                            .getTierEmoji());
            assertEquals(
                    "💎",
                    new ProfileCompletionService.CompletionResult(95, "Diamond", 9, 10, List.of(), List.of())
                            .getTierEmoji());
        }
    }

    @Nested
    @DisplayName("renderProgressBar()")
    class RenderProgressBar {

        @Test
        @DisplayName("renders empty bar for 0%")
        void rendersEmptyBarForZero() {
            String bar = ProfileCompletionService.renderProgressBar(0, 10);
            assertEquals("[░░░░░░░░░░]", bar);
        }

        @Test
        @DisplayName("renders full bar for 100%")
        void rendersFullBarFor100() {
            String bar = ProfileCompletionService.renderProgressBar(100, 10);
            assertEquals("[██████████]", bar);
        }

        @Test
        @DisplayName("renders partial bar for 50%")
        void rendersPartialBarFor50() {
            String bar = ProfileCompletionService.renderProgressBar(50, 10);
            assertEquals("[█████░░░░░]", bar);
        }
    }
}
