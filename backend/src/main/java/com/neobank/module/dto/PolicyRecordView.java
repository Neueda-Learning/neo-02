package com.neobank.module.dto;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import java.time.Instant;

/** Shared row shape for the intake board and UC01 case search. */
public record PolicyRecordView(
        String applicationId,
        String status,
        String reference,
        Instant createdAt,
        Instant submittedAt,
        String outcome,
        boolean sampled,
        int reasonCount) {

    public static PolicyRecordView of(PolicyRecord row) {
        String outcome = row.getOutcome() != null
                ? row.getOutcome().name()
                : row.getMachineOutcome() == null ? null : row.getMachineOutcome().name();
        return new PolicyRecordView(
                row.getApplicationId(),
                row.getOutcome() == null ? row.getProcessingStatus() : row.getOutcome().name(),
                row.getReference(),
                row.getSubmittedAt(),
                row.getSubmittedAt(),
                outcome,
                row.getRuleResults().stream()
                        .filter(rule -> "sampling".equals(rule.ruleName()))
                        .map(RuleResult::sampled)
                        .anyMatch(Boolean.TRUE::equals),
                (int) row.getRuleResults().stream()
                        .flatMap(rule -> rule.reasonCodes().stream())
                        .distinct()
                        .count());
    }
}
