package datingapp.module;

import datingapp.core.AppConfig;
import datingapp.core.TrustSafetyService;
import datingapp.core.ValidationService;
import java.util.Objects;

/**
 * Module containing trust and safety services. Handles reports, blocks, user verification, and
 * input validation.
 */
public record SafetyModule(TrustSafetyService trustSafety, ValidationService validation) implements Module {

    public SafetyModule {
        Objects.requireNonNull(trustSafety, "trustSafety cannot be null");
        Objects.requireNonNull(validation, "validation cannot be null");
    }

    /**
     * Creates a SafetyModule with all required services.
     *
     * @param storage The storage module providing data access
     * @param config Application configuration
     * @return Fully configured SafetyModule
     */
    public static SafetyModule create(StorageModule storage, AppConfig config) {
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        TrustSafetyService trustSafety =
                new TrustSafetyService(storage.reports(), storage.users(), storage.blocks(), config);

        ValidationService validation = new ValidationService();

        return new SafetyModule(trustSafety, validation);
    }
}
