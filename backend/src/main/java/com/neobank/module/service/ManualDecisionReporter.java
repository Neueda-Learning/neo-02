package com.neobank.module.service;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.PolicyOutcome;
import org.springframework.stereotype.Service;

/**
 * Maps a human policy outcome onto the fixed orchestrator status-update contract.
 *
 * <p>The wire accepts ACCEPTED, REJECTED, or REFERRED. Human provenance and the locked policy
 * reason code therefore travel in the comment, without changing the three-field callback body.</p>
 */
@Service
public class ManualDecisionReporter {

    private final OrchestratorClient orchestrator;

    public ManualDecisionReporter(OrchestratorClient orchestrator) {
        this.orchestrator = orchestrator;
    }

    public void report(
            String applicationId,
            PolicyOutcome outcome,
            String reason,
            String operator) {
        orchestrator.applicationStatusUpdate(
                applicationId,
                status(outcome),
                comment(outcome, reason, operator));
    }

    private Decision status(PolicyOutcome outcome) {
        return switch (outcome) {
            case APPROVED -> Decision.ACCEPTED;
            case REJECTED -> Decision.REJECTED;
            case REFERRED -> Decision.REFERRED;
        };
    }

    private String comment(PolicyOutcome outcome, String reason, String operator) {
        String prefix = switch (outcome) {
            case APPROVED -> "POL_MANUAL_APPROVED";
            case REJECTED -> "POL_MANUAL_DECLINED";
            case REFERRED -> "Manual referral";
        };
        return "%s: %s (operator: %s)".formatted(prefix, reason, operator);
    }
}
