package com.neobank.module.integrations.registry;

import java.util.Locale;
import java.util.Set;

/**
 * Development registry fixture permitted by the UC02 brief until the orchestrator publishes its
 * registry GET contract.
 */
public class InMemoryRegistryClient implements RegistryClient {

    private static final Set<String> ACTIVE_CUSTOMERS =
            Set.of(identity("James Whitfield", "1988-03-12"));

    @Override
    public RegistryCustomer findCustomer(
            String applicationId, String fullName, String dateOfBirth) {
        return new RegistryCustomer(
                ACTIVE_CUSTOMERS.contains(identity(fullName, dateOfBirth)));
    }

    private static String identity(String fullName, String dateOfBirth) {
        return normalize(fullName) + "|" + normalize(dateOfBirth);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
