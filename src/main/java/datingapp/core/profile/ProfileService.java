package datingapp.core.profile;

import datingapp.core.AppConfig;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ProfileService {

    private final AppConfig config;
    private final AnalyticsStorage analyticsStorage;
    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final UserStorage userStorage;
    private final ProfileCompletionSupport completionSupport;
    private final AchievementService achievementService;

    public ProfileService(
            AppConfig config,
            AnalyticsStorage analyticsStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            UserStorage userStorage) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.completionSupport = new ProfileCompletionSupport();
        this.achievementService = new DefaultAchievementService(
                this.config,
                this.analyticsStorage,
                this.interactionStorage,
                this.trustSafetyStorage,
                this.userStorage,
                this);
    }

    public List<User> listUsers() {
        return userStorage.findAll();
    }

    public Optional<User> getUserById(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return userStorage.get(userId);
    }

    public Map<UUID, User> getUsersByIds(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds cannot be null");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userStorage.findByIds(userIds);
    }

    public static record CompletionResult(
            int score,
            String tier,
            int filledFields,
            int totalFields,
            List<CategoryBreakdown> breakdown,
            List<String> nextSteps) {
        public CompletionResult {
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be 0-100, got: " + score);
            }
            Objects.requireNonNull(tier, "tier cannot be null");
            breakdown = breakdown != null ? List.copyOf(breakdown) : List.of();
            nextSteps = nextSteps != null ? List.copyOf(nextSteps) : List.of();
        }

        public int percentage() {
            return score;
        }

        public String getTierLabel() {
            return tier;
        }

        public String getTierEmoji() {
            return ProfileCompletionSupport.tierEmojiForScore(score);
        }

        public String getDisplayString() {
            return score + "% " + tier;
        }
    }

    public static record AchievementProgress(Achievement achievement, int current, int target, boolean unlocked) {
        public int getProgressPercent() {
            if (unlocked || target <= 0) {
                return 100;
            }
            return Math.min(100, current * 100 / target);
        }

        public String getProgressDisplay() {
            if (unlocked) {
                return "✓ Unlocked";
            }
            int displayCurrent = Math.min(current, target);
            return displayCurrent + "/" + target;
        }
    }

    public static record CategoryBreakdown(
            String category, int score, List<String> filledItems, List<String> missingItems) {
        public CategoryBreakdown {
            Objects.requireNonNull(category, "category cannot be null");
            filledItems = filledItems != null ? List.copyOf(filledItems) : List.of();
            missingItems = missingItems != null ? List.copyOf(missingItems) : List.of();
        }
    }

    public static record ProfileCompleteness(int percentage, List<String> filledFields, List<String> missingFields) {
        public ProfileCompleteness {
            filledFields = filledFields != null ? List.copyOf(filledFields) : List.of();
            missingFields = missingFields != null ? List.copyOf(missingFields) : List.of();
        }
    }

    public static record ProfilePreview(
            User user,
            ProfileCompleteness completeness,
            List<String> improvementTips,
            String displayBio,
            String displayLookingFor) {
        public ProfilePreview {
            Objects.requireNonNull(user);
            Objects.requireNonNull(completeness);
            improvementTips = improvementTips != null ? List.copyOf(improvementTips) : List.of();
        }
    }

    public CompletionResult calculate(User user) {
        return completionSupport.calculate(user);
    }

    public ProfilePreview generatePreview(User user) {
        return completionSupport.generatePreview(user);
    }

    public ProfileCompleteness calculateCompleteness(User user) {
        return completionSupport.calculateCompleteness(user);
    }

    public List<String> generateTips(User user) {
        return completionSupport.generateTips(user);
    }

    public int countLifestyleFields(User user) {
        return completionSupport.countLifestyleFields(user);
    }

    public List<UserAchievement> checkAndUnlock(UUID userId) {
        return achievementService.checkAndUnlock(userId);
    }

    public List<UserAchievement> getUnlocked(UUID userId) {
        return achievementService.getUnlocked(userId);
    }

    public List<AchievementProgress> getProgress(UUID userId) {
        return achievementService.getProgress(userId).stream()
                .map(progress -> new AchievementProgress(
                        progress.achievement(), progress.current(), progress.target(), progress.unlocked()))
                .toList();
    }

    public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
        return achievementService.getProgressByCategory(userId).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .map(progress -> new AchievementProgress(
                                progress.achievement(), progress.current(), progress.target(), progress.unlocked()))
                        .toList()));
    }

    public int countUnlocked(UUID userId) {
        return achievementService.countUnlocked(userId);
    }
}
