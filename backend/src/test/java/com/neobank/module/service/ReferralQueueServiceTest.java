package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ReferralQueueServiceTest {

    private PolicyRecordRepository records;
    private ReferralCaseWriter writer;
    private OrchestratorClient orchestrator;
    private ReferralQueueService service;

    @BeforeEach
    void setUp() {
        records = mock(PolicyRecordRepository.class);
        writer = mock(ReferralCaseWriter.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new ReferralQueueService(records, writer, orchestrator);
    }

    @Test
    void queueIsCappedAtTenAndMapsReferralCauses() {
        PolicyRecord sampled = referred("app-1287", true, false);
        PolicyRecord outage = referred("app-registry", false, true);
        when(records.findOpenReferrals(
                ArgumentMatchers.eq(PolicyOutcome.REFERRED), ArgumentMatchers.any()))
                .thenReturn(List.of(sampled, outage));

        var queue = service.findOpenReferrals();

        assertThat(queue).extracting(item -> item.referralCause())
                .containsExactly("sampled", "registry-outage");
    }

    @Test
    void oneChangedDecisionProducesExactlyOneManualCallback() {
        PolicyRecord record = referred("app-1287", true, false);
        record.completeManualDecision(
                PolicyOutcome.APPROVED, "machine confirmed", "s.chen", java.time.Instant.now());
        when(writer.decide("app-1287", PolicyOutcome.APPROVED,
                "machine confirmed", "s.chen"))
                .thenReturn(new ReferralCaseWriter.ManualWriteResult(record, true));

        var result = service.decide(
                "app-1287", PolicyOutcome.APPROVED, " machine confirmed ", " s.chen ");

        assertThat(result.decidedBy()).isEqualTo("s.chen");
        assertThat(result.machineOutcome()).isEqualTo("APPROVED");
        verify(orchestrator).manualPolicyDecision(
                "app-1287", PolicyOutcome.APPROVED, "machine confirmed");
    }

    @Test
    void idempotentReplayDoesNotSendAnotherCallback() {
        PolicyRecord record = referred("app-1287", true, false);
        record.completeManualDecision(
                PolicyOutcome.APPROVED, "machine confirmed", "s.chen", java.time.Instant.now());
        when(writer.decide("app-1287", PolicyOutcome.APPROVED,
                "machine confirmed", "s.chen"))
                .thenReturn(new ReferralCaseWriter.ManualWriteResult(record, false));

        service.decide("app-1287", PolicyOutcome.APPROVED, "machine confirmed", "s.chen");

        verify(orchestrator, never()).manualPolicyDecision(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void refusesAnyHumanOutcomeOtherThanApprovedOrRejected() {
        assertThatThrownBy(() -> service.decide(
                "app-1287", PolicyOutcome.REFERRED, "later", "s.chen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APPROVED or REJECTED");
    }

    private static PolicyRecord referred(String id, boolean sampled, boolean outage) {
        PolicyRecord record = new PolicyRecord(id, "pol-" + id);
        List<String> registryReasons = outage
                ? List.of(PolicyRuleEngine.REGISTRY_UNAVAILABLE)
                : List.of();
        record.completeDecision(new DecisionResult(
                PolicyOutcome.REFERRED,
                PolicyOutcome.APPROVED,
                List.of(
                        RuleResult.existingProduct(!outage, !outage, registryReasons),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(sampled, 7, sampled
                                ? List.of(PolicyRuleEngine.SAMPLED_FOR_REVIEW)
                                : List.of()))));
        return record;
    }
}
