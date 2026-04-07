package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileUseCases profile-note CRUD")
class ProfileUseCasesNotesTest {

    @Test
    @DisplayName("missing author is rejected through the canonical ProfileUseCases construction path")
    void missingAuthorReturnsNotFoundThroughCanonicalProfileUseCasesConstructor() {
        TestStorages.Users users = new TestStorages.Users();
        UUID authorId = UUID.randomUUID();
        users.save(new User(UUID.randomUUID(), "Subject"));

        ProfileUseCases useCases = createProfileUseCases(users, AppConfig.defaults());

        var result = useCases.listProfileNotes(new ProfileNotesUseCases.ProfileNotesQuery(UserContext.ui(authorId)));

        assertFalse(result.success());
        assertEquals(UseCaseError.Code.NOT_FOUND, result.error().code());
        assertEquals("Author not found", result.error().message());
    }

    @Test
    @DisplayName("upsert, get, list, and delete note for an author")
    void upsertGetListAndDeleteNoteForAuthor() {
        TestStorages.Users users = new TestStorages.Users();
        UUID authorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        users.save(new User(authorId, "Author"));
        users.save(new User(subjectId, "Subject"));

        ProfileUseCases useCases = createProfileUseCases(users, AppConfig.defaults());

        var createResult = useCases.upsertProfileNote(new ProfileNotesUseCases.UpsertProfileNoteCommand(
                UserContext.ui(authorId), subjectId, "Met at coffee shop"));
        assertTrue(createResult.success());
        assertEquals("Met at coffee shop", createResult.data().content());

        var getResult =
                useCases.getProfileNote(new ProfileNotesUseCases.ProfileNoteQuery(UserContext.ui(authorId), subjectId));
        assertTrue(getResult.success());
        assertEquals("Met at coffee shop", getResult.data().content());

        var updateResult = useCases.upsertProfileNote(new ProfileNotesUseCases.UpsertProfileNoteCommand(
                UserContext.ui(authorId), subjectId, "Prefers weekend plans"));
        assertTrue(updateResult.success());
        assertEquals("Prefers weekend plans", updateResult.data().content());

        var listResult =
                useCases.listProfileNotes(new ProfileNotesUseCases.ProfileNotesQuery(UserContext.ui(authorId)));
        assertTrue(listResult.success());
        assertEquals(List.of(updateResult.data()), listResult.data());

        var deleteResult = useCases.deleteProfileNote(
                new ProfileNotesUseCases.DeleteProfileNoteCommand(UserContext.ui(authorId), subjectId));
        assertTrue(deleteResult.success());

        var missingResult =
                useCases.getProfileNote(new ProfileNotesUseCases.ProfileNoteQuery(UserContext.ui(authorId), subjectId));
        assertFalse(missingResult.success());
    }

    @Test
    @DisplayName("upsert rejects missing subject user")
    void upsertRejectsMissingSubjectUser() {
        TestStorages.Users users = new TestStorages.Users();
        UUID authorId = UUID.randomUUID();
        users.save(new User(authorId, "Author"));

        ProfileUseCases useCases = createProfileUseCases(users, AppConfig.defaults());
        var result = useCases.upsertProfileNote(new ProfileNotesUseCases.UpsertProfileNoteCommand(
                UserContext.ui(authorId), UUID.randomUUID(), "Hello"));

        assertFalse(result.success());
        assertEquals("Subject user not found", result.error().message());
    }

    @Test
    @DisplayName("upsert rejects content above configured limit")
    void upsertRejectsContentAboveConfiguredLimit() {
        TestStorages.Users users = new TestStorages.Users();
        UUID authorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        users.save(new User(authorId, "Author"));
        users.save(new User(subjectId, "Subject"));

        AppConfig config = AppConfig.builder().maxProfileNoteLength(5).build();
        ProfileUseCases useCases = createProfileUseCases(users, config);

        var result = useCases.upsertProfileNote(
                new ProfileNotesUseCases.UpsertProfileNoteCommand(UserContext.ui(authorId), subjectId, "123456"));

        assertFalse(result.success());
        assertEquals("Note too long (max 5 characters)", result.error().message());
    }

    private static ProfileUseCases createProfileUseCases(TestStorages.Users users, AppConfig config) {
        ValidationService validationService = new ValidationService(config);
        return new ProfileUseCases(
                users,
                new ProfileService(users),
                validationService,
                new ProfileMutationUseCases(
                        users,
                        validationService,
                        TestAchievementService.empty(),
                        config,
                        new ProfileActivationPolicy(),
                        new InProcessAppEventBus()),
                new ProfileNotesUseCases(users, validationService, config, new InProcessAppEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(), null)); // stats/service not used in this test
    }
}
