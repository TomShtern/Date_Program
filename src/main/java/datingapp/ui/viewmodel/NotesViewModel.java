package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases.DeleteProfileNoteCommand;
import datingapp.app.usecase.profile.ProfileNotesUseCases.ProfileNotesQuery;
import datingapp.app.usecase.profile.ProfileNotesUseCases.UpsertProfileNoteCommand;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppSession;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the notes browser screen. */
public final class NotesViewModel extends BaseViewModel {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final ProfileNotesUseCases profileNotesUseCases;
    private final UiUserStore userStore;
    private final AppSession session;
    private final ObservableList<NoteEntry> notes = FXCollections.observableArrayList();
    private final ObjectProperty<NoteEntry> selectedNote = new SimpleObjectProperty<>();
    private final StringProperty selectedNoteContent = new SimpleStringProperty("");
    private final BooleanProperty selectedNoteBusy = new SimpleBooleanProperty(false);

    public record NoteEntry(UUID userId, String userName, String content, Instant updatedAt, String lastModified) {}

    public NotesViewModel(ProfileNotesUseCases profileNotesUseCases, UiUserStore userStore, AppSession session) {
        this(profileNotesUseCases, userStore, session, new JavaFxUiThreadDispatcher());
    }

    public NotesViewModel(
            ProfileNotesUseCases profileNotesUseCases,
            UiUserStore userStore,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("notes", uiDispatcher);
        this.profileNotesUseCases = Objects.requireNonNull(profileNotesUseCases, "profileNotesUseCases cannot be null");
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public NotesViewModel(ProfileUseCases profileUseCases, UiUserStore userStore, AppSession session) {
        this(profileUseCases, userStore, session, new JavaFxUiThreadDispatcher());
    }

    public NotesViewModel(
            ProfileUseCases profileUseCases,
            UiUserStore userStore,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this(
                Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null")
                        .getProfileNotesUseCases(),
                userStore,
                session,
                uiDispatcher);
    }

    public void initialize() {
        refresh();
    }

    public void setErrorHandler(ViewModelErrorSink errorHandler) {
        setErrorSink(errorHandler);
    }

    public ObservableList<NoteEntry> getNotes() {
        return notes;
    }

    public ObjectProperty<NoteEntry> selectedNoteProperty() {
        return selectedNote;
    }

    public StringProperty selectedNoteContentProperty() {
        return selectedNoteContent;
    }

    public BooleanProperty selectedNoteBusyProperty() {
        return selectedNoteBusy;
    }

    public void selectNote(NoteEntry note) {
        if (isDisposed()) {
            return;
        }
        selectedNote.set(note);
        selectedNoteContent.set(note == null ? "" : note.content());
    }

    public void refresh() {
        if (isDisposed()) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            notes.clear();
            selectNote(null);
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
        selectedNote.set(null);
        selectedNoteContent.set("");
        selectedNoteBusy.set(false);
    }

    public void saveSelectedNote() {
        User currentUser = session.getCurrentUser();
        NoteEntry note = selectedNote.get();
        String content = selectedNoteContent.get();
        if (currentUser == null || note == null || content == null || content.isBlank()) {
            return;
        }

        selectedNoteBusy.set(true);
        UUID authorId = currentUser.getId();
        UUID subjectId = note.userId();
        asyncScope.runFireAndForget("save selected note", () -> {
            try {
                var result = profileNotesUseCases.upsertProfileNote(
                        new UpsertProfileNoteCommand(UserContext.ui(authorId), subjectId, content));
                if (!result.success()) {
                    reportError(result.error().message());
                    asyncScope.dispatchToUi(() -> selectedNoteBusy.set(false));
                    return;
                }

                ProfileNote savedNote = result.data();
                asyncScope.dispatchToUi(() -> {
                    upsertNoteEntry(savedNote);
                    if (isSelectedNote(subjectId)) {
                        selectNote(createNoteEntry(savedNote, resolveUserName(subjectId)));
                    }
                    selectedNoteBusy.set(false);
                });
            } catch (RuntimeException ex) {
                reportError(ex.getMessage());
                asyncScope.dispatchToUi(() -> selectedNoteBusy.set(false));
            }
        });
    }

    public void deleteSelectedNote() {
        User currentUser = session.getCurrentUser();
        NoteEntry note = selectedNote.get();
        if (currentUser == null || note == null) {
            return;
        }

        selectedNoteBusy.set(true);
        UUID authorId = currentUser.getId();
        UUID subjectId = note.userId();
        asyncScope.runFireAndForget("delete selected note", () -> {
            try {
                var result = profileNotesUseCases.deleteProfileNote(
                        new DeleteProfileNoteCommand(UserContext.ui(authorId), subjectId));
                if (!result.success()) {
                    reportError(result.error().message());
                    asyncScope.dispatchToUi(() -> selectedNoteBusy.set(false));
                    return;
                }

                asyncScope.dispatchToUi(() -> {
                    removeNoteEntry(subjectId);
                    if (isSelectedNote(subjectId)) {
                        selectNote(null);
                    }
                    selectedNoteBusy.set(false);
                });
            } catch (RuntimeException ex) {
                reportError(ex.getMessage());
                asyncScope.dispatchToUi(() -> selectedNoteBusy.set(false));
            }
        });
    }

    private List<NoteEntry> loadNotes(UUID currentUserId) {
        var result = profileNotesUseCases.listProfileNotes(new ProfileNotesQuery(UserContext.ui(currentUserId)));
        if (!result.success()) {
            throw new IllegalStateException(result.error().message());
        }
        List<ProfileNote> profileNotes = result.data();
        Set<UUID> subjectIds =
                profileNotes.stream().map(ProfileNote::subjectId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> usersById = userStore.findByIds(subjectIds);

        return profileNotes.stream()
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .map(note -> createNoteEntry(note, resolveUserName(note.subjectId(), usersById)))
                .toList();
    }

    private NoteEntry createNoteEntry(ProfileNote note, String userName) {
        return new NoteEntry(
                note.subjectId(), userName, note.content(), note.updatedAt(), DATE_FORMATTER.format(note.updatedAt()));
    }

    private String resolveUserName(UUID subjectId) {
        return userStore
                .findByIds(Set.of(subjectId))
                .getOrDefault(subjectId, new User(subjectId, "Unknown User"))
                .getName();
    }

    private String resolveUserName(UUID subjectId, Map<UUID, User> usersById) {
        return usersById
                .getOrDefault(subjectId, new User(subjectId, "Unknown User"))
                .getName();
    }

    private void upsertNoteEntry(ProfileNote savedNote) {
        NoteEntry updatedEntry = createNoteEntry(savedNote, resolveUserName(savedNote.subjectId()));
        int index = indexOfNote(savedNote.subjectId());
        if (index >= 0) {
            notes.set(index, updatedEntry);
            sortNotesByUpdatedAtDescending();
            return;
        }
        notes.add(updatedEntry);
        sortNotesByUpdatedAtDescending();
    }

    private void sortNotesByUpdatedAtDescending() {
        notes.sort((left, right) -> {
            Instant leftUpdatedAt = left.updatedAt();
            Instant rightUpdatedAt = right.updatedAt();
            if (leftUpdatedAt == null && rightUpdatedAt == null) {
                return 0;
            }
            if (leftUpdatedAt == null) {
                return 1;
            }
            if (rightUpdatedAt == null) {
                return -1;
            }
            return rightUpdatedAt.compareTo(leftUpdatedAt);
        });
    }

    private void removeNoteEntry(UUID subjectId) {
        int index = indexOfNote(subjectId);
        if (index >= 0) {
            notes.remove(index);
        }
    }

    private int indexOfNote(UUID subjectId) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).userId().equals(subjectId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSelectedNote(UUID subjectId) {
        NoteEntry currentSelection = selectedNote.get();
        return currentSelection != null && currentSelection.userId().equals(subjectId);
    }

    private void reportError(String message) {
        if (message != null && !message.isBlank()) {
            notifyError(message, null);
        }
    }
}
