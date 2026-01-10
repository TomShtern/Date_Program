package datingapp.cli;

import datingapp.core.User;
import datingapp.core.UserStorage;
import datingapp.core.VerificationService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileVerificationHandler {
  private static final Logger logger = LoggerFactory.getLogger(ProfileVerificationHandler.class);

  private final UserStorage userStorage;
  private final VerificationService verificationService;
  private final UserSession userSession;
  private final InputReader inputReader;

  public ProfileVerificationHandler(
      UserStorage userStorage,
      VerificationService verificationService,
      UserSession userSession,
      InputReader inputReader) {
    this.userStorage = Objects.requireNonNull(userStorage);
    this.verificationService = Objects.requireNonNull(verificationService);
    this.userSession = Objects.requireNonNull(userSession);
    this.inputReader = Objects.requireNonNull(inputReader);
  }

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
    switch (choice) {
      case "1" -> startVerification(currentUser, User.VerificationMethod.EMAIL);
      case "2" -> startVerification(currentUser, User.VerificationMethod.PHONE);
      case "0" -> {
        return;
      }
      default -> logger.info(CliConstants.INVALID_SELECTION);
    }
  }

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

    String generatedCode = verificationService.generateVerificationCode();
    user.startVerification(method, generatedCode);
    userStorage.save(user);

    logger.info("\n[SIMULATED DELIVERY] Your verification code is: {}\n", generatedCode);

    String inputCode = inputReader.readLine("Enter the code: ");
    if (!verificationService.verifyCode(user, inputCode)) {
      if (verificationService.isExpired(user.getVerificationSentAt())) {
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
