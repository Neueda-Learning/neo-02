package com.neobank.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.Executor;
import com.neobank.module.repository.PolicyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole module against in-memory H2 (Liquibase applies the schema, JPA validates the
 * entities against it) and drives the real HTTP surface. No Docker or MySQL needed for
 * {@code mvn test}.
 *
 * <p>The work runs on the <em>test</em> thread here (see {@link SameThreadExecutor}), so by the time
 * a {@code POST} returns the row has already been written and the whole receive → work → report loop
 * is observable without sleeping or polling. The real pool is exercised for real by
 * {@code docker compose up}.</p>
 *
 * <p>The status update goes to {@code http://localhost:9} — a dead port, set in
 * {@code application-test.yml} — so nothing escapes the JVM and the client's swallow-and-log
 * behaviour is exercised on every test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModuleApplicationTests {

    /**
     * Swaps Boot's thread pool for one that runs the task inline. Async code is only hard to test
     * when you let it stay async — replacing the executor is cheaper and far more reliable than
     * sleeping and hoping.
     */
    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "applicationTaskExecutor")
        Executor applicationTaskExecutor() {
            return Runnable::run;
        }
    }

    /** SIM-01 from the sidecar corpus, trimmed to what these assertions read. */
    private static final String APPLICATION = """
            {
              "applicationId": "%s",
              "correlationId": "sim-0001-4c1a-8f2b-1d5e9a000001",
              "command": "process-application",
              "application": {
                "applicationId": "%s",
                "channel": "MOBILE_APP",
                "submittedAt": "2026-07-25T09:14:00Z",
                "applicant": {
                  "fullName": "Maria Nowak",
                  "dateOfBirth": "1996-04-11",
                  "taxResidencies": ["GB"]
                },
                "product": {"productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 3000}
              }
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PolicyRecordRepository records;

    private static String application(String id) {
        return APPLICATION.formatted(id, id);
    }

    @Test
    void contextLoads() {
        // Reaching here means Liquibase created policy_record and JPA validated it.
    }

    @Test
    void healthReportsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.serviceId").value("neo02"))
                .andExpect(jsonPath("$.database.status").value("UP"));
    }

    @Test
    void executeEndpointAppearsInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications'].post").exists())
                .andExpect(jsonPath("$.paths['/cases/{applicationId}'].get").exists());
    }

    @Test
    void infoReportsIdentityDomainAndWhatIsMocked() throws Exception {
        mvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value("neo02"))
                .andExpect(jsonPath("$.domain").value("policy"))
                // The UI's identity box reads team + service. A team that never sets SERVICE_TEAM
                // ships a screen claiming to be team 01's, so the field has to actually be served.
                .andExpect(jsonPath("$.team").value("Team 02"))
                .andExpect(jsonPath("$.mockedDependencies", hasSize(1)))
                .andExpect(jsonPath("$.mockedDependencies[0]").value("customer-registry"));
    }

    @Test
    void anApplicationIsDecidedAndItsStoredDetailIsReadable() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("IT-ONE")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.applicationId").value("IT-ONE"))
                .andExpect(jsonPath("$.serviceId").value("neo02"))
                .andExpect(jsonPath("$.command").value("process-application"));

        assertThat(records.findById("IT-ONE"))
                .get()
                .satisfies(row -> {
                    assertThat(row.getProcessingStatus()).isEqualTo("DECIDED");
                    assertThat(row.getOutcome().name()).isEqualTo("APPROVED");
                    assertThat(row.getPolicyConfigVersion()).isEqualTo(1);
                    assertThat(row.getRuleResults()).hasSize(4);
                });

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].status")
                        .value(org.hamcrest.Matchers.hasItem("APPROVED")))
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].createdAt")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));

        mvc.perform(get("/cases/IT-ONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.machineOutcome").value("APPROVED"))
                .andExpect(jsonPath("$.policyConfigVersion").value(1))
                .andExpect(jsonPath("$.ruleResults.length()").value(4))
                .andExpect(jsonPath("$.ruleResults[3].reasonCodes[0]")
                        .value("POL_ALL_CHECKS_PASSED"));
    }

    @Test
    void repeatedApplicationIsAcknowledgedButStoredOnlyOnce() throws Exception {
        String body = application("IT-DUPLICATE");

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        assertThat(records.findAll().stream()
                .filter(row -> row.getApplicationId().equals("IT-DUPLICATE")))
                .hasSize(1);
    }

    @Test
    void anApplicationWithoutAnIdIsRejected() throws Exception {
        // The one field worth validating: a decision this module cannot report is not worth making.
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correlationId":"c-1","command":"process-application",
                                 "application":{"channel":"WEB"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("applicationId")));
    }

    @Test
    void malformedJsonIsA400WithSomethingToRead() throws Exception {
        // You will meet this: the sidecar lets you edit the envelope before sending it.
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":\"X\",,}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("malformed request body")));
    }

    @Test
    void unknownCaseIsAJson404() throws Exception {
        mvc.perform(get("/cases/IT-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("IT-MISSING")));
    }
}
