package datingapp.module;

import datingapp.core.AchievementService;
import datingapp.core.AppConfig;
import datingapp.core.ProfilePreviewService;
import datingapp.core.StatsService;
import java.util.Objects;

/**
 * Module containing statistics and achievement services. Handles user stats, platform metrics,
 * achievements, and profile previews.
 *
 * <p>Note: ProfileCompletionService is a utility class with static methods only, so it's not
 * included in this module. Call ProfileCompletionService.calculate() directly.
 */
public record StatsModule(StatsService stats, AchievementService achievements, ProfilePreviewService profilePreview)
        implements Module {

    public StatsModule {
        Objects.requireNonNull(stats, "stats cannot be null");
        Objects.requireNonNull(achievements, "achievements cannot be null");
        Objects.requireNonNull(profilePreview, "profilePreview cannot be null");
    }

    /**
     * Creates a StatsModule with all required services.
     *
     * @param storage The storage module providing data access
     * @param config Application configuration
     * @return Fully configured StatsModule
     */
    public static StatsModule create(StorageModule storage, AppConfig config) {
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        ProfilePreviewService profilePreview = new ProfilePreviewService();

        StatsService stats = new StatsService(
                storage.likes(),
                storage.matches(),
                storage.blocks(),
                storage.reports(),
                storage.userStats(),
                storage.platformStats());

        AchievementService achievements = new AchievementService(
                storage.achievements(),
                storage.matches(),
                storage.likes(),
                storage.users(),
                storage.reports(),
                profilePreview,
                config);

        return new StatsModule(stats, achievements, profilePreview);
    }
}
