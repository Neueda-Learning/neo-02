package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.ReferralQueueItem;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC04 operator workflow: list, claim, release and decide referred cases. */
@Service
public class ReferralQueueService {

    private final PolicyRecordRepository records;
    private final ReferralCaseWriter writer;
    private final OrchestratorClient orchestrator;

    public ReferralQueueService(
            PolicyRecordRepository records,
            ReferralCaseWriter writer,
            OrchestratorClient orchestrator) {
        this.records = records;
        this.writer = writer;
        this.orchestrator = orchestrator;
    }

    @Transactional(readOnly = true)
    public List<ReferralQueueItem> findOpenReferrals() {
        return records.findOpenReferrals(PolicyOutcome.REFERRED, PageRequest.of(0, 10)).stream()
                .map(ReferralQueueItem::of)
                .toList();
    }

    public CaseDetailView claim(String applicationId, String operator) {
        return CaseDetailView.of(writer.claim(applicationId, clean(operator, "operator")));
    }

    public CaseDetailView release(String applicationId, String operator) {
        return CaseDetailView.of(writer.release(applicationId, clean(operator, "operator")));
    }

    public CaseDetailView decide(
            String applicationId, PolicyOutcome outcome, String reason, String operator) {
        if (outcome != PolicyOutcome.APPROVED && outcome != PolicyOutcome.REJECTED) {
            throw new IllegalArgumentException("outcome must be APPROVED or REJECTED");
        }
        String cleanReason = clean(reason, "reason");
        String cleanOperator = clean(operator, "operator");
        ReferralCaseWriter.ManualWriteResult result =
                writer.decide(applicationId, outcome, cleanReason, cleanOperator);
        // The callback is a PUT, so replaying it is safe. Always resend an exact idempotent
        // decision request so a transient failure during the first delivery can recover.
        orchestrator.manualPolicyDecision(applicationId, outcome, cleanReason);
        return CaseDetailView.of(result.record());
    }

    private static String clean(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is mandatory");
        }
        return value.trim();
    }
}
