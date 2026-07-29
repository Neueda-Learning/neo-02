package com.neobank.module.service;

import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the locked database transactions for referral operator actions. */
@Service
public class ReferralCaseWriter {

    private final PolicyRecordRepository records;

    public ReferralCaseWriter(PolicyRecordRepository records) {
        this.records = records;
    }

    @Transactional
    public PolicyRecord claim(String applicationId, String operator) {
        PolicyRecord record = lockOpenReferral(applicationId);
        if (record.getClaimedBy() == null) {
            record.claim(operator, Instant.now());
        } else if (!record.getClaimedBy().equals(operator)) {
            throw new ReferralConflictException("Case " + applicationId
                    + " is already claimed by " + record.getClaimedBy());
        }
        return records.saveAndFlush(record);
    }

    @Transactional
    public PolicyRecord release(String applicationId, String operator) {
        PolicyRecord record = lockOpenReferral(applicationId);
        if (record.getClaimedBy() == null) {
            return record;
        }
        if (!record.getClaimedBy().equals(operator)) {
            throw new ReferralConflictException("Case " + applicationId
                    + " is claimed by " + record.getClaimedBy());
        }
        record.release();
        return records.saveAndFlush(record);
    }

    @Transactional
    public ManualWriteResult decide(
            String applicationId, PolicyOutcome outcome, String reason, String operator) {
        PolicyRecord record = records.findForUpdate(applicationId)
                .orElseThrow(() -> new CaseNotFoundException(applicationId));

        if (record.hasHumanDecision()) {
            boolean sameRequest = record.getOutcome() == outcome
                    && operator.equals(record.getDecidedBy())
                    && reason.equals(record.getDecisionReason());
            if (sameRequest) {
                return new ManualWriteResult(record, false);
            }
            throw new ReferralConflictException(
                    "Case " + applicationId + " already has a human decision");
        }
        requireOpenReferral(record);
        if (record.getClaimedBy() != null && !record.getClaimedBy().equals(operator)) {
            throw new ReferralConflictException("Case " + applicationId
                    + " is claimed by " + record.getClaimedBy());
        }

        record.completeManualDecision(outcome, reason, operator, Instant.now());
        return new ManualWriteResult(records.saveAndFlush(record), true);
    }

    private PolicyRecord lockOpenReferral(String applicationId) {
        PolicyRecord record = records.findForUpdate(applicationId)
                .orElseThrow(() -> new CaseNotFoundException(applicationId));
        requireOpenReferral(record);
        return record;
    }

    private void requireOpenReferral(PolicyRecord record) {
        if (record.getOutcome() != PolicyOutcome.REFERRED || record.hasHumanDecision()) {
            throw new IllegalArgumentException(
                    "Case " + record.getApplicationId() + " is not an open REFERRED case");
        }
    }

    public record ManualWriteResult(PolicyRecord record, boolean changed) {
    }
}
