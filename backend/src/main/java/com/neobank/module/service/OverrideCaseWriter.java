package com.neobank.module.service;

import com.neobank.module.model.OverrideLog;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.repository.PolicyRecordRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the atomic UC06 update-and-audit transaction. */
@Service
public class OverrideCaseWriter {

    private final PolicyRecordRepository records;
    private final OverrideLogRepository overrides;

    public OverrideCaseWriter(
            PolicyRecordRepository records,
            OverrideLogRepository overrides) {
        this.records = records;
        this.overrides = overrides;
    }

    @Transactional
    public OverrideResult apply(
            String applicationId,
            PolicyOutcome newOutcome,
            String reason,
            String operator) {
        PolicyRecord record = records.findForUpdate(applicationId)
                .orElseThrow(() -> new CaseNotFoundException(applicationId));
        if (!record.isDecided() || record.getOutcome() == null) {
            throw new CaseConflictException(
                    "Policy case %s is still in progress and cannot be overridden"
                            .formatted(applicationId));
        }

        if (record.getOutcome() == newOutcome) {
            return duplicateOrConflict(applicationId, newOutcome, reason, operator);
        }

        PolicyOutcome oldOutcome = record.getOutcome();
        Instant overriddenAt = Instant.now();
        record.overrideOutcome(newOutcome, operator, reason, overriddenAt);
        overrides.save(new OverrideLog(
                applicationId,
                oldOutcome,
                newOutcome,
                reason,
                operator,
                overriddenAt));
        records.saveAndFlush(record);
        overrides.flush();
        return new OverrideResult(true, newOutcome, reason, operator);
    }

    private OverrideResult duplicateOrConflict(
            String applicationId,
            PolicyOutcome newOutcome,
            String reason,
            String operator) {
        boolean exactDuplicate = overrides
                .findFirstByApplicationIdOrderByOverriddenAtDescIdDesc(applicationId)
                .filter(log -> log.getNewOutcome() == newOutcome)
                .filter(log -> Objects.equals(log.getReason(), reason))
                .filter(log -> Objects.equals(log.getOperator(), operator))
                .isPresent();
        if (exactDuplicate) {
            return new OverrideResult(false, newOutcome, reason, operator);
        }
        throw new CaseConflictException(
                "Policy case %s already has outcome %s from a different decision"
                        .formatted(applicationId, newOutcome));
    }

    public record OverrideResult(
            boolean changed,
            PolicyOutcome outcome,
            String reason,
            String operator) {
    }
}
