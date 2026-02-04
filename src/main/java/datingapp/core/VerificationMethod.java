package datingapp.core;

/**
 * Represents the verification method used to verify a profile.
 *
 * <p>NOTE: Currently simulated - email/phone not sent externally. Future enhancement:
 * integrate real email/SMS service for actual code delivery.
 */
public enum VerificationMethod {
    EMAIL,
    PHONE
}
