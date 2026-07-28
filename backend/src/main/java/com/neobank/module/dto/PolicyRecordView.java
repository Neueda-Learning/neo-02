package com.neobank.module.dto;

import com.neobank.module.model.PolicyRecord;
import java.time.Instant;

public record PolicyRecordView(
        String applicationId,
        String status,
        String reference,
        Instant createdAt) {

    public static PolicyRecordView of(PolicyRecord row) {
        return new PolicyRecordView(
                row.getApplicationId(),
                row.getOutcome() == null ? row.getProcessingStatus() : row.getOutcome().name(),
                row.getReference(),
                row.getSubmittedAt());
    }
}
