package com.neobank.module.dto;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import java.time.Instant;
import java.util.List;

/** Stored machine evidence plus the current human decision and append-only override history. */
public record CaseDetailView(
        String outcome,
        String machineOutcome,
        String reference,
        Integer policyConfigVersion,
        List<RuleResult> ruleResults,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        List<OverrideLogView> overrides,
        long lockVersion) {

    /** Keeps the original UC02 construction shape available to focused controller tests. */
    public CaseDetailView(
            String outcome,
            String machineOutcome,
            String reference,
            Integer policyConfigVersion,
            List<RuleResult> ruleResults) {
        this(
                outcome,
                machineOutcome,
                reference,
                policyConfigVersion,
                ruleResults,
                null,
                null,
                null,
                List.of(),
                0);
    }

    /** Convenience shape for focused tests that do not exercise optimistic concurrency. */
    public CaseDetailView(
            String outcome,
            String machineOutcome,
            String reference,
            Integer policyConfigVersion,
            List<RuleResult> ruleResults,
            String decidedBy,
            Instant decidedAt,
            String decisionReason,
            List<OverrideLogView> overrides) {
        this(
                outcome,
                machineOutcome,
                reference,
                policyConfigVersion,
                ruleResults,
                decidedBy,
                decidedAt,
                decisionReason,
                overrides,
                0);
    }

    public CaseDetailView {
        ruleResults = List.copyOf(ruleResults);
        overrides = List.copyOf(overrides);
    }

    public static CaseDetailView of(PolicyRecord record) {
        return of(record, List.of());
    }

    public static CaseDetailView of(
            PolicyRecord record,
            List<OverrideLogView> overrides) {
        return new CaseDetailView(
                record.getOutcome() == null ? null : record.getOutcome().name(),
                record.getMachineOutcome() == null ? null : record.getMachineOutcome().name(),
                record.getReference(),
                record.getPolicyConfigVersion(),
                record.getRuleResults(),
                record.getDecidedBy(),
                record.getDecidedAt(),
                record.getDecisionReason(),
                overrides,
                record.getLockVersion());
    }
}
