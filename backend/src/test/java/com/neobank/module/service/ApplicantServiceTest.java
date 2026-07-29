package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
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

    @InjectMocks
    private ApplicantService applicants;

    @Test
    void returnsTheLiveApplicationAndPassesTheIdThroughUntouched() {
        Application application = application();
        when(orchestrator.application("app-1240")).thenReturn(application);

        assertThat(applicants.find("app-1240")).isSameAs(application);
        verify(orchestrator).application("app-1240");
    }

    @Test
    void wrapsAnUnavailableOrchestratorAsARetryableApplicantFailure() {
        when(orchestrator.application("app-1240"))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> applicants.find("app-1240"))
                .isInstanceOf(ApplicantUnavailableException.class)
                .hasMessageContaining("app-1240")
                .hasMessageContaining("Retry");
    }

    @Test
    void treatsAnEmptyUpstreamBodyAsUnavailable() {
        when(orchestrator.application("app-1240")).thenReturn(null);

        assertThatThrownBy(() -> applicants.find("app-1240"))
                .isInstanceOf(ApplicantUnavailableException.class);
    }

    private static Application application() {
        return new Application(
                "app-1240",
                "WEB",
                "2026-07-25T09:14:00Z",
                new Application.Applicant(
                        "Sofia Ruiz",
                        "1991-05-20",
                        null,
                        null,
                        "ESP",
                        "GB",
                        List.of("GB", "US"),
                        null,
                        null,
                        null,
                        null),
                null,
                null,
                null,
                new Application.Product("CREDIT_CARD_STANDARD", 2000),
                null,
                null);
    }
}
