package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliUtilities;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that all nested types in the core package are declared as static.
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
        assertTrue(
                Modifier.isStatic(User.State.class.getModifiers()),
                "User.State must be static to be accessible from other packages");
        assertTrue(
                Modifier.isStatic(User.Gender.class.getModifiers()),
                "User.Gender must be static to be accessible from other packages");
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
                Modifier.isStatic(DailyService.DailyPick.class.getModifiers()),
                "DailyService.DailyPick must be static");
        assertTrue(
                Modifier.isStatic(RelationshipTransitionService.TransitionValidationException.class.getModifiers()),
                "RelationshipTransitionService.TransitionValidationException must be static");
    }

    /**
     * Specific test for CliUtilities nested types accessed from Main.
     */
    @Test
    @DisplayName("CliUtilities nested types must be static")
    void cliUtilitiesNestedTypesMustBeStatic() {
        assertTrue(
                Modifier.isStatic(CliUtilities.InputReader.class.getModifiers()),
                "CliUtilities.InputReader must be static to be accessible from Main");
    }
}
