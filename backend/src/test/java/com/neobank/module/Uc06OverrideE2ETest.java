package com.neobank.module;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full UC06 API -> transaction -> audit -> callback -> read-model proof. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:uc06;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class Uc06OverrideE2ETest {

    private static final String COMMAND = """
            {
              "newOutcome": "APPROVED",
              "reason": "registry entry stale - card closed in May",
              "operator": "b.dimovski"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PolicyRecordRepository records;

    @Autowired
    private OverrideLogRepository overrides;

    @MockBean
    private OrchestratorClient orchestrator;

    @BeforeEach
    void setUpRejectedCase() {
        overrides.deleteAll();
        records.deleteAll();
        PolicyRecord james = new PolicyRecord("app-1242", "pol-000216");
        james.completeDecision(new DecisionResult(
                PolicyOutcome.REJECTED,
                PolicyOutcome.REJECTED,
                List.of(
                        RuleResult.existingProduct(
                                false,
                                true,
                                List.of("POL_EXISTING_PRODUCT_HELD")),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(false, 3, List.of()))));
        records.saveAndFlush(james);
    }

    @Test
    void overrideIsAuditedIdempotentAndKeepsTheMachineDecision() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/cases/app-1242/override")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(COMMAND))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("APPROVED"))
                    .andExpect(jsonPath("$.machineOutcome").value("REJECTED"))
                    .andExpect(jsonPath("$.ruleResults.length()").value(4))
                    .andExpect(jsonPath("$.decidedBy").value("b.dimovski"))
                    .andExpect(jsonPath("$.decisionReason").value(
                            "registry entry stale - card closed in May"))
                    .andExpect(jsonPath("$.overrides.length()").value(1))
                    .andExpect(jsonPath("$.overrides[0].oldOutcome").value("REJECTED"))
                    .andExpect(jsonPath("$.overrides[0].newOutcome").value("APPROVED"));
        }

        verify(orchestrator, times(1)).applicationStatusUpdate(
                "app-1242",
                Decision.ACCEPTED,
                "POL_MANUAL_APPROVED: registry entry stale - card closed in May "
                        + "(operator: b.dimovski)");

        mvc.perform(get("/cases/app-1242"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.machineOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.ruleResults[0].reasonCodes[0]")
                        .value("POL_EXISTING_PRODUCT_HELD"))
                .andExpect(jsonPath("$.overrides.length()").value(1));
    }
}
