package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfilePreviewServiceTest {

    private ProfilePreviewService service;
    private User fullUser;
    private User minimalUser;

    @BeforeEach
    void setUp() {
        service = new ProfilePreviewService();

        fullUser = new User(UUID.randomUUID(), "Complete Alice");
        fullUser.setBio("Detailed bio with more than 50 characters to pass the tip check. Located in New York.");
        fullUser.setBirthDate(LocalDate.now().minusYears(25));
        fullUser.setGender(Gender.FEMALE);
        fullUser.setInterestedIn(java.util.Set.of(Gender.MALE));
        fullUser.setMaxDistanceKm(50);
        fullUser.setAgeRange(20, 30);
        fullUser.addPhotoUrl("http://example.com/photo1.jpg");
        fullUser.addPhotoUrl("http://example.com/photo2.jpg");
        fullUser.setHeightCm(170);
        fullUser.setSmoking(Lifestyle.Smoking.NEVER);
        fullUser.setDrinking(Lifestyle.Drinking.SOCIALLY);
        fullUser.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
        fullUser.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
        fullUser.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
        fullUser.setLocation(40.7128, -74.0060);

        minimalUser = new User(UUID.randomUUID(), "Simple Bob");
        minimalUser.setMaxDistanceKm(5); // Small distance to trigger tip
    }

    @Test
    void calculateCompleteness_fullUser_returns100Percent() {
        ProfilePreviewService.ProfileCompleteness result = service.calculateCompleteness(fullUser);

        assertEquals(100, result.percentage(), "Full user should be 100% complete");
        assertTrue(result.missingFields().isEmpty(), "Full user should have no missing fields");
        assertFalse(result.filledFields().isEmpty(), "Full user should have filled fields");
    }

    @Test
    void calculateCompleteness_incrementalProgress() {
        User progressUser = new User(UUID.randomUUID(), "Progress Pete");
        int score0 = service.calculateCompleteness(progressUser).percentage();

        // Add Bio
        progressUser.setBio("Just a short bio.");
        int score1 = service.calculateCompleteness(progressUser).percentage();
        assertTrue(score1 > score0, "Adding bio should increase score");

        // Add Photo
        progressUser.addPhotoUrl("http://example.com/p.jpg");
        int score2 = service.calculateCompleteness(progressUser).percentage();
        assertTrue(score2 > score1, "Adding photo should increase score");

        // Add Lifestyle
        progressUser.setHeightCm(180);
        int score3 = service.calculateCompleteness(progressUser).percentage();
        assertTrue(score3 > score2, "Adding height should increase score");
    }

    @Test
    void generateTips_bioLengthBoundary() {
        User user = new User(UUID.randomUUID(), "Bio Tester");

        // Case 1: No Bio
        assertTrue(hasTip(user, "Add a bio"), "Should prompt for bio when missing");

        // Case 2: Short Bio (< 50 chars)
        user.setBio("Short bio string.");
        assertTrue(hasTip(user, "Expand your bio"), "Should prompt to expand short bio");

        // Case 3: Long Bio (>= 50 chars)
        user.setBio("This bio is definitely long enough to suppress the tip about it being too short.");
        assertFalse(hasTip(user, "Expand your bio"), "Should NOT prompt to expand long bio");
    }

    @Test
    void generateTips_photoCountLogic() {
        User user = new User(UUID.randomUUID(), "Photo Tester");

        // 0 Photos
        assertTrue(hasTip(user, "Add a photo"), "0 photos should prompt to add one");

        // 1 Photo (Required met, but tip for 2nd exists)
        user.addPhotoUrl("http://img.com/1.jpg");
        assertFalse(hasTip(user, "Add a photo"), "1 photo satisfies requirement");
        assertTrue(hasTip(user, "Add a second photo"), "1 photo should prompt for second");

        // 2 Photos
        user.addPhotoUrl("http://img.com/2.jpg");
        assertFalse(hasTip(user, "Add a second photo"), "2 photos should satisfy all photo tips");
    }

    @Test
    void generateTips_interestCountLogic() {
        User user = new User(UUID.randomUUID(), "Interest Tester");

        // 0 Interests
        assertTrue(hasTip(user, "Add at least " + Interest.MIN_FOR_COMPLETE), "0 interests should prompt to add some");

        // 1 Interest
        user.addInterest(Interest.HIKING);
        assertTrue(hasTip(user, "Add 2 more interest"), "1 interest should prompt for more");

        // 3 Interests (MIN_FOR_COMPLETE)
        user.addInterest(Interest.COFFEE);
        user.addInterest(Interest.TRAVEL);
        assertFalse(hasTip(user, "Add more interest"), "3 interests should satisfy minimum");
    }

    @Test
    void generatePreview_structureValidation() {
        ProfilePreviewService.ProfilePreview preview = service.generatePreview(fullUser);

        assertEquals(fullUser, preview.user());
        assertNotNull(preview.completeness());
        assertNotNull(preview.improvementTips());

        // Check display fields
        assertEquals(fullUser.getBio(), preview.displayBio());
        assertEquals("Long-term relationship", preview.displayLookingFor());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 9})
    void generateTips_distanceBoundary_lowValues(int distance) {
        // Distances < 10 should trigger tip
        User user = new User(UUID.randomUUID(), "Distance User");
        user.setMaxDistanceKm(distance);
        assertTrue(hasTip(user, "distance"), "Distance " + distance + " should trigger tip");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100})
    void generateTips_distanceBoundary_highValues(int distance) {
        // Distances >= 10 should NOT trigger tip
        User user = new User(UUID.randomUUID(), "Distance User");
        user.setMaxDistanceKm(distance);
        assertFalse(hasTip(user, "distance"), "Distance " + distance + " should NOT trigger tip");
    }

    // Helper to check for partial tip message match
    private boolean hasTip(User user, String fragment) {
        return service.generateTips(user).stream()
                .anyMatch(tip -> tip.toLowerCase().contains(fragment.toLowerCase()));
    }

    @Test
    void renderProgressBar_drawsCorrectly() {
        assertEquals("█████", ProfilePreviewService.renderProgressBar(1.0, 5));
        assertEquals("░░░░░", ProfilePreviewService.renderProgressBar(0.0, 5));
        assertEquals("██░░░", ProfilePreviewService.renderProgressBar(0.4, 5));
    }
}
