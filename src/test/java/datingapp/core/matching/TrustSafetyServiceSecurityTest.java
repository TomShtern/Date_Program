package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestStorages;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrustSafetyServiceSecurityTest {

    private AppConfig config;
    private TestStorages.Users userStorage;

    @BeforeEach
    void setUp() {
        config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
    }

    @Test
    void verificationConfigurationIsNotExposedOnTrustSafetyServiceBuilder() {
        assertThrows(
                NoSuchMethodException.class,
                () -> TrustSafetyService.Builder.class.getDeclaredMethod("random", java.security.SecureRandom.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> TrustSafetyService.Builder.class.getDeclaredMethod("verificationTtl", java.time.Duration.class));
    }

    @Test
    void verificationHelpersAreNotExposedOnTrustSafetyService() {
        assertThrows(NoSuchMethodException.class, () -> TrustSafetyService.class.getMethod("generateVerificationCode"));
        assertThrows(
                NoSuchMethodException.class,
                () -> TrustSafetyService.class.getMethod("verifyCode", User.class, String.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> TrustSafetyService.class.getMethod("isExpired", java.time.Instant.class));
    }

    @Test
    void report_duplicateUsesSaveTimeConstraintViolation() {
        DuplicateRejectingTrustSafetyStorage trustSafetyStorage = new DuplicateRejectingTrustSafetyStorage();
        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage, new TestStorages.Interactions(), userStorage, config)
                .build();

        User reporter = createActiveUser("Reporter");
        User reported = createActiveUser("Reported");
        userStorage.save(reporter);
        userStorage.save(reported);

        TrustSafetyService.ReportResult first =
                service.report(reporter.getId(), reported.getId(), Report.Reason.HARASSMENT, "desc", false);
        assertTrue(first.success());

        TrustSafetyService.ReportResult duplicate =
                service.report(reporter.getId(), reported.getId(), Report.Reason.HARASSMENT, "desc", false);
        assertFalse(duplicate.success());
        assertFalse(duplicate.userWasBanned());
        assertEquals("Already reported this user", duplicate.errorMessage());
        assertEquals(1, trustSafetyStorage.countReportsAgainst(reported.getId()));
    }

    @Test
    void block_doesNotPersistBlockWhenMatchUpdateFails() {
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        FailingInteractions interactions = new FailingInteractions();
        TrustSafetyService service = TrustSafetyService.builder(trustSafetyStorage, interactions, userStorage, config)
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);
        interactions.save(Match.create(blocker.getId(), blocked.getId()));

        UUID blockerId = blocker.getId();
        UUID blockedId = blocked.getId();

        assertThrows(RuntimeException.class, () -> service.block(blockerId, blockedId));
        assertFalse(trustSafetyStorage.isBlocked(blockerId, blockedId));
    }

    @Test
    void block_doesNotPersistBlockWhenBlockSaveFails() {
        FailingTrustSafetyStorage trustSafetyStorage = new FailingTrustSafetyStorage();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(communications);
        TrustSafetyService service = TrustSafetyService.builder(trustSafetyStorage, interactions, userStorage, config)
                .communicationStorage(communications)
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);
        interactions.save(Match.create(blocker.getId(), blocked.getId()));
        communications.saveConversation(Conversation.create(blocker.getId(), blocked.getId()));

        UUID blockerId = blocker.getId();
        UUID blockedId = blocked.getId();

        assertThrows(RuntimeException.class, () -> service.block(blockerId, blockedId));
        assertFalse(trustSafetyStorage.isBlocked(blockerId, blockedId));

        Match persistedMatch =
                interactions.get(Match.generateId(blockerId, blockedId)).orElseThrow();
        assertEquals(Match.MatchState.ACTIVE, persistedMatch.getState());

        Conversation conversation =
                communications.getConversationByUsers(blockerId, blockedId).orElseThrow();
        assertTrue(conversation.isVisibleTo(blockerId));
        assertTrue(conversation.isVisibleTo(blockedId));
        assertNull(conversation.getUserAArchivedAt());
        assertNull(conversation.getUserBArchivedAt());
    }

    @Test
    void block_doesNotPersistPartialStateWhenConversationArchiveFails() {
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        FailingArchiveCommunications communications = new FailingArchiveCommunications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(communications);
        TrustSafetyService service = TrustSafetyService.builder(trustSafetyStorage, interactions, userStorage, config)
                .communicationStorage(communications)
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);
        interactions.save(Match.create(blocker.getId(), blocked.getId()));
        communications.saveConversation(Conversation.create(blocker.getId(), blocked.getId()));

        UUID blockerId = blocker.getId();
        UUID blockedId = blocked.getId();

        assertThrows(RuntimeException.class, () -> service.block(blockerId, blockedId));
        assertFalse(trustSafetyStorage.isBlocked(blockerId, blockedId));

        Match persistedMatch =
                interactions.get(Match.generateId(blockerId, blockedId)).orElseThrow();
        assertEquals(Match.MatchState.ACTIVE, persistedMatch.getState());

        Conversation conversation =
                communications.getConversationByUsers(blockerId, blockedId).orElseThrow();
        assertTrue(conversation.isVisibleTo(blockerId));
        assertTrue(conversation.isVisibleTo(blockedId));
        assertNull(conversation.getUserAArchivedAt());
        assertNull(conversation.getUserBArchivedAt());
    }

    @Test
    void block_doesNotPersistPartialStateWhenConversationVisibilityFails() {
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        FailingVisibilityCommunications communications = new FailingVisibilityCommunications();
        TestStorages.Interactions interactions = new TestStorages.Interactions(communications);
        TrustSafetyService service = TrustSafetyService.builder(trustSafetyStorage, interactions, userStorage, config)
                .communicationStorage(communications)
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);
        interactions.save(Match.create(blocker.getId(), blocked.getId()));
        communications.saveConversation(Conversation.create(blocker.getId(), blocked.getId()));

        UUID blockerId = blocker.getId();
        UUID blockedId = blocked.getId();

        assertThrows(RuntimeException.class, () -> service.block(blockerId, blockedId));
        assertFalse(trustSafetyStorage.isBlocked(blockerId, blockedId));

        Match persistedMatch =
                interactions.get(Match.generateId(blockerId, blockedId)).orElseThrow();
        assertEquals(Match.MatchState.ACTIVE, persistedMatch.getState());

        Conversation conversation =
                communications.getConversationByUsers(blockerId, blockedId).orElseThrow();
        assertTrue(conversation.isVisibleTo(blockerId));
        assertTrue(conversation.isVisibleTo(blockedId));
        assertNull(conversation.getUserAArchivedAt());
        assertNull(conversation.getUserBArchivedAt());
    }

    private static User createActiveUser(String name) {
        return User.StorageBuilder.create(UUID.randomUUID(), name, AppClock.now())
                .bio("Test user")
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .interestedIn(Set.of(Gender.FEMALE))
                .photoUrls(List.of("https://example.com/photo.jpg"))
                .state(UserState.ACTIVE)
                .build();
    }

    private static final class DuplicateRejectingTrustSafetyStorage extends TestStorages.TrustSafety {
        private final Set<String> reportKeys = new HashSet<>();

        @Override
        public void save(Report report) {
            String key = report.reporterId() + "->" + report.reportedUserId();
            if (!reportKeys.add(key)) {
                throw new RuntimeException("UNIQUE constraint violation on reports");
            }
            super.save(report);
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return false;
        }
    }

    private static final class FailingInteractions extends TestStorages.Interactions {
        @Override
        public void update(Match match) {
            super.update(match);
            throw new RuntimeException("match update failed");
        }
    }

    private static final class FailingTrustSafetyStorage extends TestStorages.TrustSafety {
        @Override
        public void save(datingapp.core.connection.ConnectionModels.Block block) {
            throw new RuntimeException("block save failed");
        }
    }

    private static final class FailingArchiveCommunications extends TestStorages.Communications {
        @Override
        public void archiveConversation(
                String conversationId, UUID userId, datingapp.core.model.Match.MatchArchiveReason reason) {
            throw new RuntimeException("conversation archive failed");
        }
    }

    private static final class FailingVisibilityCommunications extends TestStorages.Communications {
        @Override
        public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
            throw new RuntimeException("conversation visibility update failed");
        }
    }
}
