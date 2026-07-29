package com.neobank.module.dto;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import java.time.Instant;
import java.util.List;

/** UC02 response: the stored decision and all four rule sections. */
public record CaseDetailView(
        String applicationId,
        String outcome,
        String machineOutcome,
        String reference,
        Integer policyConfigVersion,
        List<RuleResult> ruleResults,
        String claimedBy,
        Instant claimedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        Instant submittedAt) {

    public static CaseDetailView of(PolicyRecord record) {
        return new CaseDetailView(
                record.getApplicationId(),
                record.getOutcome() == null ? null : record.getOutcome().name(),
                record.getMachineOutcome() == null ? null : record.getMachineOutcome().name(),
                record.getReference(),
                record.getPolicyConfigVersion(),
                record.getRuleResults(),
                record.getClaimedBy(),
                record.getClaimedAt(),
                record.getDecidedBy(),
                record.getDecidedAt(),
                record.getDecisionReason(),
                record.getSubmittedAt());
    }
}
