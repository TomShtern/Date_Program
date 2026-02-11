package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliSupport.InputReader;
import datingapp.core.model.*;
import datingapp.core.model.Preferences.PacePreferences;
import datingapp.core.service.*;
import datingapp.ui.util.UiAnimations;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that nested types are static and key top-level types are public.
 * Non-static nested types (inner classes) cannot be accessed from outside their
 * package, causing compilation errors like "X is not public in Y; cannot be
 * accessed from outside package".
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class NestedTypeVisibilityTest {

    /**
     * Specific test for User nested types since they are frequently accessed from CLI
     * package.
     */
    @Test
    @DisplayName("User nested types must be static")
    void userNestedTypesMustBeStatic() {
        assertTrue(
                Modifier.isStatic(User.ProfileNote.class.getModifiers()),
                "User.ProfileNote must be static to be accessible from other packages");
    }

    /**
     * Specific test for UserInteractions nested types accessed from CLI.
     */
    @Test
    @DisplayName("UserInteractions nested types must be static")
    void userInteractionsNestedTypesMustBeStatic() {
        assertTrue(
                Modifier.isStatic(UserInteractions.Like.class.getModifiers()),
                "UserInteractions.Like must be static to be accessible from other packages");
        assertTrue(
                Modifier.isStatic(UserInteractions.Block.class.getModifiers()),
                "UserInteractions.Block must be static to be accessible from other packages");
        assertTrue(
                Modifier.isStatic(UserInteractions.Report.class.getModifiers()),
                "UserInteractions.Report must be static to be accessible from other packages");
    }

    /**
     * Specific test for service nested types accessed from CLI.
     */
    @Test
    @DisplayName("Service nested types must be static")
    void serviceNestedTypesMustBeStatic() {
        assertTrue(
                Modifier.isStatic(MatchQualityService.MatchQuality.class.getModifiers()),
                "MatchQualityService.MatchQuality must be static");
        assertTrue(
                Modifier.isStatic(MatchQualityService.InterestMatcher.class.getModifiers()),
                "MatchQualityService.InterestMatcher must be static");
        assertTrue(
                Modifier.isStatic(MatchingService.SwipeResult.class.getModifiers()),
                "MatchingService.SwipeResult must be static");
        assertTrue(
                Modifier.isStatic(RelationshipTransitionService.TransitionValidationException.class.getModifiers()),
                "RelationshipTransitionService.TransitionValidationException must be static");
    }

    /**
     * Specific test for CliUtilities nested types accessed from Main.
     */
    @Test
    @DisplayName("Top-level types used across packages are public")
    void topLevelTypesArePublic() {
        assertTrue(Modifier.isPublic(User.Gender.class.getModifiers()), "User.Gender must be public");
        assertTrue(Modifier.isPublic(User.UserState.class.getModifiers()), "User.UserState must be public");
        assertTrue(
                Modifier.isPublic(User.VerificationMethod.class.getModifiers()),
                "User.VerificationMethod must be public");
        assertTrue(Modifier.isPublic(PacePreferences.class.getModifiers()), "PacePreferences must be public");
        assertTrue(Modifier.isPublic(DailyService.DailyPick.class.getModifiers()), "DailyPick must be public");
        assertTrue(Modifier.isPublic(InputReader.class.getModifiers()), "InputReader must be public");
        assertTrue(
                Modifier.isPublic(UiAnimations.ConfettiAnimation.class.getModifiers()),
                "ConfettiAnimation must be public");
    }
}
