package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ReasonCodeCountView;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
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
    void countsReasonsRanksDescendingAndSplitsKinds() {
        when(records.findBySubmittedAtBetweenInclusive(any(), any())).thenReturn(List.of(
                row("A", "{\"reasons\":[{\"code\":\"POL_SAMPLED_FOR_REVIEW\"}]}"),
                row("B", "{\"reasons\":[{\"code\":\"POL_TAX_RESIDENCY_UNSUPPORTED\"},{\"code\":\"POL_EXISTING_PRODUCT_HELD\"}]}"),
                row("C", "{\"reasons\":[{\"code\":\"POL_TAX_RESIDENCY_UNSUPPORTED\"}]}")
        ));

        List<ReasonCodeCountView> result = service.countReasonCodes(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"));

        assertThat(result).containsExactly(
                new ReasonCodeCountView("POL_TAX_RESIDENCY_UNSUPPORTED", 2, "rejection"),
                new ReasonCodeCountView("POL_EXISTING_PRODUCT_HELD", 1, "rejection"),
                new ReasonCodeCountView("POL_SAMPLED_FOR_REVIEW", 1, "review")
        );
    }

    @Test
    void returnsEmptyForEmptyWindow() {
        when(records.findBySubmittedAtBetweenInclusive(any(), any())).thenReturn(List.of());

        List<ReasonCodeCountView> result = service.countReasonCodes(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"));

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyAndSkipsRepositoryWhenToIsBeforeFrom() {
        List<ReasonCodeCountView> result = service.countReasonCodes(
                LocalDate.parse("2026-07-14"),
                LocalDate.parse("2026-07-01"));

        assertThat(result).isEmpty();
        verifyNoInteractions(records);
    }

    private static PolicyRecord row(String id, String ruleResults) {
        PolicyRecord row = new PolicyRecord(id, "ref-" + id);
        setField(row, "ruleResults", ruleResults);
        return row;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not seed test field " + fieldName, e);
        }
    }
}
