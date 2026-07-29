package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.ReferralQueueItem;
import com.neobank.module.model.RuleResult;
import com.neobank.module.service.ApplicantUnavailableException;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseDetailService;
import com.neobank.module.service.CaseNotFoundException;
import com.neobank.module.service.ReferralConflictException;
import com.neobank.module.service.ReferralQueueService;
import java.time.Instant;
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

    @MockBean
    private ReferralQueueService referrals;

    @Test
    void returnsTheStoredDecisionAndFourRuleSections() throws Exception {
        when(cases.find("app-1234")).thenReturn(new CaseDetailView(
                "app-1234",
                "APPROVED",
                "APPROVED",
                "pol-000214",
                1,
                List.of(
                        RuleResult.existingProduct(true, true, List.of()),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(false, 1, List.of("POL_ALL_CHECKS_PASSED"))),
                null, null, null, Instant.parse("2026-07-15T08:00:00Z"), null,
                Instant.parse("2026-07-15T07:59:00Z")));

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
    void listsOnlyTheOpenReferralRowsReturnedByTheService() throws Exception {
        when(referrals.findOpenReferrals()).thenReturn(List.of(new ReferralQueueItem(
                "app-1287", "pol-000287", "APPROVED", "sampled", null, null,
                Instant.parse("2026-07-15T08:00:00Z"))));

        mvc.perform(get("/cases?outcome=REFERRED&unclaimed-first=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("app-1287"))
                .andExpect(jsonPath("$[0].referralCause").value("sampled"))
                .andExpect(jsonPath("$[0].machineOutcome").value("APPROVED"));
    }

    @Test
    void claimConflictIs409() throws Exception {
        when(referrals.claim("app-1287", "other.operator"))
                .thenThrow(new ReferralConflictException("already claimed"));

        mvc.perform(post("/cases/app-1287/claim")
                        .contentType("application/json")
                        .content("{\"operator\":\"other.operator\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("already claimed"));
    }

    @Test
    void decisionRequiresReasonAndOperator() throws Exception {
        mvc.perform(post("/cases/app-1287/decision")
                        .contentType("application/json")
                        .content("{\"outcome\":\"APPROVED\",\"reason\":\"\",\"operator\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void referredIsNotAValidHumanOutcome() throws Exception {
        when(referrals.decide("app-1287", com.neobank.module.model.PolicyOutcome.REFERRED,
                "wait", "s.chen"))
                .thenThrow(new IllegalArgumentException("outcome must be APPROVED or REJECTED"));

        mvc.perform(post("/cases/app-1287/decision")
                        .contentType("application/json")
                        .content("{\"outcome\":\"REFERRED\",\"reason\":\"wait\",\"operator\":\"s.chen\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("outcome must be APPROVED or REJECTED"));
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
