package com.neobank.module;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.repository.PolicyRecordRepository;

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
                "applicant": {"fullName": "Maria Nowak", "dateOfBirth": "1996-04-11"},
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
                .andExpect(jsonPath("$.paths['/api/v1/applications'].post").exists());
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
                .andExpect(jsonPath("$.mockedDependencies", hasSize(2)))
                .andExpect(jsonPath("$.mockedDependencies[0]").value("id-verification-provider"));
    }

    @Test
    void anApplicationIsPersistedBeforeTheAcknowledgementAndReadableBack() throws Exception {
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
                .satisfies(row -> assertThat(row.getProcessingStatus()).isEqualTo("IN_PROGRESS"));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationId == 'IT-ONE')].submittedAt")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
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
                        .content("{\"applicationId\":\"X\",,...}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("malformed request body")));
    }

    @Test
    void searchFindsRecordsOutsideTheDefaultTopTen() throws Exception {
        // Insert 25 records in order; the first one (IT-SRCH-001) will be the oldest of this batch
        // and must NOT appear in the capped default list, but MUST be findable via ?q=.
        for (int i = 1; i <= 25; i++) {
            mvc.perform(post("/api/v1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(application("IT-SRCH-%03d".formatted(i))))
                    .andExpect(status().isAccepted());
        }

        // Default list (no query) does NOT include the first-inserted record — it is outside top 10
        String defaultBody = mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(defaultBody).doesNotContain("IT-SRCH-001");

        // Blank ?q= returns []
        mvc.perform(get("/api/v1/applications?q="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Exact ID search finds the record even though it is outside the default top 10
        mvc.perform(get("/api/v1/applications?q=IT-SRCH-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value("IT-SRCH-001"));

        // Partial prefix search returns at most 10 results, all matching
        mvc.perform(get("/api/v1/applications?q=IT-SRCH-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void httpRequestSearchResponseHasCorrectJsonStructure() throws Exception {
        // Ensure the HTTP response includes all required fields in camelCase
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("HTTP-STRUCT-TEST")))
                .andExpect(status().isAccepted());

        mvc.perform(get("/api/v1/applications?q=HTTP-STRUCT-TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value("HTTP-STRUCT-TEST"))
                .andExpect(jsonPath("$[0].submittedAt").exists())
                .andExpect(jsonPath("$[0].sampled").exists())
                .andExpect(jsonPath("$[0].reasonCount").isNumber());
    }

    @Test
    void httpRequestSearchIsCaseInsensitive() throws Exception {
        // Create a test record
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("Case-Insensitive-Test")))
                .andExpect(status().isAccepted());

        // Search with uppercase
        mvc.perform(get("/api/v1/applications?q=CASE-INSENSITIVE-TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value("Case-Insensitive-Test"));

        // Search with lowercase
        mvc.perform(get("/api/v1/applications?q=case-insensitive-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value("Case-Insensitive-Test"));

        // Search with mixed case
        mvc.perform(get("/api/v1/applications?q=CaSe-InSeNsItIvE-TeSt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void httpRequestSearchReturnsOrderedBySubmittedAtDescending() throws Exception {
        // Insert records in order
        String[] ids = {"SORT-FIRST", "SORT-SECOND", "SORT-THIRD"};
        for (String id : ids) {
            mvc.perform(post("/api/v1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(application(id)))
                    .andExpect(status().isAccepted());
            // Small delay to ensure different timestamps
            Thread.sleep(10);
        }

        // Search and verify order: most recent first (DESC)
        mvc.perform(get("/api/v1/applications?q=SORT-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("SORT-THIRD"))
                .andExpect(jsonPath("$[1].applicationId").value("SORT-SECOND"))
                .andExpect(jsonPath("$[2].applicationId").value("SORT-FIRST"));
    }

    @Test
    void httpRequestSearchReturnsEmptyForNonExistentQuery() throws Exception {
        mvc.perform(get("/api/v1/applications?q=DEFINITELY-DOES-NOT-EXIST-XYZ-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void httpRequestDefaultListReturnsTopTenOnly() throws Exception {
        // Insert 15 records
        for (int i = 1; i <= 15; i++) {
            mvc.perform(post("/api/v1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(application("TOP-TEN-TEST-%02d".formatted(i))))
                    .andExpect(status().isAccepted());
        }

        // Default list must cap at 10
        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void httpRequestGetApplicationsAlwaysReturns200Status() throws Exception {
        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/applications?q="))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/applications?q=nonexistent"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("STATUS-TEST")))
                .andExpect(status().isAccepted());

        mvc.perform(get("/api/v1/applications?q=STATUS-TEST"))
                .andExpect(status().isOk());
    }

    @Test
    void httpRequestCasesSearchReturnsSeedDataWithOutcomes() throws Exception {
        // Search for seeded test cases which have outcome data
        mvc.perform(get("/api/v1/cases?q=uc01-maria-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value("uc01-maria-001"))
                .andExpect(jsonPath("$[0].outcome").value("APPROVED"));

        mvc.perform(get("/api/v1/cases?q=uc01-sofia-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value("uc01-sofia-001"))
                .andExpect(jsonPath("$[0].outcome").value("REJECTED"));
    }

        @Test
        void httpRequestCasesNameQueryFindsMariaCheckpointCase() throws Exception {
                mvc.perform(get("/api/v1/cases?q=Maria&limit=10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.applicationId == 'uc01-maria-001')]").exists());
        }

        @Test
        void httpRequestApplicantHydrationReturnsRetryableWhenOrchestratorIsDown() throws Exception {
                // In test profile the orchestrator URL points to a dead port (localhost:9).
                mvc.perform(get("/api/v1/cases/uc01-maria-001/applicant"))
                                .andExpect(status().isServiceUnavailable())
                                .andExpect(jsonPath("$.retryable").value(true))
                                .andExpect(jsonPath("$.applicationId").value("uc01-maria-001"))
                                .andExpect(jsonPath("$.applicantName").value("—"));
        }

    @Test
    void httpRequestUrlEncodedSpacesAreHandledCorrectly() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("SPACE-TEST-ID")))
                .andExpect(status().isAccepted());

        // Search with URL-encoded space (%20) in query
        mvc.perform(get("/api/v1/applications?q=SPACE%20TEST"))
                .andExpect(status().isOk());

        // Search returns empty when space doesn't match (ID has no spaces)
        mvc.perform(get("/api/v1/applications?q=SPACE%20TEST%20ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void httpRequestPostApplicationReturnsCorrectJsonFields() throws Exception {
        // Verify the POST response includes all required fields
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application("HTTP-POST-JSON")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.applicationId").value("HTTP-POST-JSON"))
                .andExpect(jsonPath("$.serviceId").value("neo02"))
                .andExpect(jsonPath("$.command").value("process-application"));
    }
}
