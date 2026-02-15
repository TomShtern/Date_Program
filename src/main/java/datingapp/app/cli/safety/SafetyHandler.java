package datingapp.app.cli.safety;

import datingapp.app.cli.shared.CliTextAndInput;
import datingapp.app.cli.shared.CliTextAndInput.InputReader;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.safety.TrustSafetyService;
import datingapp.core.storage.UserStorage;
import java.util.HashSet;
import java.util.List;
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
    private final AppSession session;
    private final InputReader inputReader;

    public SafetyHandler(
            UserStorage userStorage,
            TrustSafetyService trustSafetyService,
            AppSession session,
            InputReader inputReader) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.session = Objects.requireNonNull(session);
        this.inputReader = Objects.requireNonNull(inputReader);
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /** Allows the current user to block another user. */
    public void blockUser() {
        CliTextAndInput.requireLogin(() -> {
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
                TrustSafetyService.BlockResult result = trustSafetyService.block(currentUser.getId(), toBlock.getId());
                if (result.success()) {
                    logInfo("🚫 Blocked {}.\n", toBlock.getName());
                } else {
                    logInfo("❌ {}\n", result.errorMessage());
                }
            } else {
                logInfo(CliTextAndInput.CANCELLED);
            }
        });
    }

    /** Allows the current user to report another user for violations. */
    public void reportUser() {
        CliTextAndInput.requireLogin(() -> {
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

            TrustSafetyService.ReportResult result =
                    trustSafetyService.report(currentUser.getId(), toReport.getId(), reason, description);

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
        } catch (NumberFormatException ignored) {
            logInfo("❌ Invalid input.\n");
            return null;
        }
    }

    /**
     * Displays blocked users and allows the current user to unblock them.
     */
    public void manageBlockedUsers() {
        CliTextAndInput.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            List<User> blockedUsers = trustSafetyService.getBlockedUsers(currentUser.getId());

            if (blockedUsers.isEmpty()) {
                logInfo("\nYou haven't blocked anyone.\n");
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
        if (description == null || description.isBlank()) {
            return null;
        }
        return description;
    }

    private void handleReportResult(TrustSafetyService.ReportResult result, User reportedUser) {
        if (result.success()) {
            logInfo("\n✅ Report submitted. {} has been blocked.", reportedUser.getName());
            if (result.userWasBanned()) {
                logInfo("⚠️  This user has been automatically BANNED due to multiple reports.");
            }
            logInfo("");
        } else {
            logInfo("\n❌ {}\n", result.errorMessage());
        }
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
        } catch (NumberFormatException ignored) {
            logInfo(CliTextAndInput.INVALID_INPUT);
            return null;
        }
    }

    // =========================================================================
    // Profile Verification (merged from ProfileVerificationHandler)
    // =========================================================================

    /** Starts the profile verification flow for the current user. */
    public void verifyProfile() {
        CliTextAndInput.requireLogin(() -> {
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
