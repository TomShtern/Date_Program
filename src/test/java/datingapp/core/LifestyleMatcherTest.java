package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.matching.PreferencesMatcher;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PreferencesMatcher - Lifestyle")
class PreferencesMatcherLifestyleTest {

    @Test
    @DisplayName("isAcceptable returns true when allowed set is empty")
    void isAcceptableWithEmptyAllowedSet() {
        assertTrue(PreferencesMatcher.isAcceptable(Lifestyle.Smoking.NEVER, EnumSet.noneOf(Lifestyle.Smoking.class)));
    }

    @Test
    @DisplayName("isAcceptable enforces containment when allowed set exists")
    void isAcceptableEnforcesContainment() {
        EnumSet<Lifestyle.Drinking> allowed = EnumSet.of(Lifestyle.Drinking.SOCIALLY);
        assertTrue(PreferencesMatcher.isAcceptable(Lifestyle.Drinking.SOCIALLY, allowed));
        assertFalse(PreferencesMatcher.isAcceptable(Lifestyle.Drinking.REGULARLY, allowed));
    }

    @Test
    @DisplayName("kids stances compatibility supports OPEN and SOMEDAY-HAS_KIDS pairing")
    void kidsStancesCompatibilityRules() {
        assertTrue(PreferencesMatcher.areKidsStancesCompatible(Lifestyle.WantsKids.OPEN, Lifestyle.WantsKids.NO));
        assertTrue(
                PreferencesMatcher.areKidsStancesCompatible(Lifestyle.WantsKids.SOMEDAY, Lifestyle.WantsKids.HAS_KIDS));
        assertFalse(PreferencesMatcher.areKidsStancesCompatible(Lifestyle.WantsKids.NO, Lifestyle.WantsKids.HAS_KIDS));
    }
}
