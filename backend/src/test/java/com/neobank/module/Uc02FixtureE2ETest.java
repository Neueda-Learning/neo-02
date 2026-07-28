package com.neobank.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Drives the exact UC02 checkpoints through the real controller, DB, worker, and rule engine. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:uc02fixtures;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class Uc02FixtureE2ETest {

    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "applicationTaskExecutor")
        Executor applicationTaskExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PolicyRecordRepository records;

    @MockBean
    private OrchestratorClient orchestrator;

    @Test
    void exactFixturesProduceTheRequiredDecisionsAndApp1287IsTwentyFirst() throws Exception {
        submit("app-1234", "Maria Nowak", "1996-04-11", "[\"GB\"]");
        submit("app-1240", "Sofia Ruiz", "1991-05-20", "[\"GB\",\"US\"]");
        submit("app-1242", "James Whitfield", "1988-03-12", "[\"GB\"]");
        IntStream.rangeClosed(4, 20).forEach(position ->
                submitUnchecked(
                        "app-filler-" + position,
                        "Clean Applicant " + position,
                        "1990-01-%02d".formatted(position),
                        "[\"GB\"]"));
        submit("app-1287", "Elena Fischer", "1994-08-16", "[\"GB\"]");

        assertThat(records.count()).isEqualTo(21);
        assertThat(records.findById("app-1287")).get()
                .extracting(record -> record.getSamplingPosition())
                .isEqualTo(21L);

        mvc.perform(get("/cases/app-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.machineOutcome").value("APPROVED"))
                .andExpect(jsonPath("$.ruleResults[0].passed").value(true))
                .andExpect(jsonPath("$.ruleResults[1].passed").value(true))
                .andExpect(jsonPath("$.ruleResults[2].passed").value(true))
                .andExpect(jsonPath("$.ruleResults[3].reasonCodes[0]")
                        .value("POL_ALL_CHECKS_PASSED"));

        mvc.perform(get("/cases/app-1240"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.ruleResults[1].reasonCodes[0]")
                        .value("POL_TAX_RESIDENCY_EXCLUDED"));

        mvc.perform(get("/cases/app-1242"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.ruleResults[0].registryChecked").value(true))
                .andExpect(jsonPath("$.ruleResults[0].reasonCodes[0]")
                        .value("POL_EXISTING_PRODUCT_HELD"));

        mvc.perform(get("/cases/app-1287"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REFERRED"))
                .andExpect(jsonPath("$.machineOutcome").value("APPROVED"))
                .andExpect(jsonPath("$.ruleResults[3].sampled").value(true))
                .andExpect(jsonPath("$.ruleResults[3].position").value(21))
                .andExpect(jsonPath("$.ruleResults[3].reasonCodes[0]")
                        .value("POL_SAMPLED_FOR_REVIEW"));
    }

    private void submit(
            String applicationId, String fullName, String dateOfBirth, String taxResidencies)
            throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(
                                applicationId, fullName, dateOfBirth, taxResidencies)))
                .andExpect(status().isAccepted());
    }

    private void submitUnchecked(
            String applicationId, String fullName, String dateOfBirth, String taxResidencies) {
        try {
            submit(applicationId, fullName, dateOfBirth, taxResidencies);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not submit " + applicationId, failure);
        }
    }

    private static String envelope(
            String applicationId, String fullName, String dateOfBirth, String taxResidencies) {
        return """
                {
                  "applicationId": "%s",
                  "correlationId": "uc02-%s",
                  "command": "process-application",
                  "application": {
                    "applicationId": "%s",
                    "channel": "WEB",
                    "submittedAt": "2026-07-28T10:00:00Z",
                    "applicant": {
                      "fullName": "%s",
                      "dateOfBirth": "%s",
                      "countryOfResidence": "GB",
                      "taxResidencies": %s
                    },
                    "product": {
                      "productCode": "CREDIT_CARD_STANDARD",
                      "requestedCreditLimit": 2000
                    }
                  }
                }
                """.formatted(
                applicationId, applicationId, applicationId,
                fullName, dateOfBirth, taxResidencies);
    }
}
