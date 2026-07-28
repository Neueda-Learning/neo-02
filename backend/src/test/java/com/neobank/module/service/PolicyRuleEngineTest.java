package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyConfigDocument;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.RuleResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyRuleEngineTest {

    private final PolicyRuleEngine engine = new PolicyRuleEngine();

    @Test
    void cleanApplicationIsApprovedWithAllChecksPassed() {
        DecisionResult result = decide(
                application("Maria Nowak", "1996-04-11", List.of("GB")),
                config(),
                RegistryLookupService.RegistrySnapshot.available(false),
                1);

        assertThat(result.outcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(result.machineOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(result.ruleResults()).extracting(RuleResult::ruleName)
                .containsExactly("existingProduct", "taxResidency", "restrictionList", "sampling");
        assertThat(result.reasonCodes()).containsExactly(PolicyRuleEngine.ALL_CHECKS_PASSED);
        assertThat(result.ruleResults()).allSatisfy(rule -> assertThat(rule.passed()).isTrue());
    }

    @Test
    void excludedTaxResidencyWinsOverSupportedResidency() {
        DecisionResult result = decide(
                application("Sofia Ruiz", "1991-05-20", List.of("GB", "US")),
                config(),
                RegistryLookupService.RegistrySnapshot.available(false),
                2);

        assertThat(result.outcome()).isEqualTo(PolicyOutcome.REJECTED);
        assertThat(result.reasonCodes())
                .containsExactly(PolicyRuleEngine.TAX_RESIDENCY_EXCLUDED);
        RuleResult tax = rule(result, "taxResidency");
        assertThat(tax.passed()).isFalse();
        assertThat(tax.matchedList()).isEqualTo("EXCLUDED");
    }

    @Test
    void activeRegistryProductRejectsTheApplication() {
        DecisionResult result = decide(
                application("James Whitfield", "1988-03-12", List.of("GB")),
                config(),
                RegistryLookupService.RegistrySnapshot.available(true),
                3);

        assertThat(result.outcome()).isEqualTo(PolicyOutcome.REJECTED);
        assertThat(result.reasonCodes())
                .containsExactly(PolicyRuleEngine.EXISTING_PRODUCT_HELD);
        RuleResult product = rule(result, "existingProduct");
        assertThat(product.registryChecked()).isTrue();
        assertThat(product.passed()).isFalse();
    }

    @Test
    void allBusinessFailuresAreReported() {
        DecisionResult result = decide(
                application("Victor Sable", "1978-03-02", List.of("US")),
                config(),
                RegistryLookupService.RegistrySnapshot.available(false),
                4);

        assertThat(result.outcome()).isEqualTo(PolicyOutcome.REJECTED);
        assertThat(result.reasonCodes()).containsExactlyInAnyOrder(
                PolicyRuleEngine.TAX_RESIDENCY_EXCLUDED,
                PolicyRuleEngine.CUSTOMER_BLOCKED);
    }

    @Test
    void samplingOverridesACleanMachineApproval() {
        DecisionResult result = decide(
                application("Clean Customer", "1990-01-01", List.of("GB")),
                config(),
                RegistryLookupService.RegistrySnapshot.available(false),
                21);

        assertThat(result.outcome()).isEqualTo(PolicyOutcome.REFERRED);
        assertThat(result.machineOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(result.reasonCodes())
                .containsExactly(PolicyRuleEngine.SAMPLED_FOR_REVIEW);
        RuleResult sampling = rule(result, "sampling");
        assertThat(sampling.sampled()).isTrue();
        assertThat(sampling.position()).isEqualTo(21);
    }

    @Test
    void registryFailureRefersButStillRecordsTheOtherRules() {
        DecisionResult result = decide(
                application("Maria Nowak", "1996-04-11", List.of("GB")),
                config(),
                RegistryLookupService.RegistrySnapshot.unavailable(),
                5);

        assertThat(result.outcome()).isEqualTo(PolicyOutcome.REFERRED);
        assertThat(result.machineOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(result.reasonCodes())
                .containsExactly(PolicyRuleEngine.REGISTRY_UNAVAILABLE);
        assertThat(rule(result, "taxResidency").passed()).isTrue();
        assertThat(rule(result, "restrictionList").passed()).isTrue();
    }

    private DecisionResult decide(
            Application application,
            PolicyConfigDocument config,
            RegistryLookupService.RegistrySnapshot registry,
            long position) {
        return engine.decide(application, config, registry, position);
    }

    private RuleResult rule(DecisionResult result, String name) {
        return result.ruleResults().stream()
                .filter(rule -> name.equals(rule.ruleName()))
                .findFirst()
                .orElseThrow();
    }

    private PolicyConfigDocument config() {
        return new PolicyConfigDocument(
                1,
                List.of("GB", "IE", "PL", "DE", "FR", "ES", "NL"),
                List.of("US"),
                List.of(new PolicyConfigDocument.RestrictionEntry(
                        "Victor Sable", "1978-03-02", "prior fraud loss")),
                7);
    }

    private Application application(
            String fullName, String dateOfBirth, List<String> taxResidencies) {
        return new Application(
                "inner-id",
                "MOBILE_APP",
                "2026-07-25T09:14:00Z",
                new Application.Applicant(
                        fullName, dateOfBirth, null, null, null, "GB", taxResidencies,
                        null, null, null, null),
                null, null, null, null, null, null);
    }
}
