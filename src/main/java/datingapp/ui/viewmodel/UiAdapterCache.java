package datingapp.ui.viewmodel;

import datingapp.core.ServiceRegistry;
import datingapp.ui.viewmodel.UiDataAdapters.MetricsUiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;
import java.util.Objects;

/**
 * Focused package-private helper that owns the cached UI adapter singletons.
 *
 * <p>
 * This cache manages the lifecycle of three shared adapters used by multiple
 * ViewModels:
 * <ul>
 *   <li>{@link StorageUiUserStore} - for user data access</li>
 *   <li>{@link UseCaseUiProfileNoteDataAccess} - for private profile-note access</li>
 *   <li>{@link MetricsUiPresenceDataAccess} - for chat presence/activity state</li>
 * </ul>
 *
 * <p>
 * Reduces clutter in {@link ViewModelFactory} by extracting adapter caching as a
 * separate concern while keeping {@link ViewModelFactory} as the production UI
 * composition root.
 */
final class UiAdapterCache {

    private static final String SERVICES_CANNOT_BE_NULL = "services cannot be null";

    private UiUserStore userStore;
    private UiProfileNoteDataAccess profileNotes;
    private UiPresenceDataAccess presence;
    private UiSocialDataAccess socialDataAccess;

    /**
     * Returns the cached user-store adapter, creating it if needed.
     *
     * @param services the service registry providing UserStorage
     * @return the cached adapter instance
     * @throws NullPointerException if services is null
     */
    synchronized UiUserStore userStore(ServiceRegistry services) {
        Objects.requireNonNull(services, SERVICES_CANNOT_BE_NULL);
        if (userStore == null) {
            userStore = new StorageUiUserStore(services.getUserStorage());
        }
        return userStore;
    }

    /**
     * Returns the cached profile-note adapter, creating it if needed.
     *
     * @param services the service registry providing ProfileNotesUseCases
     * @return the cached adapter instance
     * @throws NullPointerException if services is null
     */
    synchronized UiProfileNoteDataAccess profileNotes(ServiceRegistry services) {
        Objects.requireNonNull(services, SERVICES_CANNOT_BE_NULL);
        if (profileNotes == null) {
            profileNotes = new UseCaseUiProfileNoteDataAccess(services.getProfileNotesUseCases());
        }
        return profileNotes;
    }

    /**
     * Returns the cached presence adapter, creating it if needed.
     *
     * @param services the service registry providing ActivityMetricsService and AppConfig
     * @return the cached adapter instance
     * @throws NullPointerException if services is null
     */
    synchronized UiPresenceDataAccess presence(ServiceRegistry services) {
        Objects.requireNonNull(services, SERVICES_CANNOT_BE_NULL);
        if (presence == null) {
            presence = new MetricsUiPresenceDataAccess(services.getActivityMetricsService(), services.getConfig());
        }
        return presence;
    }

    /**
     * Returns the cached social data-access adapter, creating it if needed.
     *
     * @param services the service registry providing CommunicationStorage
     * @return the cached adapter instance
     * @throws NullPointerException if services is null
     */
    synchronized UiSocialDataAccess socialDataAccess(ServiceRegistry services) {
        Objects.requireNonNull(services, SERVICES_CANNOT_BE_NULL);
        if (socialDataAccess == null) {
            socialDataAccess = new StorageUiSocialDataAccess(services.getCommunicationStorage());
        }
        return socialDataAccess;
    }

    /**
     * Clears all cached adapter instances.
     *
     * <p>
     * Called during reset/dispose cycles to free references and ensure fresh
     * adapters are created on the next access.
     */
    synchronized void reset() {
        userStore = null;
        profileNotes = null;
        presence = null;
        socialDataAccess = null;
    }
}
