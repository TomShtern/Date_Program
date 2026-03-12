package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.matching.LifestyleMatcher;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LifestyleMatcher")
class LifestyleMatcherTest {

    @Test
    @DisplayName("isAcceptable returns true when allowed set is empty")
    void isAcceptableWithEmptyAllowedSet() {
        assertTrue(LifestyleMatcher.isAcceptable(Lifestyle.Smoking.NEVER, EnumSet.noneOf(Lifestyle.Smoking.class)));
    }

    @Test
    @DisplayName("isAcceptable enforces containment when allowed set exists")
    void isAcceptableEnforcesContainment() {
        EnumSet<Lifestyle.Drinking> allowed = EnumSet.of(Lifestyle.Drinking.SOCIALLY);
        assertTrue(LifestyleMatcher.isAcceptable(Lifestyle.Drinking.SOCIALLY, allowed));
        assertFalse(LifestyleMatcher.isAcceptable(Lifestyle.Drinking.REGULARLY, allowed));
    }

    @Test
    @DisplayName("kids stances compatibility supports OPEN and SOMEDAY-HAS_KIDS pairing")
    void kidsStancesCompatibilityRules() {
        assertTrue(LifestyleMatcher.areKidsStancesCompatible(Lifestyle.WantsKids.OPEN, Lifestyle.WantsKids.NO));
        assertTrue(
                LifestyleMatcher.areKidsStancesCompatible(Lifestyle.WantsKids.SOMEDAY, Lifestyle.WantsKids.HAS_KIDS));
        assertFalse(LifestyleMatcher.areKidsStancesCompatible(Lifestyle.WantsKids.NO, Lifestyle.WantsKids.HAS_KIDS));
    }
}
