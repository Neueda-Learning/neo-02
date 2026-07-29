package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.RuleResult;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.ApplicantUnavailableException;
import com.neobank.module.service.CaseDetailService;
import com.neobank.module.service.CaseNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CaseController.class)
class CaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CaseDetailService cases;

    @MockBean
    private ApplicantService applicants;

    @Test
    void returnsTheStoredDecisionAndFourRuleSections() throws Exception {
        when(cases.find("app-1234")).thenReturn(new CaseDetailView(
                "APPROVED",
                "APPROVED",
                "pol-000214",
                1,
                List.of(
                        RuleResult.existingProduct(true, true, List.of()),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(false, 1, List.of("POL_ALL_CHECKS_PASSED")))));

        mvc.perform(get("/cases/app-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.machineOutcome").value("APPROVED"))
                .andExpect(jsonPath("$.reference").value("pol-000214"))
                .andExpect(jsonPath("$.policyConfigVersion").value(1))
                .andExpect(jsonPath("$.ruleResults.length()").value(4))
                .andExpect(jsonPath("$.ruleResults[0].registryChecked").value(true))
                .andExpect(jsonPath("$.ruleResults[3].sampled").value(false));
    }

    @Test
    void unknownCaseReturnsJson404() throws Exception {
        when(cases.find("missing")).thenThrow(new CaseNotFoundException("missing"));

        mvc.perform(get("/cases/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("missing")));
    }

    @Test
    void proxiesTheLiveApplicantApplication() throws Exception {
        when(applicants.find("app-1240")).thenReturn(application());

        mvc.perform(get("/cases/app-1240/applicant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("app-1240"))
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.applicant.fullName").value("Sofia Ruiz"))
                .andExpect(jsonPath("$.applicant.countryOfResidence").value("GB"))
                .andExpect(jsonPath("$.applicant.taxResidencies[0]").value("GB"))
                .andExpect(jsonPath("$.applicant.taxResidencies[1]").value("US"))
                .andExpect(jsonPath("$.product.productCode").value("CREDIT_CARD_STANDARD"));
    }

    @Test
    void unavailableApplicantIsARetryableJson503() throws Exception {
        when(applicants.find("app-1240"))
                .thenThrow(new ApplicantUnavailableException("app-1240"));

        mvc.perform(get("/cases/app-1240/applicant"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Retry")));
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
