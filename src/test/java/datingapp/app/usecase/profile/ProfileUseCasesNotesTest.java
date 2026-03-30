package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
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
    @DisplayName("upsert, get, list, and delete note for an author")
    void upsertGetListAndDeleteNoteForAuthor() {
        TestStorages.Users users = new TestStorages.Users();
        UUID authorId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        users.save(new User(authorId, "Author"));
        users.save(new User(subjectId, "Subject"));

        ProfileUseCases useCases = new ProfileUseCases(
                users,
                null,
                null,
                null,
                TestAchievementService.empty(),
                AppConfig.defaults(),
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        var createResult = useCases.upsertProfileNote(new ProfileUseCases.UpsertProfileNoteCommand(
                UserContext.ui(authorId), subjectId, "Met at coffee shop"));
        assertTrue(createResult.success());
        assertEquals("Met at coffee shop", createResult.data().content());

        var getResult =
                useCases.getProfileNote(new ProfileUseCases.ProfileNoteQuery(UserContext.ui(authorId), subjectId));
        assertTrue(getResult.success());
        assertEquals("Met at coffee shop", getResult.data().content());

        var updateResult = useCases.upsertProfileNote(new ProfileUseCases.UpsertProfileNoteCommand(
                UserContext.ui(authorId), subjectId, "Prefers weekend plans"));
        assertTrue(updateResult.success());
        assertEquals("Prefers weekend plans", updateResult.data().content());

        var listResult = useCases.listProfileNotes(new ProfileUseCases.ProfileNotesQuery(UserContext.ui(authorId)));
        assertTrue(listResult.success());
        assertEquals(List.of(updateResult.data()), listResult.data());

        var deleteResult = useCases.deleteProfileNote(
                new ProfileUseCases.DeleteProfileNoteCommand(UserContext.ui(authorId), subjectId));
        assertTrue(deleteResult.success());

        var missingResult =
                useCases.getProfileNote(new ProfileUseCases.ProfileNoteQuery(UserContext.ui(authorId), subjectId));
        assertFalse(missingResult.success());
    }

    @Test
    @DisplayName("upsert rejects missing subject user")
    void upsertRejectsMissingSubjectUser() {
        TestStorages.Users users = new TestStorages.Users();
        UUID authorId = UUID.randomUUID();
        users.save(new User(authorId, "Author"));

        ProfileUseCases useCases = new ProfileUseCases(
                users,
                null,
                null,
                null,
                TestAchievementService.empty(),
                AppConfig.defaults(),
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());
        var result = useCases.upsertProfileNote(
                new ProfileUseCases.UpsertProfileNoteCommand(UserContext.ui(authorId), UUID.randomUUID(), "Hello"));

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
        ProfileUseCases useCases = new ProfileUseCases(
                users,
                null,
                null,
                null,
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        var result = useCases.upsertProfileNote(
                new ProfileUseCases.UpsertProfileNoteCommand(UserContext.ui(authorId), subjectId, "123456"));

        assertFalse(result.success());
        assertEquals(
                "Note content exceeds maximum length of 5 characters",
                result.error().message());
    }
}
