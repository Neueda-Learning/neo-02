package com.neobank.module.integrations.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryRegistryClientTest {

    private final InMemoryRegistryClient registry = new InMemoryRegistryClient();

    @Test
    void jamesFixtureMatchesByNameAndDateOfBirth() {
        assertThat(registry.findCustomer(
                        "app-1242", "James Whitfield", "1988-03-12")
                .activeProductHeld()).isTrue();
    }

    @Test
    void sameNameWithAnotherDateOfBirthIsNotTheFixtureCustomer() {
        assertThat(registry.findCustomer(
                        "another-application", "James Whitfield", "1988-03-13")
                .activeProductHeld()).isFalse();
    }
}
