package com.neobank.module.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.PolicyOutcome;
import org.junit.jupiter.api.Test;

class ManualDecisionReporterTest {

    private final OrchestratorClient orchestrator = mock(OrchestratorClient.class);
    private final ManualDecisionReporter reporter = new ManualDecisionReporter(orchestrator);

    @Test
    void approvedOverrideUsesAcceptedAndTheLockedManualReasonCode() {
        reporter.report("app-1", PolicyOutcome.APPROVED, "stale registry", "operator.one");

        verify(orchestrator).applicationStatusUpdate(
                "app-1",
                Decision.ACCEPTED,
                "POL_MANUAL_APPROVED: stale registry (operator: operator.one)");
    }

    @Test
    void rejectedOverrideUsesRejectedAndTheLockedManualReasonCode() {
        reporter.report("app-2", PolicyOutcome.REJECTED, "policy correction", "operator.two");

        verify(orchestrator).applicationStatusUpdate(
                "app-2",
                Decision.REJECTED,
                "POL_MANUAL_DECLINED: policy correction (operator: operator.two)");
    }

    @Test
    void referredOverrideUsesTheExistingReferredStatusWithoutInventingAReasonCode() {
        reporter.report("app-3", PolicyOutcome.REFERRED, "needs specialist", "operator.three");

        verify(orchestrator).applicationStatusUpdate(
                "app-3",
                Decision.REFERRED,
                "Manual referral: needs specialist (operator: operator.three)");
    }
}
