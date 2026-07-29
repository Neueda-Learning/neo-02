package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ReferralQueueItem;
import com.neobank.module.service.ReferralQueueService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReferralQueueController.class)
class ReferralQueueControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReferralQueueService referrals;

    @Test
    void returnsTheOpenReferralQueueForTheUi() throws Exception {
        when(referrals.findOpenReferrals()).thenReturn(List.of(new ReferralQueueItem(
                "app-1287", "pol-000287", "APPROVED", "sampled", null, null,
                Instant.parse("2026-07-15T08:00:00Z"))));

        mvc.perform(get("/api/v1/referrals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("app-1287"))
                .andExpect(jsonPath("$[0].referralCause").value("sampled"))
                .andExpect(jsonPath("$[0].machineOutcome").value("APPROVED"));
    }
}
