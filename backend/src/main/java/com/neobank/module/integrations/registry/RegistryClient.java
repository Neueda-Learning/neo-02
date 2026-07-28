package com.neobank.module.integrations.registry;

/** Boundary for the customer-registry read owned by the orchestrator. */
@FunctionalInterface
public interface RegistryClient {

    RegistryCustomer findCustomer(String applicationId, String fullName, String dateOfBirth);

    record RegistryCustomer(boolean activeProductHeld) {
    }
}
