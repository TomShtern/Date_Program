package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.Match;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MatchingCliPresenterTest {

    private final MatchingCliPresenter presenter = new MatchingCliPresenter();

    @Test
    @DisplayName("renders mutual match swipe feedback")
    void rendersMutualMatchSwipeFeedback() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Like like = Like.create(first, second, Like.Direction.LIKE);
        Match match = Match.create(first, second);
        MatchingService.SwipeResult result = new MatchingService.SwipeResult(true, true, match, like, "It's a match!");

        assertIterableEquals(
                List.of("", "🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉", "You and Riley like each other!", ""),
                presenter.swipeResultLines(result, "Riley"));
    }

    @Test
    @DisplayName("renders daily limit panel using localized lines")
    void rendersDailyLimitPanelUsingLocalizedLines() {
        RecommendationService.DailyStatus status = new RecommendationService.DailyStatus(
                7, 0, 0, 999, 0, 999, LocalDate.now(), Instant.now().plusSeconds(3600));

        List<String> lines = presenter.dailyLimitReachedLines(status, "1h 0m");

        assertEquals("         💔 DAILY LIMIT REACHED", lines.get(2));
        assertEquals("   You've used all 7 likes for today!", lines.get(5));
        assertEquals("   Resets in: 1h 0m", lines.get(7));
    }
}
