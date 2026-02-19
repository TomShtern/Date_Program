package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.core.connection.ConnectionModels;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.ui.UiAnimations;
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
     * Specific test for UserInteractions nested types accessed from CLI.
     */
    @Test
    @DisplayName("UserInteractions nested types must be static")
    void userInteractionsNestedTypesMustBeStatic() {
        assertTrue(
                Modifier.isStatic(ConnectionModels.Like.class.getModifiers()),
                "ConnectionModels.Like must be static to be accessible from other packages");
        assertTrue(
                Modifier.isStatic(ConnectionModels.Block.class.getModifiers()),
                "ConnectionModels.Block must be static to be accessible from other packages");
        assertTrue(
                Modifier.isStatic(ConnectionModels.Report.class.getModifiers()),
                "ConnectionModels.Report must be static to be accessible from other packages");
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
                Modifier.isStatic(ConnectionService.TransitionValidationException.class.getModifiers()),
                "ConnectionService.TransitionValidationException must be static");
    }

    /**
     * Specific test for re-nested User types.
     */
    @Test
    @DisplayName("Re-nested types in User must be public and static")
    void userNestedTypesArePublicAndStatic() {
        assertTrue(Modifier.isPublic(Gender.class.getModifiers()), "User.Gender must be public");
        assertTrue(Modifier.isStatic(Gender.class.getModifiers()), "User.Gender must be static");
        assertTrue(Modifier.isPublic(UserState.class.getModifiers()), "User.UserState must be public");
        assertTrue(Modifier.isStatic(UserState.class.getModifiers()), "User.UserState must be static");
        assertTrue(
                Modifier.isPublic(VerificationMethod.class.getModifiers()), "User.VerificationMethod must be public");
        assertTrue(
                Modifier.isStatic(VerificationMethod.class.getModifiers()), "User.VerificationMethod must be static");
        assertTrue(Modifier.isPublic(ProfileNote.class.getModifiers()), "User.ProfileNote must be public");
        assertTrue(Modifier.isStatic(ProfileNote.class.getModifiers()), "User.ProfileNote must be static");
    }

    @Test
    @DisplayName("Re-nested types in Match must be public and static")
    void matchNestedTypesArePublicAndStatic() {
        assertTrue(Modifier.isPublic(MatchState.class.getModifiers()), "Match.MatchState must be public");
        assertTrue(Modifier.isStatic(MatchState.class.getModifiers()), "Match.MatchState must be static");
        assertTrue(
                Modifier.isPublic(MatchArchiveReason.class.getModifiers()), "Match.MatchArchiveReason must be public");
        assertTrue(
                Modifier.isStatic(MatchArchiveReason.class.getModifiers()), "Match.MatchArchiveReason must be static");
    }

    @Test
    @DisplayName("Cross-package nested types used by CLI/UI are accessible")
    void crossPackageNestedTypesAccessible() {
        assertTrue(Modifier.isPublic(PacePreferences.class.getModifiers()), "PacePreferences must be public");
        assertTrue(Modifier.isPublic(RecommendationService.DailyPick.class.getModifiers()), "DailyPick must be public");
        assertTrue(Modifier.isPublic(InputReader.class.getModifiers()), "InputReader must be public");
        assertTrue(
                Modifier.isPublic(UiAnimations.ConfettiAnimation.class.getModifiers()),
                "ConfettiAnimation must be public");
    }
}
