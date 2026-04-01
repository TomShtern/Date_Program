package datingapp.core.matching;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for managing daily interaction limits (likes, passes) and resets.
 */
public interface DailyLimitService {

    /** Whether the user can perform a like action today. */
    boolean canLike(UUID userId);

    /** Whether the user can perform a super-like action today. */
    boolean canSuperLike(UUID userId);

    /** Whether the user can perform a pass action today. */
    boolean canPass(UUID userId);

    /** Current daily status including counts and time to reset. */
    DailyStatus getStatus(UUID userId);

    /** Time remaining until next daily reset (midnight local time). */
    Duration getTimeUntilReset();

    /** Status snapshot for daily limits. */
    record DailyStatus(
            int likesUsed,
            int likesRemaining,
            int superLikesUsed,
            int superLikesRemaining,
            int passesUsed,
            int passesRemaining,
            LocalDate date,
            Instant resetsAt) {

        public DailyStatus {
            if (likesUsed < 0 || superLikesUsed < 0 || passesUsed < 0) {
                throw new IllegalArgumentException("Usage counts cannot be negative");
            }
            Objects.requireNonNull(date, "date cannot be null");
            Objects.requireNonNull(resetsAt, "resetsAt cannot be null");
        }

        public boolean hasUnlimitedLikes() {
            return likesRemaining < 0;
        }

        public boolean hasUnlimitedPasses() {
            return passesRemaining < 0;
        }

        public boolean hasUnlimitedSuperLikes() {
            return superLikesRemaining < 0;
        }
    }
}
