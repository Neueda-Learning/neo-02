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
            String operator,
            long expectedVersion) {
        PolicyRecord record = records.findForUpdate(applicationId)
                .orElseThrow(() -> new CaseNotFoundException(applicationId));
        if (!record.isDecided() || record.getOutcome() == null) {
            throw new CaseConflictException(
                    "Policy case %s is still in progress and cannot be overridden"
                            .formatted(applicationId));
        }
        if (record.getLockVersion() != expectedVersion) {
            return duplicateOrConflict(
                    record,
                    newOutcome,
                    reason,
                    operator,
                    "Policy case %s changed after version %d; reload before overriding"
                            .formatted(applicationId, expectedVersion));
        }
        if (record.getOutcome() == PolicyOutcome.REFERRED) {
            return duplicateOrConflict(
                    record,
                    newOutcome,
                    reason,
                    operator,
                    "Policy case %s is referred and must be decided through the claimed queue"
                            .formatted(applicationId));
        }
        if (record.getOutcome() == newOutcome) {
            return duplicateOrConflict(
                    record,
                    newOutcome,
                    reason,
                    operator,
                    "Policy case %s already has outcome %s from a different decision"
                            .formatted(applicationId, newOutcome));
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
            PolicyRecord record,
            PolicyOutcome newOutcome,
            String reason,
            String operator,
            String conflictMessage) {
        String applicationId = record.getApplicationId();
        boolean exactDuplicate = overrides
                .findFirstByApplicationIdOrderByOverriddenAtDescIdDesc(applicationId)
                .filter(log -> record.getOutcome() == newOutcome)
                .filter(log -> Objects.equals(record.getDecisionReason(), reason))
                .filter(log -> Objects.equals(record.getDecidedBy(), operator))
                .filter(log -> log.getNewOutcome() == newOutcome)
                .filter(log -> Objects.equals(log.getReason(), reason))
                .filter(log -> Objects.equals(log.getOperator(), operator))
                .isPresent();
        if (exactDuplicate) {
            return new OverrideResult(false, newOutcome, reason, operator);
        }
        throw new CaseConflictException(conflictMessage);
    }

    public record OverrideResult(
            boolean changed,
            PolicyOutcome outcome,
            String reason,
            String operator) {
    }
}
