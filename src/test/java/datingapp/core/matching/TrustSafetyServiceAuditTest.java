package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Block;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.event.KeyValuePair;

@DisplayName("TrustSafetyService audit")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class TrustSafetyServiceAuditTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-24T12:00:00Z");

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(FIXED_INSTANT);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Test
    @DisplayName("report and block emit structured audit events without logging report descriptions")
    void reportAndBlockEmitStructuredAuditEventsWithoutLoggingDescriptions() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();

        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage,
                        interactionStorage,
                        userStorage,
                        AppConfig.builder().autoBanThreshold(3).build())
                .build();

        User reporter = createActiveUser("Reporter");
        User reported = createActiveUser("Reported");
        userStorage.save(reporter);
        userStorage.save(reported);

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger("audit.moderation");
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String description = "DO NOT LOG THIS DESCRIPTION";
        try {
            TrustSafetyService.ReportResult result =
                    service.report(reporter.getId(), reported.getId(), Report.Reason.SPAM, description, true);

            assertTrue(result.success());
            assertFalse(result.userWasBanned());

            List<String> actions = appender.list.stream()
                    .map(event -> event.getKeyValuePairs().stream()
                            .filter(pair -> pair.key.equals("action"))
                            .findFirst()
                            .orElseThrow()
                            .value
                            .toString())
                    .toList();

            assertEquals(
                    List.of(
                            ModerationAuditEvent.Action.REPORT.name(),
                            ModerationAuditEvent.Action.AUTO_BAN.name(),
                            ModerationAuditEvent.Action.BLOCK.name()),
                    actions);

            List<KeyValuePair> reportPairs = appender.list.get(0).getKeyValuePairs();
            assertTrue(reportPairs.stream()
                    .anyMatch(pair ->
                            pair.key.equals("context") && pair.value.toString().contains("SPAM")));

            assertFalse(
                    appender.list.stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .anyMatch(message -> message.contains(description)),
                    "Report descriptions must not be logged");
            assertFalse(
                    appender.list.stream()
                            .flatMap(event -> event.getKeyValuePairs().stream())
                            .map(String::valueOf)
                            .anyMatch(text -> text.contains(description)),
                    "Report descriptions must not be logged in structured fields");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("unblock emits an audit event")
    void unblockEmitsAuditEvent() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();

        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, AppConfig.defaults())
                .build();

        User blocker = createActiveUser("Blocker");
        User blocked = createActiveUser("Blocked");
        userStorage.save(blocker);
        userStorage.save(blocked);
        trustSafetyStorage.save(Block.create(blocker.getId(), blocked.getId()));

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger("audit.moderation");
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            boolean deleted = service.unblock(blocker.getId(), blocked.getId());

            assertTrue(deleted);
            assertEquals(1, appender.list.size());
            assertTrue(appender.list.get(0).getKeyValuePairs().stream()
                    .anyMatch(pair ->
                            pair.key.equals("action") && pair.value.equals(ModerationAuditEvent.Action.UNBLOCK)));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("auto-ban emits an audit event")
    void autoBanEmitsAuditEvent() {
        TestStorages.Users userStorage = new TestStorages.Users();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Interactions interactionStorage = new TestStorages.Interactions();

        TrustSafetyService service = TrustSafetyService.builder(
                        trustSafetyStorage,
                        interactionStorage,
                        userStorage,
                        AppConfig.builder().autoBanThreshold(1).build())
                .build();

        User reporter = createActiveUser("Reporter");
        User reported = createActiveUser("Reported");
        userStorage.save(reporter);
        userStorage.save(reported);

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger("audit.moderation");
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            TrustSafetyService.ReportResult result =
                    service.report(reporter.getId(), reported.getId(), Report.Reason.HARASSMENT, null, false);

            assertTrue(result.success());
            assertTrue(result.userWasBanned());
            assertTrue(appender.list.stream().anyMatch(event -> event.getKeyValuePairs().stream()
                    .anyMatch(pair ->
                            pair.key.equals("action") && pair.value.equals(ModerationAuditEvent.Action.AUTO_BAN))));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
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
        user.addPhotoUrl("photo.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
