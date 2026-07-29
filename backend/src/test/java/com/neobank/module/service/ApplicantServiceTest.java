package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ApplicantView;
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
    private ApplicantService applicants;

    @Test
    void returnsOnlyTheMinimumLiveApplicantProjection() {
        Application application = application();
        when(records.existsById("app-1240")).thenReturn(true);
        when(orchestrator.application("app-1240")).thenReturn(application);

        ApplicantView result = applicants.find("app-1240");

        assertThat(result.applicationId()).isEqualTo("app-1240");
        assertThat(result.channel()).isEqualTo("WEB");
        assertThat(result.applicant().fullName()).isEqualTo("Sofia Ruiz");
        assertThat(result.applicant().taxResidencies()).containsExactly("GB", "US");
        assertThat(result.product().productCode()).isEqualTo("CREDIT_CARD_STANDARD");
        verify(orchestrator).application("app-1240");
    }

    @Test
    void rejectsAnApplicationThatIsNotALocalPolicyCase() {
        when(records.existsById("not-ours")).thenReturn(false);

        assertThatThrownBy(() -> applicants.find("not-ours"))
                .isInstanceOf(CaseNotFoundException.class)
                .hasMessageContaining("not-ours");

        verify(orchestrator, never()).application("not-ours");
    }

    @Test
    void wrapsAnUnavailableOrchestratorAsARetryableApplicantFailure() {
        when(records.existsById("app-1240")).thenReturn(true);
        when(orchestrator.application("app-1240"))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> applicants.find("app-1240"))
                .isInstanceOf(ApplicantUnavailableException.class)
                .hasMessageContaining("app-1240")
                .hasMessageContaining("Retry");
    }

    @Test
    void treatsAnEmptyUpstreamBodyAsUnavailable() {
        when(records.existsById("app-1240")).thenReturn(true);
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
                        "sofia@example.com",
                        "+44-7000-000000",
                        "ESP",
                        "GB",
                        List.of("GB", "US"),
                        "OWNER",
                        new Application.Address(
                                "1 Sensitive Street", null, "London", "SW1A 1AA", "GB"),
                        24,
                        1),
                new Application.IdentityDocument(
                        "PASSPORT", "SECRET-123", "ES", "2030-01-01"),
                new Application.Employment("PERMANENT", "Sensitive Employer", 36),
                new Application.Finances(60000, 1500, 500),
                new Application.Product("CREDIT_CARD_STANDARD", 2000),
                new Application.Delivery(false,
                        new Application.Address(
                                "2 Private Road", null, "London", "SW1A 2AA", "GB")),
                new Application.Consents(true, true, false));
    }
}
