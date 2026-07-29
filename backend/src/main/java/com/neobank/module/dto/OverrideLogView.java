package com.neobank.module.dto;

import com.neobank.module.model.OverrideLog;
import java.time.Instant;

/** One immutable entry in the operator-facing case history. */
public record OverrideLogView(
        long id,
        String oldOutcome,
        String newOutcome,
        String reason,
        String operator,
        Instant overriddenAt) {

    public static OverrideLogView of(OverrideLog log) {
        return new OverrideLogView(
                log.getId(),
                log.getOldOutcome().name(),
                log.getNewOutcome().name(),
                log.getReason(),
                log.getOperator(),
                log.getOverriddenAt());
    }
}
