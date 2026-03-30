package datingapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases.DailyStatusQuery;
import datingapp.app.usecase.matching.MatchingUseCases.DailyStatusResult;
import datingapp.app.usecase.profile.ProfileUseCases.CurrentSessionSnapshot;
import datingapp.app.usecase.profile.ProfileUseCases.SessionSummaryQuery;
import datingapp.app.usecase.profile.ProfileUseCases.SessionSummaryResult;
import datingapp.core.model.User;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("Main lifecycle shutdown handling")
class MainLifecycleTest {

    @Test
    @DisplayName("runWithShutdown should always run shutdown when the body throws")
    void runWithShutdownAlwaysInvokesShutdown() {
        AtomicBoolean shutdownCalled = new AtomicBoolean(false);

        assertThrows(
                RuntimeException.class,
                () -> Main.runWithShutdown(
                        () -> {
                            throw new RuntimeException("boom");
                        },
                        () -> shutdownCalled.set(true)));

        assertTrue(shutdownCalled.get());
    }

    @Test
    @DisplayName("menu EOF should stop the main loop")
    void menuEofShouldStopTheMainLoop() {
        InputReader inputReader = new InputReader(new Scanner(new StringReader("")));

        inputReader.readLine("Choose an option: ");

        assertTrue(Main.shouldExitMainMenu(inputReader));
    }

    @Test
    @DisplayName("current user status lines should query use-case seams and format remaining likes")
    void currentUserStatusLinesShouldQueryUseCaseSeamsAndFormatRemainingLikes() {
        User currentUser = new User(UUID.randomUUID(), "Taylor");
        AtomicReference<SessionSummaryQuery> sessionSummaryQuery = new AtomicReference<>();
        AtomicReference<DailyStatusQuery> dailyStatusQuery = new AtomicReference<>();

        List<String> lines = Main.buildCurrentUserStatusLines(
                currentUser,
                query -> {
                    sessionSummaryQuery.set(query);
                    return UseCaseResult.success(
                            new SessionSummaryResult(Optional.of(new CurrentSessionSnapshot(9, 4, 5, "12m"))));
                },
                query -> {
                    dailyStatusQuery.set(query);
                    return UseCaseResult.success(new DailyStatusResult(3, 7, false, "2h 15m"));
                },
                10);

        assertNotNull(sessionSummaryQuery.get());
        assertNotNull(dailyStatusQuery.get());
        assertEquals(
                UserContext.cli(currentUser.getId()), sessionSummaryQuery.get().context());
        assertEquals(
                UserContext.cli(currentUser.getId()), dailyStatusQuery.get().context());
        assertTrue(lines.contains("  Session: 9 swipes (4 likes, 5 passes) | 12m elapsed"));
        assertTrue(lines.contains("  💝 Daily Likes: 7/10 remaining"));
    }

    @Test
    @DisplayName("current user status lines should show unlimited likes when the use case says so")
    void currentUserStatusLinesShouldShowUnlimitedLikes() {
        User currentUser = new User(UUID.randomUUID(), "Riley");

        List<String> lines = Main.buildCurrentUserStatusLines(
                currentUser,
                query -> UseCaseResult.success(new SessionSummaryResult(Optional.empty())),
                query -> UseCaseResult.success(new DailyStatusResult(0, 0, true, "0m")),
                10);

        assertTrue(lines.contains("  💝 Daily Likes: unlimited"));
    }
}
