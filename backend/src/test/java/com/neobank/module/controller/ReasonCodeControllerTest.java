package com.neobank.module.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ReasonCodeCountDto;
import com.neobank.module.service.ReasonCodeService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReasonCodeController.class)
class ReasonCodeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReasonCodeService reasonCodes;

    @Test
    void returnsReasonCodeCountsForTheWindow() throws Exception {
        when(reasonCodes.countReasonCodes(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-14")))
                .thenReturn(List.of(
                        new ReasonCodeCountDto("POL_SAMPLED_FOR_REVIEW", 26, "review"),
                        new ReasonCodeCountDto("POL_TAX_RESIDENCY_UNSUPPORTED", 4, "rejection")));

        mvc.perform(get("/reason-codes")
                        .queryParam("from", "2026-07-01")
                        .queryParam("to", "2026-07-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("POL_SAMPLED_FOR_REVIEW"))
                .andExpect(jsonPath("$[0].count").value(26))
                .andExpect(jsonPath("$[0].kind").value("review"));

        verify(reasonCodes).countReasonCodes(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"));
    }

    @Test
    void rejectsWhenToIsBeforeFrom() throws Exception {
        mvc.perform(get("/reason-codes")
                        .queryParam("from", "2026-07-14")
                        .queryParam("to", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("to must be on or after from"));

        verifyNoInteractions(reasonCodes);
    }

    @Test
    void returnsAnEmptyArrayForAnEmptyWindow() throws Exception {
        when(reasonCodes.countReasonCodes(
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-08-01")))
                .thenReturn(List.of());

        mvc.perform(get("/reason-codes")
                        .queryParam("from", "2026-08-01")
                        .queryParam("to", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
