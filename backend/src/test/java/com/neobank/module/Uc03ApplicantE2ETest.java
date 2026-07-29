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
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UC03 regression proof: repeated hydration stays live and leaves this module's schema untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Uc03ApplicantE2ETest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PolicyRecordRepository records;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private OrchestratorClient orchestrator;

    @Test
    void applicantProxyAppearsInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/cases/{applicationId}/applicant'].get").exists());
    }

    @Test
    void sofiaIsHydratedLiveTwiceWithoutAWriteOrApplicantColumn() throws Exception {
        when(orchestrator.application("app-1240")).thenReturn(application());
        long rowsBefore = records.count();

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/cases/app-1240/applicant"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applicant.fullName").value("Sofia Ruiz"))
                    .andExpect(jsonPath("$.applicant.taxResidencies[0]").value("GB"))
                    .andExpect(jsonPath("$.applicant.taxResidencies[1]").value("US"))
                    .andExpect(jsonPath("$.applicant.countryOfResidence").value("GB"))
                    .andExpect(jsonPath("$.product.productCode")
                            .value("CREDIT_CARD_STANDARD"))
                    .andExpect(jsonPath("$.channel").value("WEB"));
        }

        assertThat(records.count()).isEqualTo(rowsBefore);
        assertThat(applicantColumnCount()).isZero();
        verify(orchestrator, times(2)).application("app-1240");
    }

    private int applicantColumnCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'PUBLIC'
                  AND table_name IN ('POLICY_RECORD', 'POLICY_CONFIG', 'OVERRIDE_LOG')
                  AND (
                    LOWER(column_name) LIKE '%applicant%'
                    OR LOWER(column_name) LIKE '%full_name%'
                    OR LOWER(column_name) LIKE '%date_of_birth%'
                    OR LOWER(column_name) LIKE '%tax_residen%'
                    OR LOWER(column_name) LIKE '%email%'
                    OR LOWER(column_name) LIKE '%mobile%'
                    OR LOWER(column_name) LIKE '%country_of_residence%'
                  )
                """, Integer.class);
        return count == null ? -1 : count;
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
