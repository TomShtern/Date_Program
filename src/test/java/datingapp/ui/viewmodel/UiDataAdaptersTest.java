package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import datingapp.core.model.Match;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiMatchDataAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UiDataAdapters paging boundary")
class UiDataAdaptersTest {

    @Test
    @DisplayName("StorageUiMatchDataAccess returns a UI-owned page wrapper")
    void storageUiMatchDataAccessReturnsUiOwnedPageWrapper() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();

        var currentUser = TestUserFactory.createActiveUser(java.util.UUID.randomUUID(), "Current");
        var otherUser = TestUserFactory.createActiveUser(java.util.UUID.randomUUID(), "Other");
        users.save(currentUser);
        users.save(otherUser);
        interactions.save(Match.create(currentUser.getId(), otherUser.getId()));

        StorageUiMatchDataAccess access = new StorageUiMatchDataAccess(interactions, trustSafetyStorage);

        UiDataAdapters.UiPage<Match> page = access.getPageOfActiveMatchesFor(currentUser.getId(), 0, 10);

        assertEquals(1, page.items().size());
        assertEquals(1, page.totalCount());
        assertFalse(page.hasMore());
    }
}
