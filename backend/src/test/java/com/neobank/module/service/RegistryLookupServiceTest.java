package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.registry.RegistryClient;
import org.junit.jupiter.api.Test;

class RegistryLookupServiceTest {

    @Test
    void returnsTheFirstSuccessfulLookup() {
        RegistryClient client = mock(RegistryClient.class);
        when(client.findCustomer("APP-1", "Maria Nowak", "1996-04-11"))
                .thenReturn(new RegistryClient.RegistryCustomer(false));

        RegistryLookupService.RegistrySnapshot result =
                new RegistryLookupService(client)
                        .lookup("APP-1", "Maria Nowak", "1996-04-11");

        assertThat(result.available()).isTrue();
        assertThat(result.activeProductHeld()).isFalse();
        verify(client).findCustomer("APP-1", "Maria Nowak", "1996-04-11");
    }

    @Test
    void exactlyThreeFailuresBecomeUnavailable() {
        RegistryClient client = mock(RegistryClient.class);
        when(client.findCustomer("APP-2", "Sofia Ruiz", "1991-05-20"))
                .thenThrow(new IllegalStateException("registry down"));

        RegistryLookupService.RegistrySnapshot result =
                new RegistryLookupService(client)
                        .lookup("APP-2", "Sofia Ruiz", "1991-05-20");

        assertThat(result.available()).isFalse();
        verify(client, times(3)).findCustomer("APP-2", "Sofia Ruiz", "1991-05-20");
    }
}
