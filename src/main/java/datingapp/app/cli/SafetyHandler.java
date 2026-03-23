package datingapp.app.cli;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.storage.UserStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles user blocking, reporting, and profile verification operations in the
 * CLI.
 */
public class SafetyHandler implements LoggingSupport {
    private static final Logger logger = LoggerFactory.getLogger(SafetyHandler.class);

    private final UserStorage userStorage;
    private final TrustSafetyService trustSafetyService;
    private final SocialUseCases socialUseCases;
    private final AppSession session;
    private final InputReader inputReader;

    public SafetyHandler(
            UserStorage userStorage,
            TrustSafetyService trustSafetyService,
            SocialUseCases socialUseCases,
            AppSession session,
            InputReader inputReader) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.socialUseCases = Objects.requireNonNull(socialUseCases);
        this.session = Objects.requireNonNull(session);
        this.inputReader = Objects.requireNonNull(inputReader);
    }

    public static SafetyHandler fromServices(ServiceRegistry services, AppSession session, InputReader inputReader) {
        Objects.requireNonNull(services, "services cannot be null");
        return new SafetyHandler(
                services.getUserStorage(),
                services.getTrustSafetyService(),
                services.getSocialUseCases(),
                session,
                inputReader);
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /** Allows the current user to block another user. */
    public void blockUser() {
        CliTextAndInput.requireLogin(session, () -> {
            logInfo(CliTextAndInput.HEADER_BLOCK_USER);

            User currentUser = session.getCurrentUser();
            List<User> blockableUsers = getBlockableUsers(currentUser);

            if (blockableUsers.isEmpty()) {
                logInfo("No users to block.\n");
                return;
            }
            User toBlock = selectUserFromList(blockableUsers, "\nSelect user to block (or 0 to cancel): ", false);
            if (toBlock == null) {
                return;
            }

            String confirm = inputReader.readLine(
                    CliTextAndInput.BLOCK_PREFIX + toBlock.getName() + CliTextAndInput.CONFIRM_SUFFIX);
            if ("y".equalsIgnoreCase(confirm)) {
                var result = socialUseCases.blockUser(
                        new RelationshipCommand(UserContext.cli(currentUser.getId()), toBlock.getId()));
                if (result.success()) {
                    logInfo("🚫 Blocked {}.\n", toBlock.getName());
                } else {
                    logInfo("❌ {}\n", result.error().message());
                }
            } else {
                logInfo(CliTextAndInput.CANCELLED);
            }
        });
    }

    /** Allows the current user to report another user for violations. */
    public void reportUser() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            if (currentUser.getState() != UserState.ACTIVE) {
                logInfo("\n⚠️  You must be ACTIVE to report users.\n");
                return;
            }

            logInfo(CliTextAndInput.HEADER_REPORT_USER);

            List<User> reportableUsers = getReportableUsers(currentUser);

            if (reportableUsers.isEmpty()) {
                logInfo("No users to report.\n");
                return;
            }

            User toReport = selectUserFromList(reportableUsers, "\nSelect user to report (or 0 to cancel): ", true);
            if (toReport == null) {
                return;
            }

            Report.Reason reason = selectReportReason();
            if (reason == null) {
                return;
            }

            String description =
                    normalizeReportDescription(inputReader.readLine("Additional details (optional, max 500 chars): "));
            String blockResponse = inputReader
                    .readLine("Do you also want to block them? (y/n)> ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            boolean blockUser = "y".equals(blockResponse) || "yes".equals(blockResponse);

            var result = socialUseCases.reportUser(new ReportCommand(
                    UserContext.cli(currentUser.getId()), toReport.getId(), reason, description, blockUser));

            handleReportResult(result, toReport);
        });
    }

    /**
     * Prompts the user to select a reason for reporting.
     *
     * @return The selected report reason, or null if invalid
     */
    @Nullable
    private Report.Reason selectReportReason() {
        logInfo("\nReason for report:");
        Report.Reason[] reasons = Report.Reason.values();
        for (int i = 0; i < reasons.length; i++) {
            logInfo("  {}. {}", i + 1, reasons[i]);
        }

        String reasonInput = inputReader.readLine("Select reason: ");
        try {
            int reasonIdx = Integer.parseInt(reasonInput) - 1;
            if (reasonIdx < 0 || reasonIdx >= reasons.length) {
                logInfo("❌ Invalid reason.\n");
                return null;
            }
            return reasons[reasonIdx];
        } catch (NumberFormatException _) {
            String lengthMeta = reasonInput == null ? "empty" : String.valueOf(reasonInput.length());
            logger.debug("Invalid report reason input; length={}", lengthMeta);
            logInfo("❌ Invalid input.\n");
            return null;
        }
    }

    /**
     * Displays blocked users and allows the current user to unblock them.
     */
    public void manageBlockedUsers() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            List<User> blockedUsers = trustSafetyService.getBlockedUsers(currentUser.getId());

            if (blockedUsers.isEmpty()) {
                logInfo("No blocked users.\n");
                return;
            }

            logInfo(CliTextAndInput.HEADER_BLOCKED_USERS);
            User toUnblock = selectUserFromList(blockedUsers, "\nEnter number to unblock (or 0 to go back): ", true);
            if (toUnblock == null) {
                return;
            }

            String confirm = inputReader.readLine("Unblock " + toUnblock.getName() + CliTextAndInput.CONFIRM_SUFFIX);
            if ("y".equalsIgnoreCase(confirm)) {
                boolean success = trustSafetyService.unblock(currentUser.getId(), toUnblock.getId());

                if (success) {
                    logInfo("✅ Unblocked {}.\n", toUnblock.getName());
                } else {
                    logInfo("❌ Failed to unblock user.\n");
                }
            } else {
                logInfo(CliTextAndInput.CANCELLED);
            }
        });
    }

    private List<User> getBlockableUsers(User currentUser) {
        List<User> allUsers = userStorage.findAll();
        Set<UUID> alreadyBlocked = trustSafetyService.getBlockedUsers(currentUser.getId()).stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        return allUsers.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !alreadyBlocked.contains(u.getId()))
                .toList();
    }

    private List<User> getReportableUsers(User currentUser) {
        return userStorage.findAll().stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .toList();
    }

    @Nullable
    private String normalizeReportDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private void handleReportResult(
            datingapp.app.usecase.common.UseCaseResult<TrustSafetyService.ReportResult> result, User reportedUser) {
        if (!result.success()) {
            logInfo("\n❌ {}\n", result.error().message());
            return;
        }

        TrustSafetyService.ReportResult reportResult = result.data();
        logInfo("\n✅ Report submitted. {} has been blocked.", reportedUser.getName());
        if (reportResult.userWasBanned()) {
            logInfo("⚠️  This user has been automatically BANNED due to multiple reports.");
        }
        logInfo("");
    }

    @Nullable
    private User selectUserFromList(List<User> users, String prompt, boolean showState) {
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (showState) {
                logInfo("  {}. {} ({})", i + 1, user.getName(), user.getState());
            } else {
                logInfo("  {}. {}", i + 1, user.getName());
            }
        }

        String input = inputReader.readLine(prompt);
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= users.size()) {
                if (idx != -1) {
                    logInfo(CliTextAndInput.INVALID_INPUT);
                }
                return null;
            }
            return users.get(idx);
        } catch (NumberFormatException _) {
            logger.warn("Invalid safety handler selection input");
            String lengthMeta = input == null ? "empty" : String.valueOf(input.length());
            logger.debug("Invalid safety input length={}", lengthMeta);
            logInfo(CliTextAndInput.INVALID_INPUT);
            return null;
        }
    }

    // =========================================================================
    // Profile Verification (merged from ProfileVerificationHandler)
    // =========================================================================

    /** Starts the profile verification flow for the current user. */
    public void verifyProfile() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();

            if (currentUser.isVerified()) {
                logInfo("\n✅ Profile already verified ({}).\n", currentUser.getVerifiedAt());
                return;
            }

            logInfo("\n--- Profile Verification ---\n");
            logInfo("1. Verify by email");
            logInfo("2. Verify by phone");
            logInfo("0. Back\n");

            String choice = inputReader.readLine("Choice: ");
            switch (choice) {
                case "0" -> {
                    /* cancelled */ }
                case "1" -> startVerification(currentUser, VerificationMethod.EMAIL);
                case "2" -> startVerification(currentUser, VerificationMethod.PHONE);
                default -> logInfo(CliTextAndInput.INVALID_SELECTION);
            }
        });
    }

    /**
     * Starts the verification process for a user using the specified method.
     *
     * @param user   The user to verify
     * @param method The verification method (email or phone)
     */
    private void startVerification(User user, VerificationMethod method) {
        if (method == VerificationMethod.EMAIL) {
            String email = inputReader.readLine("Email: ");
            if (email == null || email.isBlank()) {
                logInfo("❌ Email required.\n");
                return;
            }
            user.setEmail(email.trim());
        } else {
            String phone = inputReader.readLine("Phone: ");
            if (phone == null || phone.isBlank()) {
                logInfo("❌ Phone required.\n");
                return;
            }
            user.setPhone(phone.trim());
        }

        String generatedCode = trustSafetyService.generateVerificationCode();
        user.startVerification(method, generatedCode);
        userStorage.save(user);

        logInfo("\n[SIMULATED DELIVERY] Your verification code is: {}\n", generatedCode);

        String inputCode = inputReader.readLine("Enter the code: ");
        if (!trustSafetyService.verifyCode(user, inputCode)) {
            if (trustSafetyService.isExpired(user.getVerificationSentAt())) {
                logInfo("❌ Code expired. Please try again.\n");
            } else {
                logInfo("❌ Incorrect code.\n");
            }
            return;
        }

        user.markVerified();
        userStorage.save(user);
        logInfo("✅ Profile verified!\n");
    }
}
