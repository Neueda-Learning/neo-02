package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.PolicyRecordRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OverrideCaseWriterTest {

    @Autowired
    private OverrideCaseWriter writer;

    @Autowired
    private PolicyRecordRepository records;

    @Autowired
    private OverrideLogRepository overrides;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearCases() {
        jdbc.update("DELETE FROM override_log");
        jdbc.update("DELETE FROM policy_record");
    }

    @Test
    void updatesTheEffectiveOutcomeAndAppendsAuditWithoutTouchingMachineEvidence() {
        decidedCase("OVERRIDE-1", PolicyOutcome.REJECTED);

        OverrideCaseWriter.OverrideResult result = writer.apply(
                "OVERRIDE-1",
                PolicyOutcome.APPROVED,
                "registry entry stale",
                "b.dimovski");
        entityManager.clear();

        PolicyRecord updated = records.findById("OVERRIDE-1").orElseThrow();
        assertThat(result.changed()).isTrue();
        assertThat(updated.getOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(updated.getMachineOutcome()).isEqualTo(PolicyOutcome.REJECTED);
        assertThat(updated.getRuleResults()).hasSize(1);
        assertThat(updated.getDecidedBy()).isEqualTo("b.dimovski");
        assertThat(updated.getDecisionReason()).isEqualTo("registry entry stale");
        assertThat(updated.getDecidedAt()).isNotNull();

        List<OverrideLog> history =
                overrides.findByApplicationIdOrderByOverriddenAtAscIdAsc("OVERRIDE-1");
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getOldOutcome()).isEqualTo(PolicyOutcome.REJECTED);
        assertThat(history.getFirst().getNewOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(history.getFirst().getReason()).isEqualTo("registry entry stale");
        assertThat(history.getFirst().getOperator()).isEqualTo("b.dimovski");
    }

    @Test
    void exactRetryIsAnIdempotentNoOp() {
        decidedCase("OVERRIDE-RETRY", PolicyOutcome.REJECTED);
        writer.apply(
                "OVERRIDE-RETRY",
                PolicyOutcome.APPROVED,
                "registry entry stale",
                "b.dimovski");

        OverrideCaseWriter.OverrideResult retry = writer.apply(
                "OVERRIDE-RETRY",
                PolicyOutcome.APPROVED,
                "registry entry stale",
                "b.dimovski");

        assertThat(retry.changed()).isFalse();
        assertThat(overrides.findByApplicationIdOrderByOverriddenAtAscIdAsc("OVERRIDE-RETRY"))
                .hasSize(1);
    }

    @Test
    void sameOutcomeWithDifferentEvidenceIsAConflict() {
        decidedCase("OVERRIDE-CONFLICT", PolicyOutcome.REJECTED);
        writer.apply(
                "OVERRIDE-CONFLICT",
                PolicyOutcome.APPROVED,
                "first reason",
                "first.operator");

        assertThatThrownBy(() -> writer.apply(
                "OVERRIDE-CONFLICT",
                PolicyOutcome.APPROVED,
                "different reason",
                "second.operator"))
                .isInstanceOf(CaseConflictException.class)
                .hasMessageContaining("different decision");
        assertThat(overrides.findByApplicationIdOrderByOverriddenAtAscIdAsc("OVERRIDE-CONFLICT"))
                .hasSize(1);
    }

    @Test
    void inProgressCaseCannotBeOverridden() {
        records.saveAndFlush(new PolicyRecord("OVERRIDE-PENDING", "pol-override-pending"));

        assertThatThrownBy(() -> writer.apply(
                "OVERRIDE-PENDING",
                PolicyOutcome.APPROVED,
                "too early",
                "b.dimovski"))
                .isInstanceOf(CaseConflictException.class)
                .hasMessageContaining("still in progress");
        assertThat(overrides.count()).isZero();
    }

    @Test
    void unknownCaseReturnsNotFoundWithoutAudit() {
        assertThatThrownBy(() -> writer.apply(
                "MISSING",
                PolicyOutcome.APPROVED,
                "not found",
                "b.dimovski"))
                .isInstanceOf(CaseNotFoundException.class);
        assertThat(overrides.count()).isZero();
    }

    private void decidedCase(String applicationId, PolicyOutcome machineOutcome) {
        PolicyRecord record = new PolicyRecord(
                applicationId,
                "pol-" + applicationId.toLowerCase());
        record.completeDecision(new DecisionResult(
                machineOutcome,
                machineOutcome,
                List.of(RuleResult.existingProduct(
                        machineOutcome == PolicyOutcome.APPROVED,
                        true,
                        machineOutcome == PolicyOutcome.REJECTED
                                ? List.of("POL_EXISTING_PRODUCT_HELD")
                                : List.of()))));
        records.saveAndFlush(record);
    }
}
