package com.neobank.module.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.service.PolicyConfigService;
import com.neobank.module.service.PolicyConfigValidationException;
import com.neobank.module.service.PolicyConfigValidationException.Violation;

/** UC07 · Edit Policy Config — the {@code POST /config} contract. */
@WebMvcTest(PolicyConfigController.class)
class PolicyConfigControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PolicyConfigService configs;

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
}
