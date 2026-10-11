package datingapp.core.verification;

import datingapp.core.model.User.VerificationMethod;

/** Sends verification codes. Local impl logs; future SMS/email providers plug in here. */
public interface VerificationSender {
    void send(VerificationMethod method, String contact, String code);
}
