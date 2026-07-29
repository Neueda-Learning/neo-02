package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicantServiceTest {

    @Test
    void mapsTheOrchestratorApplicationWithoutPersistingAnything() {
        OrchestratorClient orchestrator = mock(OrchestratorClient.class);
        when(orchestrator.getApplication("app-1240")).thenReturn(new Application(
                "app-1240",
                "WEB",
                "2026-07-10T09:00:00Z",
                new Application.Applicant(
                        "Sofia Ruiz",
                        "1990-02-14",
                        null,
                        null,
                        "ES",
                        "GB",
                        List.of("GB", "US"),
                        null,
                        null,
                        null,
                        null),
                null,
                null,
                null,
                new Application.Product("CREDIT_CARD_REWARDS", 3000),
                null,
                null));

        ApplicantViewDto result = new ApplicantService(orchestrator).find("app-1240");

        assertThat(result.fullName()).isEqualTo("Sofia Ruiz");
        assertThat(result.taxResidencies()).containsExactly("GB", "US");
        assertThat(result.productCode()).isEqualTo("CREDIT_CARD_REWARDS");
        verify(orchestrator).getApplication("app-1240");
    }
}
