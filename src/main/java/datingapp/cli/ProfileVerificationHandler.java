package datingapp.cli;

import datingapp.core.TrustSafetyService;
import datingapp.core.User;
import datingapp.core.UserStorage;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for profile verification via email or phone. */
public class ProfileVerificationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileVerificationHandler.class);

    private final UserStorage userStorage;
    private final TrustSafetyService trustSafetyService;
    private final CliUtilities.UserSession userSession;
    private final CliUtilities.InputReader inputReader;

    public ProfileVerificationHandler(
            UserStorage userStorage,
            TrustSafetyService trustSafetyService,
            CliUtilities.UserSession userSession,
            CliUtilities.InputReader inputReader) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.userSession = Objects.requireNonNull(userSession);
        this.inputReader = Objects.requireNonNull(inputReader);
    }

    /** Starts the profile verification flow for the current user. */
    public void verifyProfile() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();

        if (Boolean.TRUE.equals(currentUser.isVerified())) {
            logger.info("\n✅ Profile already verified ({}).\n", currentUser.getVerifiedAt());
            return;
        }

        logger.info("\n--- Profile Verification ---\n");
        logger.info("1. Verify by email");
        logger.info("2. Verify by phone");
        logger.info("0. Back\n");

        String choice = inputReader.readLine("Choice: ");
        if ("0".equals(choice)) {
            return;
        }
        if ("1".equals(choice)) {
            startVerification(currentUser, User.VerificationMethod.EMAIL);
        } else if ("2".equals(choice)) {
            startVerification(currentUser, User.VerificationMethod.PHONE);
        } else {
            logger.info(CliConstants.INVALID_SELECTION);
        }
    }

    /**
     * Starts the verification process for a user using the specified method.
     *
     * @param user The user to verify
     * @param method The verification method (email or phone)
     */
    private void startVerification(User user, User.VerificationMethod method) {
        if (method == User.VerificationMethod.EMAIL) {
            String email = inputReader.readLine("Email: ");
            if (email == null || email.isBlank()) {
                logger.info("❌ Email required.\n");
                return;
            }
            user.setEmail(email.trim());
        } else {
            String phone = inputReader.readLine("Phone: ");
            if (phone == null || phone.isBlank()) {
                logger.info("❌ Phone required.\n");
                return;
            }
            user.setPhone(phone.trim());
        }

        String generatedCode = trustSafetyService.generateVerificationCode();
        user.startVerification(method, generatedCode);
        userStorage.save(user);

        logger.info("\n[SIMULATED DELIVERY] Your verification code is: {}\n", generatedCode);

        String inputCode = inputReader.readLine("Enter the code: ");
        if (!trustSafetyService.verifyCode(user, inputCode)) {
            if (trustSafetyService.isExpired(user.getVerificationSentAt())) {
                logger.info("❌ Code expired. Please try again.\n");
            } else {
                logger.info("❌ Incorrect code.\n");
            }
            return;
        }

        user.markVerified();
        userStorage.save(user);
        logger.info("✅ Profile verified!\n");
    }
}
