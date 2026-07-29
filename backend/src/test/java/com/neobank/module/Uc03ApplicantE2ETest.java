package com.neobank.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** UC03 regression proof: repeated hydration stays live and does not mutate the local case. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Uc03ApplicantE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PolicyRecordRepository records;

    @MockBean
    private OrchestratorClient orchestrator;

    @BeforeEach
    void ensureOwnedCaseExists() {
        if (!records.existsById("app-1240")) {
            records.saveAndFlush(new PolicyRecord("app-1240", "pol-uc03-1240"));
        }
    }

    @Test
    void applicantProxyAppearsInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/cases/{applicationId}/applicant'].get").exists());
    }

    @Test
    void sofiaIsHydratedLiveTwiceWithoutChangingTheStoredCase() throws Exception {
        when(orchestrator.getApplication("app-1240")).thenReturn(application());
        long rowsBefore = records.count();
        PolicyRecord before = records.findById("app-1240").orElseThrow();

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/cases/app-1240/applicant"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Sofia Ruiz"))
                    .andExpect(jsonPath("$.dateOfBirth").value("1991-05-20"))
                    .andExpect(jsonPath("$.taxResidencies[0]").value("GB"))
                    .andExpect(jsonPath("$.taxResidencies[1]").value("US"))
                    .andExpect(jsonPath("$.countryOfResidence").value("GB"))
                    .andExpect(jsonPath("$.productCode").value("CREDIT_CARD_STANDARD"))
                    .andExpect(jsonPath("$.channel").value("WEB"))
                    .andExpect(jsonPath("$.email").doesNotExist())
                    .andExpect(jsonPath("$.mobile").doesNotExist())
                    .andExpect(jsonPath("$.identityDocument").doesNotExist())
                    .andExpect(jsonPath("$.employment").doesNotExist())
                    .andExpect(jsonPath("$.finances").doesNotExist())
                    .andExpect(jsonPath("$.delivery").doesNotExist())
                    .andExpect(jsonPath("$.consents").doesNotExist())
                    .andExpect(jsonPath("$.requestedCreditLimit").doesNotExist());
        }

        PolicyRecord after = records.findById("app-1240").orElseThrow();
        assertThat(records.count()).isEqualTo(rowsBefore);
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(after.getApplicantFullName()).isEqualTo(before.getApplicantFullName());
        verify(orchestrator, times(2)).getApplication("app-1240");
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
