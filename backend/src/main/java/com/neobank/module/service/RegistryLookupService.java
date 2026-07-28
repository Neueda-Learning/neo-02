package com.neobank.module.service;

import com.neobank.module.integrations.registry.RegistryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Applies the locked three-attempt policy around the Registry client. */
@Service
public class RegistryLookupService {

    private static final Logger log = LoggerFactory.getLogger(RegistryLookupService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RegistryClient registry;

    public RegistryLookupService(RegistryClient registry) {
        this.registry = registry;
    }

    public RegistrySnapshot lookup(String applicationId, String fullName, String dateOfBirth) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RegistryClient.RegistryCustomer customer =
                        registry.findCustomer(applicationId, fullName, dateOfBirth);
                return RegistrySnapshot.available(customer.activeProductHeld());
            } catch (RuntimeException failure) {
                lastFailure = failure;
                log.warn("Registry attempt {} of {} failed for {}",
                        attempt, MAX_ATTEMPTS, applicationId);
            }
        }
        log.error("Registry unavailable after {} attempts for {}",
                MAX_ATTEMPTS, applicationId, lastFailure);
        return RegistrySnapshot.unavailable();
    }

    public record RegistrySnapshot(boolean available, boolean activeProductHeld) {

        static RegistrySnapshot available(boolean activeProductHeld) {
            return new RegistrySnapshot(true, activeProductHeld);
        }

        static RegistrySnapshot unavailable() {
            return new RegistrySnapshot(false, false);
        }
    }
}
