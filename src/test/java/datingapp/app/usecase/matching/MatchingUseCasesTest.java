package datingapp.app.usecase.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
import datingapp.app.usecase.matching.MatchingUseCases.ListActiveMatchesQuery;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.UndoSwipeCommand;
import datingapp.core.AppConfig;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MatchingUseCases")
class MatchingUseCasesTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private MatchingUseCases useCases;
    private User currentUser;
    private User candidate;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        var trustSafetyStorage = new TestStorages.TrustSafety();
        var undoStorage = new TestStorages.Undos();

        currentUser = TestUserFactory.createActiveUser(UUID.randomUUID(), "Current");
        candidate = TestUserFactory.createActiveUser(UUID.randomUUID(), "Candidate");
        candidate.setGender(User.Gender.FEMALE);
        candidate.setInterestedIn(Set.of(User.Gender.MALE));

        userStorage.save(currentUser);
        userStorage.save(candidate);

        var candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                config.safety().userTimeZone());
        var undoService = new UndoService(interactionStorage, undoStorage, config);
        var matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .undoService(undoService)
                .build();

        useCases = new MatchingUseCases(
                candidateFinder, matchingService, null, undoService, interactionStorage, userStorage, null);
    }

    @Test
    @DisplayName("browseCandidates should return compatible users")
    void browseCandidatesReturnsCompatibleUsers() {
        var result = useCases.browseCandidates(
                new BrowseCandidatesCommand(UserContext.cli(currentUser.getId()), currentUser));

        assertTrue(result.success());
        assertFalse(result.data().candidates().isEmpty());
    }

    @Test
    @DisplayName("processSwipe and undoSwipe report configuration/no-history conflicts")
    void processAndUndoReturnConflictsForCurrentSetup() {
        var swipeResult = useCases.processSwipe(
                new ProcessSwipeCommand(UserContext.cli(currentUser.getId()), currentUser, candidate, true, false));

        assertFalse(swipeResult.success());
        assertNotNull(swipeResult.error());

        var undoResult = useCases.undoSwipe(new UndoSwipeCommand(UserContext.cli(currentUser.getId())));
        assertFalse(undoResult.success());
        assertEquals("No recent swipe to undo", undoResult.error().message());
    }

    @Test
    @DisplayName("listActiveMatches should return map of opposite users")
    void listActiveMatchesReturnsUserMap() {
        interactionStorage.save(Match.create(currentUser.getId(), candidate.getId()));

        var result = useCases.listActiveMatches(new ListActiveMatchesQuery(UserContext.cli(currentUser.getId())));

        assertTrue(result.success());
        assertEquals(1, result.data().matches().size());
        assertTrue(result.data().usersById().containsKey(candidate.getId()));
    }
}
