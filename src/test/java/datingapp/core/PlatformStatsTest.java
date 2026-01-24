package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.Stats.PlatformStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for PlatformStats domain model. */
class PlatformStatsTest {

    @Test
    @DisplayName("create() factory generates ID and timestamp")
    void createFactoryGeneratesIdAndTimestamp() {
        PlatformStats stats = PlatformStats.create(
                100, // totalActiveUsers
                50.0, // avgLikesReceived
                45.0, // avgLikesGiven
                0.35, // avgMatchRate
                0.65 // avgLikeRatio
                );

        assertNotNull(stats.id(), "ID should be generated");
        assertNotNull(stats.computedAt(), "Timestamp should be set");
        assertEquals(100, stats.totalActiveUsers());
        assertEquals(50.0, stats.avgLikesReceived());
        assertEquals(45.0, stats.avgLikesGiven());
        assertEquals(0.35, stats.avgMatchRate());
        assertEquals(0.65, stats.avgLikeRatio());
    }

    @Test
    @DisplayName("empty() factory returns default values for new platform")
    void emptyFactoryReturnsDefaults() {
        PlatformStats empty = PlatformStats.empty();

        assertNotNull(empty.id(), "ID should be generated");
        assertNotNull(empty.computedAt(), "Timestamp should be set");
        assertEquals(0, empty.totalActiveUsers(), "New platform has 0 users");
        assertEquals(0.0, empty.avgLikesReceived(), "No likes received");
        assertEquals(0.0, empty.avgLikesGiven(), "No likes given");
        assertEquals(0.0, empty.avgMatchRate(), "No matches yet");
        assertEquals(0.5, empty.avgLikeRatio(), "Default 50% like ratio");
    }

    @Test
    @DisplayName("IDs are unique for multiple stats snapshots")
    void idsAreUnique() {
        PlatformStats stats1 = PlatformStats.create(100, 50.0, 45.0, 0.35, 0.65);
        PlatformStats stats2 = PlatformStats.create(100, 50.0, 45.0, 0.35, 0.65);

        assertNotEquals(stats1.id(), stats2.id(), "Different snapshots should have unique IDs");
    }

    @Test
    @DisplayName("empty() creates unique instances")
    void emptyCreatesUniqueInstances() {
        PlatformStats empty1 = PlatformStats.empty();
        PlatformStats empty2 = PlatformStats.empty();

        assertNotEquals(empty1.id(), empty2.id(), "Each empty() call should generate unique ID");
    }

    @Test
    @DisplayName("Record equality works correctly")
    void recordEquality() {
        PlatformStats stats1 = new PlatformStats(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                java.time.Instant.parse("2026-01-10T12:00:00Z"),
                100,
                50.0,
                45.0,
                0.35,
                0.65);

        PlatformStats stats2 = new PlatformStats(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                java.time.Instant.parse("2026-01-10T12:00:00Z"),
                100,
                50.0,
                45.0,
                0.35,
                0.65);

        assertEquals(stats1, stats2, "Records with same values should be equal");
        assertEquals(stats1.hashCode(), stats2.hashCode(), "Hash codes should match");
    }

    @Test
    @DisplayName("Accepts zero and negative values without validation")
    void acceptsZeroAndNegativeValues() {
        // Note: PlatformStats has no compact constructor validation
        // This test documents the current behavior
        assertDoesNotThrow(() -> PlatformStats.create(0, -10.0, -5.0, -0.5, -1.0));
    }
}
