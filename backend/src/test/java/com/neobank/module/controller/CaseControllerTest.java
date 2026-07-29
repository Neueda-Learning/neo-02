package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.model.RuleResult;
import com.neobank.module.service.ApplicantUnavailableException;
import com.neobank.module.service.ApplicantService;
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
    void proxiesTheLiveApplicantSubset() throws Exception {
        when(applicants.find("app-1240")).thenReturn(new ApplicantViewDto(
                "Sofia Ruiz",
                "1990-02-14",
                List.of("GB", "US"),
                "CREDIT_CARD_REWARDS",
                "WEB",
                "GB"));

        mvc.perform(get("/cases/app-1240/applicant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Sofia Ruiz"))
                .andExpect(jsonPath("$.taxResidencies[0]").value("GB"))
                .andExpect(jsonPath("$.taxResidencies[1]").value("US"))
                .andExpect(jsonPath("$.productCode").value("CREDIT_CARD_REWARDS"))
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.countryOfResidence").value("GB"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.mobile").doesNotExist())
                .andExpect(jsonPath("$.identityDocument").doesNotExist())
                .andExpect(jsonPath("$.employment").doesNotExist())
                .andExpect(jsonPath("$.finances").doesNotExist())
                .andExpect(jsonPath("$.delivery").doesNotExist())
                .andExpect(jsonPath("$.consents").doesNotExist())
                .andExpect(jsonPath("$.requestedCreditLimit").doesNotExist());
    }

    @Test
    void unavailableApplicantReturnsJson503() throws Exception {
        when(applicants.find("app-1240"))
                .thenThrow(new ApplicantUnavailableException("app-1240"));

        mvc.perform(get("/cases/app-1240/applicant"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Retry")));
    }

    @Test
    void applicantForUnknownLocalCaseReturnsJson404() throws Exception {
        when(applicants.find("missing")).thenThrow(new CaseNotFoundException("missing"));

        mvc.perform(get("/cases/missing/applicant"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("missing")));
    }
}
