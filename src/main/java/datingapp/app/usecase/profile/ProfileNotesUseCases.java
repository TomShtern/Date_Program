package datingapp.app.usecase.profile;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.ProfileNote;
import datingapp.core.profile.SanitizerUtils;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Profile note application use-cases shared across adapters. */
public class ProfileNotesUseCases {

    private static final Logger logger = LoggerFactory.getLogger(ProfileNotesUseCases.class);
    private static final String USER_STORAGE_REQUIRED = "UserStorage is required";
    private static final String AUTHOR_NOT_FOUND = "Author not found";
    private static final String CONTEXT_AND_SUBJECT_REQUIRED = "Context and subjectId are required";
    private static final String PROFILE_NOTE_TOO_LONG = "Note content exceeds maximum length of %d characters";

    private final UserStorage userStorage;
    private final ValidationService validationService;
    private final AppConfig config;
    private final AppEventBus eventBus;

    public ProfileNotesUseCases(
            UserStorage userStorage, ValidationService validationService, AppConfig config, AppEventBus eventBus) {
        this.userStorage = userStorage;
        this.validationService = validationService;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    public UseCaseResult<List<ProfileNote>> listProfileNotes(ProfileNotesQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context is required"));
        }
        Optional<UseCaseError> authorError = validateAuthor(query.context().userId());
        if (authorError.isPresent()) {
            return UseCaseResult.failure(authorError.get());
        }
        try {
            return UseCaseResult.success(List.copyOf(
                    userStorage.getProfileNotesByAuthor(query.context().userId())));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load profile notes: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileNote> getProfileNote(ProfileNoteQuery query) {
        if (query == null || query.context() == null || query.subjectId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_SUBJECT_REQUIRED));
        }
        Optional<UseCaseError> authorError = validateAuthor(query.context().userId());
        if (authorError.isPresent()) {
            return UseCaseResult.failure(authorError.get());
        }
        try {
            return userStorage
                    .getProfileNote(query.context().userId(), query.subjectId())
                    .map(UseCaseResult::success)
                    .orElseGet(() -> UseCaseResult.failure(UseCaseError.notFound("Profile note not found")));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load profile note: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileNote> upsertProfileNote(UpsertProfileNoteCommand command) {
        if (command == null || command.context() == null || command.subjectId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_SUBJECT_REQUIRED));
        }
        Optional<UseCaseError> authorError = validateAuthor(command.context().userId());
        if (authorError.isPresent()) {
            return UseCaseResult.failure(authorError.get());
        }
        if (userStorage.get(command.subjectId()).isEmpty()) {
            return UseCaseResult.failure(UseCaseError.notFound("Subject user not found"));
        }

        try {
            String sanitizedContent = SanitizerUtils.sanitize(command.content());
            Optional<UseCaseError> contentError = validateSanitizedContent(sanitizedContent);
            if (contentError.isPresent()) {
                return UseCaseResult.failure(contentError.get());
            }
            ProfileNote note = userStorage
                    .getProfileNote(command.context().userId(), command.subjectId())
                    .map(existing -> existing.withContent(sanitizedContent))
                    .orElseGet(() ->
                            ProfileNote.create(command.context().userId(), command.subjectId(), sanitizedContent));
            userStorage.saveProfileNote(note);
            int safeContentLength = sanitizedContent == null ? 0 : sanitizedContent.length();
            publishEvent(
                    new AppEvent.ProfileNoteSaved(
                            command.context().userId(), command.subjectId(), safeContentLength, AppClock.now()),
                    "Post-profile-note-save event publication failed for author "
                            + command.context().userId());
            return UseCaseResult.success(note);
        } catch (IllegalArgumentException | NullPointerException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Invalid profile note input";
            return UseCaseResult.failure(UseCaseError.validation(msg));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to save profile note: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> deleteProfileNote(DeleteProfileNoteCommand command) {
        if (command == null || command.context() == null || command.subjectId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_SUBJECT_REQUIRED));
        }
        Optional<UseCaseError> authorError = validateAuthor(command.context().userId());
        if (authorError.isPresent()) {
            return UseCaseResult.failure(authorError.get());
        }
        try {
            boolean deleted = userStorage.deleteProfileNote(command.context().userId(), command.subjectId());
            if (!deleted) {
                return UseCaseResult.failure(UseCaseError.notFound("Profile note not found"));
            }
            publishEvent(
                    new AppEvent.ProfileNoteDeleted(command.context().userId(), command.subjectId(), AppClock.now()),
                    "Post-profile-note-delete event publication failed for author "
                            + command.context().userId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to delete profile note: " + e.getMessage()));
        }
    }

    public ValidationService validationService() {
        return validationService;
    }

    private Optional<UseCaseError> validateAuthor(UUID authorId) {
        if (userStorage == null) {
            return Optional.of(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (userStorage.get(authorId).isEmpty()) {
            return Optional.of(UseCaseError.notFound(AUTHOR_NOT_FOUND));
        }
        return Optional.empty();
    }

    private Optional<UseCaseError> validateSanitizedContent(String sanitizedContent) {
        if (validationService != null) {
            var validationResult = validationService.validateProfileNoteContent(sanitizedContent);
            if (!validationResult.valid()) {
                String errorMessage = validationResult.errors().isEmpty()
                        ? "Profile note content is invalid"
                        : validationResult.errors().getFirst();
                return Optional.of(UseCaseError.validation(errorMessage));
            }
            return Optional.empty();
        }

        int maxProfileNoteLength = config.validation().maxProfileNoteLength();
        if (sanitizedContent != null && sanitizedContent.length() > maxProfileNoteLength) {
            return Optional.of(UseCaseError.validation(PROFILE_NOTE_TOO_LONG.formatted(maxProfileNoteLength)));
        }
        return Optional.empty();
    }

    private void publishEvent(AppEvent event, String failureMessage) {
        try {
            eventBus.publish(event);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("{}: {}", failureMessage, e.getMessage(), e);
            }
        }
    }

    public static record ProfileNotesQuery(UserContext context) {}

    public static record ProfileNoteQuery(UserContext context, UUID subjectId) {}

    public static record UpsertProfileNoteCommand(UserContext context, UUID subjectId, String content) {}

    public static record DeleteProfileNoteCommand(UserContext context, UUID subjectId) {}
}
