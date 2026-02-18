package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.MatchingService;
import datingapp.core.model.Match;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class EdgeCaseRegressionTest {
    @Test
    @DisplayName("rejects blank names")
    void rejectsBlankNames() {
        ValidationService service = new ValidationService(AppConfig.defaults());

        ValidationService.ValidationResult result = service.validateName("   ");

        assertFalse(result.valid());
    }

    @Test
    @DisplayName("duplicate match creation does not crash")
    void duplicateMatchCreationDoesNotCrash() {
        RaceInteractionStorage interactionStorage = new RaceInteractionStorage();
        MatchingService service = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(new TestStorages.TrustSafety())
                .userStorage(new TestStorages.Users())
                .build();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        service.recordLike(Like.create(userA, userB, Like.Direction.LIKE));
        service.recordLike(Like.create(userB, userA, Like.Direction.LIKE));

        assertDoesNotThrow(() -> service.recordLike(Like.create(userB, userA, Like.Direction.LIKE)));
        assertTrue(interactionStorage.getByUsers(userA, userB).isPresent());
    }

    private static class RaceInteractionStorage extends TestStorages.Interactions {
        @Override
        public void save(Match match) {
            Optional<Match> existing = super.get(match.getId());
            if (existing.isPresent()) {
                throw new RuntimeException("DUPLICATE_KEY: Match already exists " + match.getId());
            }
            super.save(match);
        }

        @Override
        public boolean exists(String matchId) {
            return false;
        }
    }
}
