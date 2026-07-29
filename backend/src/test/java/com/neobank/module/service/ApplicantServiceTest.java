package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceTest {

    @Mock
    private OrchestratorClient orchestrator;

    @Mock
    private PolicyRecordRepository records;

    @InjectMocks
    private ApplicantService service;

    @Test
    void mapsTheOrchestratorApplicationWithoutPersistingAnything() {
        when(records.existsById("app-1240")).thenReturn(true);
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

        ApplicantViewDto result = service.find("app-1240");

        assertThat(result.fullName()).isEqualTo("Sofia Ruiz");
        assertThat(result.dateOfBirth()).isEqualTo("1990-02-14");
        assertThat(result.taxResidencies()).containsExactly("GB", "US");
        assertThat(result.productCode()).isEqualTo("CREDIT_CARD_REWARDS");
        assertThat(result.channel()).isEqualTo("WEB");
        assertThat(result.countryOfResidence()).isEqualTo("GB");
        verify(records).existsById("app-1240");
        verify(orchestrator).getApplication("app-1240");
    }

    @Test
    void rejectsAnApplicationThisModuleHasNeverReceivedWithoutCallingTheOrchestrator() {
        when(records.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.find("missing"))
                .isInstanceOf(CaseNotFoundException.class)
                .hasMessageContaining("missing");

        verify(orchestrator, never()).getApplication("missing");
    }

    @Test
    void turnsAnOrchestratorConnectionFailureIntoARetryableDomainFailure() {
        when(records.existsById("app-1240")).thenReturn(true);
        when(orchestrator.getApplication("app-1240"))
                .thenThrow(new ResourceAccessException("timed out"));

        assertThatThrownBy(() -> service.find("app-1240"))
                .isInstanceOf(ApplicantUnavailableException.class)
                .hasMessageContaining("Retry");
    }

    @Test
    void treatsAnEmptyOrchestratorResponseAsUnavailable() {
        when(records.existsById("app-1240")).thenReturn(true);
        when(orchestrator.getApplication("app-1240")).thenReturn(null);

        assertThatThrownBy(() -> service.find("app-1240"))
                .isInstanceOf(ApplicantUnavailableException.class)
                .hasMessageContaining("app-1240");
    }
}
