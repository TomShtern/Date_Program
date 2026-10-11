package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that moderation audit events are also persisted to the configured {@link ModerationAuditStorage}
 * (the durable path used by SQL runtimes), in addition to the SLF4J stream asserted by
 * {@link TrustSafetyServiceAuditTest}. Also verifies that a storage failure is best-effort and never breaks
 * the user-facing moderation action.
 */
@DisplayName("ModerationAudit durable persistence")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ModerationAuditPersistenceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-24T12:00:00Z");

    private final CapturingAuditStorage auditStorage = new CapturingAuditStorage();

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(FIXED_INSTANT);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Test
    @DisplayName("block persists a durable audit event alongside the SLF4J stream")
    void blockPersistsDurableAuditEvent() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();

        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage,
                        interactionStorage,
                        userStorage,
                        AppConfig.builder().autoBanThreshold(3).build())
                .moderationAuditStorage(auditStorage)
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);

        TrustSafetyService.BlockResult result = service.block(blocker.getId(), blocked.getId());
        assertTrue(result.success(), "block should succeed");
        assertTrue(trustSafetyStorage.isBlocked(blocker.getId(), blocked.getId()), "block should be persisted");

        List<ModerationAuditEvent> events = auditStorage.events();
        assertTrue(
                events.stream().anyMatch(event -> event.action() == ModerationAuditEvent.Action.BLOCK),
                "block should emit a durable BLOCK audit event");
        assertTrue(
                events.stream().anyMatch(event -> event.outcome() == ModerationAuditEvent.Outcome.SUCCESS),
                "successful block should record a SUCCESS outcome");
    }

    @Test
    @DisplayName("report persists a durable audit event")
    void reportPersistsDurableAuditEvent() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();

        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage,
                        interactionStorage,
                        userStorage,
                        AppConfig.builder().autoBanThreshold(3).build())
                .moderationAuditStorage(auditStorage)
                .build();

        User reporter = createActiveUser("Reporter");
        User reported = createActiveUser("Reported");
        userStorage.save(reporter);
        userStorage.save(reported);

        TrustSafetyService.ReportResult result =
                service.report(reporter.getId(), reported.getId(), Report.Reason.SPAM, "spam content", false);

        assertTrue(result.success(), "report should succeed");
        assertTrue(
                auditStorage.events().stream().anyMatch(event -> event.action() == ModerationAuditEvent.Action.REPORT),
                "report should emit a durable REPORT audit event");
    }

    @Test
    @DisplayName("storage failure is best-effort and does not break the moderation action")
    void storageFailureDoesNotBreakAction() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();

        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage,
                        interactionStorage,
                        userStorage,
                        AppConfig.builder().autoBanThreshold(3).build())
                .moderationAuditStorage(FailingAuditStorage.INSTANCE)
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);

        TrustSafetyService.BlockResult result = service.block(blocker.getId(), blocked.getId());
        assertTrue(result.success(), "block must succeed even if the durable audit store fails");
        assertTrue(
                trustSafetyStorage.isBlocked(blocker.getId(), blocked.getId()),
                "block must still be persisted despite audit failure");
    }

    @Test
    @DisplayName("noOp storage persists nothing and never throws")
    void noOpStorageDiscards() {
        ModerationAuditStorage storage = ModerationAuditStorage.noOp();
        ModerationAuditEvent event = new ModerationAuditEvent(
                FIXED_INSTANT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ModerationAuditEvent.Action.REPORT,
                ModerationAuditEvent.Outcome.SUCCESS,
                Map.of("moderation_reason_code", "SPAM"));
        storage.append(event);
        assertEquals(List.of(), storage.findRecentForTarget(event.targetId(), 10));
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test user");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setAgeRange(
                18,
                60,
                AppConfig.defaults().validation().minAge(),
                AppConfig.defaults().validation().maxAge());
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    /** In-memory capturing implementation of {@link ModerationAuditStorage} for persistence assertions. */
    private static final class CapturingAuditStorage implements ModerationAuditStorage {
        private final List<ModerationAuditEvent> events = new ArrayList<>();

        @Override
        public void append(ModerationAuditEvent event) {
            events.add(event);
        }

        @Override
        public List<ModerationAuditEvent> findRecentForTarget(UUID targetId, int limit) {
            return events.stream()
                    .filter(event -> targetId.equals(event.targetId()))
                    .limit(limit)
                    .toList();
        }

        List<ModerationAuditEvent> events() {
            return List.copyOf(events);
        }
    }

    /** Storage that always throws, to verify the logger's best-effort behavior. */
    private static final class FailingAuditStorage implements ModerationAuditStorage {
        static final FailingAuditStorage INSTANCE = new FailingAuditStorage();

        @Override
        public void append(ModerationAuditEvent event) {
            throw new IllegalStateException("simulated durable audit failure");
        }
    }
}
