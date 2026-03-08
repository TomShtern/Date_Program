package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.ProfileNotesQuery;
import datingapp.core.AppSession;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the notes browser screen. */
public final class NotesViewModel extends BaseViewModel {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final ProfileUseCases profileUseCases;
    private final UiUserStore userStore;
    private final AppSession session;
    private final ObservableList<NoteEntry> notes = FXCollections.observableArrayList();
    private ViewModelErrorSink errorHandler;

    public record NoteEntry(UUID userId, String userName, String content, String lastModified) {}

    public NotesViewModel(ProfileUseCases profileUseCases, UiUserStore userStore, AppSession session) {
        this(profileUseCases, userStore, session, new JavaFxUiThreadDispatcher());
    }

    public NotesViewModel(
            ProfileUseCases profileUseCases,
            UiUserStore userStore,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("notes", uiDispatcher);
        this.profileUseCases = Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public void initialize() {
        refresh();
    }

    public void setErrorHandler(ViewModelErrorSink errorHandler) {
        this.errorHandler = errorHandler;
    }

    public ObservableList<NoteEntry> getNotes() {
        return notes;
    }

    public void refresh() {
        if (isDisposed()) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            notes.clear();
            return;
        }

        asyncScope.runLatest(
                "notes-load",
                "load notes",
                () -> {
                    try {
                        return loadNotes(currentUser.getId());
                    } catch (RuntimeException ex) {
                        reportError(ex.getMessage());
                        return List.of();
                    }
                },
                loadedNotes -> notes.setAll(loadedNotes.toArray(NoteEntry[]::new)));
    }

    @Override
    protected void onDispose() {
        notes.clear();
    }

    private List<NoteEntry> loadNotes(UUID currentUserId) {
        var result = profileUseCases.listProfileNotes(new ProfileNotesQuery(UserContext.ui(currentUserId)));
        if (!result.success()) {
            throw new IllegalStateException(result.error().message());
        }
        List<ProfileNote> profileNotes = result.data();
        Set<UUID> subjectIds =
                profileNotes.stream().map(ProfileNote::subjectId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> usersById = userStore.findByIds(subjectIds);

        return profileNotes.stream()
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .map(note -> new NoteEntry(
                        note.subjectId(),
                        usersById
                                .getOrDefault(note.subjectId(), new User(note.subjectId(), "Unknown User"))
                                .getName(),
                        note.getPreview(),
                        DATE_FORMATTER.format(note.updatedAt())))
                .toList();
    }

    private void reportError(String message) {
        if (errorHandler != null && message != null && !message.isBlank()) {
            asyncScope.dispatchToUi(() -> errorHandler.onError(message));
        }
    }
}
