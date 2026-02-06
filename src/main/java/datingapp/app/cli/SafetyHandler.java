package datingapp.app.cli;

import datingapp.core.AppSession;
import datingapp.core.Match;
import datingapp.core.TrustSafetyService;
import datingapp.core.User;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Report;
import datingapp.core.UserState;
import datingapp.core.VerificationMethod;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles user blocking, reporting, and profile verification operations in the
 * CLI.
 */
public class SafetyHandler {
    private static final Logger logger = LoggerFactory.getLogger(SafetyHandler.class);

    private final UserStorage userStorage;
    private final BlockStorage blockStorage;
    private final MatchStorage matchStorage;
    private final TrustSafetyService trustSafetyService;
    private final AppSession session;
    private final InputReader inputReader;

    public SafetyHandler(
            UserStorage userStorage,
            BlockStorage blockStorage,
            MatchStorage matchStorage,
            TrustSafetyService trustSafetyService,
            AppSession session,
            InputReader inputReader) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.session = Objects.requireNonNull(session);
        this.inputReader = Objects.requireNonNull(inputReader);
    }

    /** Allows the current user to block another user. */
    public void blockUser() {
        CliUtilities.requireLogin(() -> {
            logInfo(CliConstants.HEADER_BLOCK_USER);

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

            String confirm =
                    inputReader.readLine(CliConstants.BLOCK_PREFIX + toBlock.getName() + CliConstants.CONFIRM_SUFFIX);
            if ("y".equalsIgnoreCase(confirm)) {
                Block block = Block.create(currentUser.getId(), toBlock.getId());
                blockStorage.save(block);

                // If matched, end the match
                String matchId = Match.generateId(currentUser.getId(), toBlock.getId());
                matchStorage.get(matchId).ifPresent(match -> {
                    if (match.isActive()) {
                        match.block(currentUser.getId());
                        matchStorage.update(match);
                    }
                });

                logInfo("🚫 Blocked {}.\n", toBlock.getName());
            } else {
                logInfo(CliConstants.CANCELLED);
            }
        });
    }

    /** Allows the current user to report another user for violations. */
    public void reportUser() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            if (currentUser.getState() != UserState.ACTIVE) {
                logInfo("\n⚠️  You must be ACTIVE to report users.\n");
                return;
            }

            logInfo(CliConstants.HEADER_REPORT_USER);

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
            logInfo("❌ Invalid input.\n");
            return null;
        }
    }

    /**
     * Displays blocked users and allows the current user to unblock them.
     */
    public void manageBlockedUsers() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            List<User> blockedUsers = trustSafetyService.getBlockedUsers(currentUser.getId());

            if (blockedUsers.isEmpty()) {
                logInfo("\nYou haven't blocked anyone.\n");
                return;
            }

            logInfo(CliConstants.HEADER_BLOCKED_USERS);
            User toUnblock = selectUserFromList(blockedUsers, "\nEnter number to unblock (or 0 to go back): ", true);
            if (toUnblock == null) {
                return;
            }

            String confirm = inputReader.readLine("Unblock " + toUnblock.getName() + CliConstants.CONFIRM_SUFFIX);
            if ("y".equalsIgnoreCase(confirm)) {
                boolean success = trustSafetyService.unblock(currentUser.getId(), toUnblock.getId());

                if (success) {
                    logInfo("✅ Unblocked {}.\n", toUnblock.getName());
                } else {
                    logInfo("❌ Failed to unblock user.\n");
                }
            } else {
                logInfo(CliConstants.CANCELLED);
            }
        });
    }

    private List<User> getBlockableUsers(User currentUser) {
        List<User> allUsers = userStorage.findAll();
        Set<UUID> alreadyBlocked = blockStorage.getBlockedUserIds(currentUser.getId());

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
                    logInfo(CliConstants.INVALID_INPUT);
                }
                return null;
            }
            return users.get(idx);
        } catch (NumberFormatException _) {
            logInfo(CliConstants.INVALID_INPUT);
            return null;
        }
    }

    // =========================================================================
    // Profile Verification (merged from ProfileVerificationHandler)
    // =========================================================================

    /** Starts the profile verification flow for the current user. */
    public void verifyProfile() {
        CliUtilities.requireLogin(() -> {
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
                default -> logInfo(CliConstants.INVALID_SELECTION);
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

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }
}
