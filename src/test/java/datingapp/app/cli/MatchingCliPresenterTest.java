package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import datingapp.app.usecase.matching.MatchingUseCases.SwipeOutcome;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.model.Match;
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
        SwipeOutcome result = new SwipeOutcome(true, match, like, "It's a match!");

        assertIterableEquals(
                List.of("", "🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉", "You and Riley like each other!", ""),
                presenter.swipeResultLines(result, "Riley"));
    }

    @Test
    @DisplayName("renders daily limit panel using localized lines")
    void rendersDailyLimitPanelUsingLocalizedLines() {
        List<String> lines = presenter.dailyLimitReachedLines(7, "1h 0m");

        assertEquals("         💔 DAILY LIMIT REACHED", lines.get(2));
        assertEquals("   You've used all 7 likes for today!", lines.get(5));
        assertEquals("   Resets in: 1h 0m", lines.get(7));
    }
}
