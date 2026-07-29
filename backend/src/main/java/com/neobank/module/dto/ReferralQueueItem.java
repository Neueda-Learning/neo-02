package com.neobank.module.dto;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.service.PolicyRuleEngine;
import java.time.Instant;

/** One open REFERRED case shown on the operator queue. */
public record ReferralQueueItem(
        String applicationId,
        String reference,
        String machineOutcome,
        String referralCause,
        String claimedBy,
        Instant claimedAt,
        Instant submittedAt) {

    public static ReferralQueueItem of(PolicyRecord record) {
        boolean sampled = record.getRuleResults().stream()
                .anyMatch(rule -> "sampling".equals(rule.ruleName())
                        && Boolean.TRUE.equals(rule.sampled()));
        boolean registryOutage = record.getRuleResults().stream()
                .flatMap(rule -> rule.reasonCodes().stream())
                .anyMatch(PolicyRuleEngine.REGISTRY_UNAVAILABLE::equals);
        String cause = sampled ? "sampled" : registryOutage ? "registry-outage" : "operator";
        return new ReferralQueueItem(
                record.getApplicationId(),
                record.getReference(),
                record.getMachineOutcome() == null ? null : record.getMachineOutcome().name(),
                cause,
                record.getClaimedBy(),
                record.getClaimedAt(),
                record.getSubmittedAt());
    }
}
