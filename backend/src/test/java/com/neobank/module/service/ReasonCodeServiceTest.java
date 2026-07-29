package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ReasonCodeCountDto;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.PolicyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReasonCodeServiceTest {

    private PolicyRecordRepository records;
    private ReasonCodeService service;

    @BeforeEach
    void setUp() {
        records = mock(PolicyRecordRepository.class);
        service = new ReasonCodeService(records);
    }

    @Test
    void countsAndRanksReasonCodesForUc05CheckpointWindow() {
        when(records.findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-15T00:00:00Z")))
                .thenReturn(seedRows());

        List<ReasonCodeCountDto> result = service.countReasonCodes(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"));

        assertThat(result).extracting(ReasonCodeCountDto::code, ReasonCodeCountDto::count, ReasonCodeCountDto::kind)
                .containsExactly(
                        tuple("POL_SAMPLED_FOR_REVIEW", 26L, "review"),
                        tuple("POL_TAX_RESIDENCY_UNSUPPORTED", 4L, "rejection"),
                        tuple("POL_EXISTING_PRODUCT_HELD", 3L, "rejection"),
                        tuple("POL_TAX_RESIDENCY_EXCLUDED", 2L, "rejection"),
                        tuple("POL_CUSTOMER_BLOCKED", 1L, "rejection"));
    }

    @Test
    void returnsEmptyListForAnEmptyWindow() {
        when(records.findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z")))
                .thenReturn(List.of());

        List<ReasonCodeCountDto> result = service.countReasonCodes(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-01"));

        assertThat(result).isEmpty();
    }

    private List<PolicyRecord> seedRows() {
        List<PolicyRecord> rows = new ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            List<List<String>> codes = new ArrayList<>();
            codes.add(List.of(PolicyRuleEngine.SAMPLED_FOR_REVIEW));
            if (i <= 4) {
                codes.add(List.of(PolicyRuleEngine.TAX_RESIDENCY_UNSUPPORTED));
            }
            if (i <= 3) {
                codes.add(List.of(PolicyRuleEngine.EXISTING_PRODUCT_HELD));
            }
            if (i <= 2) {
                codes.add(List.of(PolicyRuleEngine.TAX_RESIDENCY_EXCLUDED));
            }
            if (i == 1) {
                codes.add(List.of(PolicyRuleEngine.CUSTOMER_BLOCKED));
            }
            rows.add(decidedRow("UC05-" + i, codes));
        }
        return rows;
    }

    private PolicyRecord decidedRow(String applicationId, List<List<String>> reasonCodeGroups) {
        List<RuleResult> results = reasonCodeGroups.stream()
                .map(codes -> new RuleResult("rule", false, codes, null, null, null, null))
                .toList();
        PolicyRecord row = new PolicyRecord(applicationId, "ref-" + applicationId);
        row.completeDecision(new DecisionResult(
                PolicyOutcome.REFERRED,
                PolicyOutcome.REJECTED,
                results));
        return row;
    }
}
