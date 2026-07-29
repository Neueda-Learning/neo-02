package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.RuleResult;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.ApplicantUnavailableException;
import com.neobank.module.service.CaseConflictException;
import com.neobank.module.service.CaseDetailService;
import com.neobank.module.service.CaseNotFoundException;
import com.neobank.module.service.OverrideCaseService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CaseController.class)
class CaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CaseDetailService cases;

    @MockBean
    private ApplicantService applicants;

    @MockBean
    private OverrideCaseService overrides;

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
        when(applicants.find("app-1240")).thenReturn(applicant());

        mvc.perform(get("/cases/app-1240/applicant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("app-1240"))
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.applicant.fullName").value("Sofia Ruiz"))
                .andExpect(jsonPath("$.applicant.countryOfResidence").value("GB"))
                .andExpect(jsonPath("$.applicant.taxResidencies[0]").value("GB"))
                .andExpect(jsonPath("$.applicant.taxResidencies[1]").value("US"))
                .andExpect(jsonPath("$.product.productCode").value("CREDIT_CARD_STANDARD"))
                .andExpect(jsonPath("$.applicant.email").doesNotExist())
                .andExpect(jsonPath("$.applicant.mobile").doesNotExist())
                .andExpect(jsonPath("$.identityDocument").doesNotExist())
                .andExpect(jsonPath("$.employment").doesNotExist())
                .andExpect(jsonPath("$.finances").doesNotExist())
                .andExpect(jsonPath("$.delivery").doesNotExist())
                .andExpect(jsonPath("$.consents").doesNotExist())
                .andExpect(jsonPath("$.product.requestedCreditLimit").doesNotExist());
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

    @Test
    void unknownApplicantCaseReturnsJson404() throws Exception {
        when(applicants.find("not-ours")).thenThrow(new CaseNotFoundException("not-ours"));

        mvc.perform(get("/cases/not-ours/applicant"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not-ours")));
    }

    @Test
    void overridesACaseAndReturnsTheUpdatedDetail() throws Exception {
        OverrideCaseRequest request = new OverrideCaseRequest(
                PolicyOutcome.APPROVED,
                "registry entry stale",
                "b.dimovski");
        when(overrides.override("app-1242", request, 7L)).thenReturn(new CaseDetailView(
                "APPROVED",
                "REJECTED",
                "pol-000216",
                1,
                List.of(),
                "b.dimovski",
                java.time.Instant.parse("2026-07-29T02:00:00Z"),
                "registry entry stale",
                List.of()));

        mvc.perform(post("/cases/app-1242/override")
                        .header("X-Expected-Version", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOutcome": "APPROVED",
                                  "reason": "registry entry stale",
                                  "operator": "b.dimovski"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.machineOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.decidedBy").value("b.dimovski"))
                .andExpect(jsonPath("$.decisionReason").value("registry entry stale"));
    }

    @Test
    void overrideRequiresReasonAndOperator() throws Exception {
        mvc.perform(post("/cases/app-1242/override")
                        .header("X-Expected-Version", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome": "APPROVED", "reason": " ", "operator": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void overrideRejectsAnUnknownOutcome() throws Exception {
        mvc.perform(post("/cases/app-1242/override")
                        .header("X-Expected-Version", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOutcome": "MAYBE",
                                  "reason": "test",
                                  "operator": "b.dimovski"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("malformed request body")));
    }

    @Test
    void overrideConflictReturnsJson409() throws Exception {
        when(overrides.override(
                "app-1242",
                new OverrideCaseRequest(
                        PolicyOutcome.APPROVED,
                        "different reason",
                        "b.dimovski"),
                7L))
                .thenThrow(new CaseConflictException("stale override"));

        mvc.perform(post("/cases/app-1242/override")
                        .header("X-Expected-Version", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOutcome": "APPROVED",
                                  "reason": "different reason",
                                  "operator": "b.dimovski"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("stale override"));
    }

    @Test
    void overrideRequiresAnExpectedVersionHeader() throws Exception {
        mvc.perform(post("/cases/app-1242/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOutcome": "APPROVED",
                                  "reason": "registry entry stale",
                                  "operator": "b.dimovski"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static ApplicantView applicant() {
        return new ApplicantView(
                "app-1240",
                "WEB",
                new ApplicantView.Applicant(
                        "Sofia Ruiz",
                        "1991-05-20",
                        List.of("GB", "US"),
                        "GB"),
                new ApplicantView.Product("CREDIT_CARD_STANDARD"));
    }
}
