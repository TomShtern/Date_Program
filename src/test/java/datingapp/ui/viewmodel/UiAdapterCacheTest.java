package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.ServiceRegistry;
import datingapp.core.testutil.TestServiceRegistryBuilder;
import datingapp.ui.viewmodel.UiDataAdapters.UiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("UiAdapterCache")
class UiAdapterCacheTest {

    @Test
    @DisplayName("userStore is cached and returns the same instance on repeated calls")
    void userStoreIsCachedUntilReset() {
        UiAdapterCache cache = new UiAdapterCache();
        ServiceRegistry services = buildTestServiceRegistry();

        UiUserStore first = cache.userStore(services);
        UiUserStore second = cache.userStore(services);

        assertSame(first, second, "userStore should be cached within cache lifetime");

        cache.reset();

        UiUserStore third = cache.userStore(services);
        assertNotSame(first, third, "After reset, new userStore instance should be created");
    }

    @Test
    @DisplayName("profileNotes and presence adapters are cached independently")
    void profileNotesAndPresenceAdaptersAreCachedIndependently() {
        UiAdapterCache cache = new UiAdapterCache();
        ServiceRegistry services = buildTestServiceRegistry();

        UiProfileNoteDataAccess profileNotes1 = cache.profileNotes(services);
        UiProfileNoteDataAccess profileNotes2 = cache.profileNotes(services);
        assertSame(profileNotes1, profileNotes2, "profileNotes should be cached");

        UiPresenceDataAccess presence1 = cache.presence(services);
        UiPresenceDataAccess presence2 = cache.presence(services);
        assertSame(presence1, presence2, "presence should be cached");
    }

    @Test
    @DisplayName("reset clears all cached adapters")
    void resetClearsAllCachedAdapters() {
        UiAdapterCache cache = new UiAdapterCache();
        ServiceRegistry services = buildTestServiceRegistry();

        UiUserStore userStore1 = cache.userStore(services);
        UiProfileNoteDataAccess profileNotes1 = cache.profileNotes(services);
        UiPresenceDataAccess presence1 = cache.presence(services);

        cache.reset();

        UiUserStore userStore2 = cache.userStore(services);
        UiProfileNoteDataAccess profileNotes2 = cache.profileNotes(services);
        UiPresenceDataAccess presence2 = cache.presence(services);

        assertNotSame(userStore1, userStore2, "userStore should be reset");
        assertNotSame(profileNotes1, profileNotes2, "profileNotes should be reset");
        assertNotSame(presence1, presence2, "presence should be reset");
    }

    @Test
    @DisplayName("adapter accessors and reset are synchronized")
    void accessorsAndResetAreSynchronized() throws NoSuchMethodException {
        assertTrue(
                Modifier.isSynchronized(UiAdapterCache.class
                        .getDeclaredMethod("userStore", ServiceRegistry.class)
                        .getModifiers()),
                "UiAdapterCache.userStore must be synchronized");
        assertTrue(
                Modifier.isSynchronized(UiAdapterCache.class
                        .getDeclaredMethod("profileNotes", ServiceRegistry.class)
                        .getModifiers()),
                "UiAdapterCache.profileNotes must be synchronized");
        assertTrue(
                Modifier.isSynchronized(UiAdapterCache.class
                        .getDeclaredMethod("presence", ServiceRegistry.class)
                        .getModifiers()),
                "UiAdapterCache.presence must be synchronized");
        assertTrue(
                Modifier.isSynchronized(
                        UiAdapterCache.class.getDeclaredMethod("reset").getModifiers()),
                "UiAdapterCache.reset must be synchronized");
    }

    private static ServiceRegistry buildTestServiceRegistry() {
        return TestServiceRegistryBuilder.build();
    }
}
