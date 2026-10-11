package datingapp.core.verification;

import datingapp.core.model.User.VerificationMethod;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development sender: logs the destination, never the code itself. */
public final class LocalLogVerificationSender implements VerificationSender {

    private static final Logger LOG = LoggerFactory.getLogger(LocalLogVerificationSender.class);

    @Override
    public void send(VerificationMethod method, String contact, String code) {
        Objects.requireNonNull(method, "method cannot be null");
        if (LOG.isInfoEnabled()) {
            LOG.info("Verification code sent via {} to {}", method, mask(contact));
        }
    }

    private static String mask(String contact) {
        if (contact == null || contact.length() <= 3) {
            return "***";
        }
        return contact.substring(0, 2) + "***" + contact.substring(contact.length() - 1);
    }
}
