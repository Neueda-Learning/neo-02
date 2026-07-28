package com.neobank.module.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.dto.PolicyConfigVersionDto;
import com.neobank.module.model.PolicyConfig;
import com.neobank.module.service.PolicyConfigService;
import com.neobank.module.service.PolicyConfigValidationException;
import com.neobank.module.service.PolicyConfigValidationException.Violation;

/** UC07 · Edit Policy Config — {@code POST /config} contract.
 *  UC08 · View Config History — {@code GET /config/versions} contract. */
@WebMvcTest(PolicyConfigController.class)
class PolicyConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PolicyConfigService configs;

    // ── UC07 ──────────────────────────────────────────────────────────────────

    @Test
    void publishingAFullDocumentReturns201WithTheNewVersion() throws Exception {
        when(configs.createVersion(any(PolicyConfigRequest.class))).thenReturn(2);

        mvc.perform(post("/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supportedResidencies": ["GB","IE","PL","DE","FR","ES","NL"],
                                  "excludedResidencies": ["US"],
                                  "restrictionList": [
                                    {"fullName":"Victor Sable","dateOfBirth":"1978-03-02",
                                     "reason":"prior fraud loss"}
                                  ],
                                  "sampleEvery": 7
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        verify(configs).createVersion(any(PolicyConfigRequest.class));
    }

    @Test
    void sampleEveryBelowOneIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supportedResidencies": ["GB"],
                                  "excludedResidencies": ["US"],
                                  "restrictionList": [],
                                  "sampleEvery": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sampleEvery"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        verifyNoInteractions(configs);
    }

    @Test
    void businessRuleViolationsFromTheServiceComeBackAs400() throws Exception {
        when(configs.createVersion(any(PolicyConfigRequest.class)))
                .thenThrow(new PolicyConfigValidationException(
                        List.of(new Violation("excludedResidencies",
                                "residencies [GB] cannot also appear on supportedResidencies"))));

        mvc.perform(post("/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supportedResidencies": ["GB"],
                                  "excludedResidencies": ["GB"],
                                  "restrictionList": [],
                                  "sampleEvery": 7
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("excludedResidencies"))
                .andExpect(jsonPath("$.errors[0].message").value(
                        org.hamcrest.Matchers.containsString("cannot also appear")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("cannot also appear")));
    }

    // ── UC08 ──────────────────────────────────────────────────────────────────

    @Test
    void getVersionsReturns200WithAllVersionsOldestFirst() throws Exception {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-07-01T00:00:00Z");
        List<PolicyConfigVersionDto> stub = List.of(
                new PolicyConfigVersionDto(1, List.of("GB", "IE"), List.of("US"),
                        List.of(new PolicyConfig.RestrictionEntry("Victor Sable", "1978-03-02", "prior fraud loss")),
                        7, t1, false),
                new PolicyConfigVersionDto(2, List.of("GB", "IE", "PL"), List.of("US"),
                        List.of(new PolicyConfig.RestrictionEntry("Victor Sable", "1978-03-02", "prior fraud loss")),
                        5, t2, true));
        when(configs.versions()).thenReturn(stub);

        mvc.perform(get("/config/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].isCurrent").value(false))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].isCurrent").value(true))
                .andExpect(jsonPath("$[1].sampleEvery").value(5));
    }

    @Test
    void getVersionsIsIdempotentSameRequestTwiceSameResult() throws Exception {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        List<PolicyConfigVersionDto> stub = List.of(
                new PolicyConfigVersionDto(1, List.of("GB"), List.of("US"), List.of(), 7, t1, true));
        when(configs.versions()).thenReturn(stub);

        mvc.perform(get("/config/versions")).andExpect(status().isOk());
        mvc.perform(get("/config/versions")).andExpect(status().isOk());

        verify(configs, org.mockito.Mockito.times(2)).versions();
    }
}
