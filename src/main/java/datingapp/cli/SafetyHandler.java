package datingapp.cli;

import datingapp.core.BlockStorage;
import datingapp.core.Match;
import datingapp.core.MatchStorage;
import datingapp.core.TrustSafetyService;
import datingapp.core.User;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Report;
import datingapp.core.UserStorage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles user blocking and reporting operations in the CLI. */
public class SafetyHandler {
    private static final Logger logger = LoggerFactory.getLogger(SafetyHandler.class);

    private final UserStorage userStorage;
    private final BlockStorage blockStorage;
    private final MatchStorage matchStorage;
    private final TrustSafetyService trustSafetyService;
    private final UserSession userSession;
    private final InputReader inputReader;

    public SafetyHandler(
            UserStorage userStorage,
            BlockStorage blockStorage,
            MatchStorage matchStorage,
            TrustSafetyService trustSafetyService,
            UserSession userSession,
            InputReader inputReader) {
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
        this.matchStorage = matchStorage;
        this.trustSafetyService = trustSafetyService;
        this.userSession = userSession;
        this.inputReader = inputReader;
    }

    /** Allows the current user to block another user. */
    public void blockUser() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        logger.info(CliConstants.HEADER_BLOCK_USER);

        User currentUser = userSession.getCurrentUser();
        List<User> allUsers = userStorage.findAll();
        Set<UUID> alreadyBlocked = blockStorage.getBlockedUserIds(currentUser.getId());

        List<User> blockableUsers = allUsers.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !alreadyBlocked.contains(u.getId()))
                .toList();

        if (blockableUsers.isEmpty()) {
            logger.info("No users to block.\n");
            return;
        }

        for (int i = 0; i < blockableUsers.size(); i++) {
            User u = blockableUsers.get(i);
            logger.info("  {}. {}", i + 1, u.getName());
        }

        String input = inputReader.readLine("\nSelect user to block (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= blockableUsers.size()) {
                if (idx != -1) {
                    logger.info(CliConstants.INVALID_INPUT);
                }
                return;
            }

            User toBlock = blockableUsers.get(idx);
            String confirm =
                    inputReader.readLine(CliConstants.BLOCK_PREFIX + toBlock.getName() + CliConstants.CONFIRM_SUFFIX);
            if (confirm.equalsIgnoreCase("y")) {
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

                logger.info("🚫 Blocked {}.\n", toBlock.getName());
            } else {
                logger.info(CliConstants.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logger.info(CliConstants.INVALID_INPUT);
        }
    }

    /** Allows the current user to report another user for violations. */
    public void reportUser() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
        if (currentUser.getState() != User.State.ACTIVE) {
            logger.info("\n⚠️  You must be ACTIVE to report users.\n");
            return;
        }

        logger.info(CliConstants.HEADER_REPORT_USER);

        List<User> allUsers = userStorage.findAll();
        List<User> reportableUsers = allUsers.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .toList();

        if (reportableUsers.isEmpty()) {
            logger.info("No users to report.\n");
            return;
        }

        User toReport = selectReportCandidate(reportableUsers);
        if (toReport == null) {
            return;
        }

        Report.Reason reason = selectReportReason();
        if (reason == null) {
            return;
        }

        String description = inputReader.readLine("Additional details (optional, max 500 chars): ");
        if (description.isBlank()) {
            description = null;
        }

        TrustSafetyService.ReportResult result =
                trustSafetyService.report(currentUser.getId(), toReport.getId(), reason, description);

        if (result.success()) {
            logger.info("\n✅ Report submitted. {} has been blocked.", toReport.getName());
            if (result.userWasBanned()) {
                logger.info("⚠️  This user has been automatically BANNED due to multiple reports.");
            }
            logger.info("");
        } else {
            logger.info("\n❌ {}\n", result.errorMessage());
        }
    }

    /** Displays reportable users and prompts for selection. */
    private User selectReportCandidate(List<User> reportableUsers) {
        for (int i = 0; i < reportableUsers.size(); i++) {
            User u = reportableUsers.get(i);
            logger.info("  {}. {} ({})", i + 1, u.getName(), u.getState());
        }

        String input = inputReader.readLine("\nSelect user to report (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= reportableUsers.size()) {
                if (idx != -1) {
                    logger.info(CliConstants.INVALID_INPUT);
                }
                return null;
            }
            return reportableUsers.get(idx);
        } catch (NumberFormatException _) {
            logger.info("❌ Invalid input.\n");
            return null;
        }
    }

    /**
     * Prompts the user to select a reason for reporting.
     *
     * @return The selected report reason, or null if invalid
     */
    private Report.Reason selectReportReason() {
        logger.info("\nReason for report:");
        Report.Reason[] reasons = Report.Reason.values();
        for (int i = 0; i < reasons.length; i++) {
            logger.info("  {}. {}", i + 1, reasons[i]);
        }

        String reasonInput = inputReader.readLine("Select reason: ");
        try {
            int reasonIdx = Integer.parseInt(reasonInput) - 1;
            if (reasonIdx < 0 || reasonIdx >= reasons.length) {
                logger.info("❌ Invalid reason.\n");
                return null;
            }
            return reasons[reasonIdx];
        } catch (NumberFormatException _) {
            logger.info("❌ Invalid input.\n");
            return null;
        }
    }
}
