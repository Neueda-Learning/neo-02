package com.neobank.module.service;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyConfigDocument;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.RuleResult;
import com.neobank.module.service.RegistryLookupService.RegistrySnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Pure UC02 rules over one application, one pinned config and one Registry result. */
@Service
public class PolicyRuleEngine {

    public static final String ALL_CHECKS_PASSED = "POL_ALL_CHECKS_PASSED";
    public static final String EXISTING_PRODUCT_HELD = "POL_EXISTING_PRODUCT_HELD";
    public static final String TAX_RESIDENCY_UNSUPPORTED = "POL_TAX_RESIDENCY_UNSUPPORTED";
    public static final String TAX_RESIDENCY_EXCLUDED = "POL_TAX_RESIDENCY_EXCLUDED";
    public static final String CUSTOMER_BLOCKED = "POL_CUSTOMER_BLOCKED";
    public static final String SAMPLED_FOR_REVIEW = "POL_SAMPLED_FOR_REVIEW";
    public static final String REGISTRY_UNAVAILABLE = "POL_REGISTRY_UNAVAILABLE";

    public DecisionResult decide(
            Application application,
            PolicyConfigDocument config,
            RegistrySnapshot registry,
            long samplingPosition) {
        Application.Applicant applicant = application == null ? null : application.applicant();

        RuleResult existingProduct = existingProduct(registry);
        RuleResult taxResidency = taxResidency(applicant, config);
        RuleResult restrictionList = restrictionList(applicant, config);

        boolean rejected = isBusinessRejection(existingProduct)
                || !taxResidency.passed()
                || !restrictionList.passed();
        PolicyOutcome machineOutcome =
                rejected ? PolicyOutcome.REJECTED : PolicyOutcome.APPROVED;

        boolean sampled = samplingPosition % config.sampleEvery() == 0;
        List<String> samplingReasons = new ArrayList<>();
        if (sampled) {
            samplingReasons.add(SAMPLED_FOR_REVIEW);
        } else if (!rejected && registry.available()) {
            samplingReasons.add(ALL_CHECKS_PASSED);
        }
        RuleResult sampling =
                RuleResult.sampling(sampled, samplingPosition, samplingReasons);

        PolicyOutcome outcome = sampled || !registry.available()
                ? PolicyOutcome.REFERRED
                : machineOutcome;

        return new DecisionResult(
                outcome,
                machineOutcome,
                List.of(existingProduct, taxResidency, restrictionList, sampling));
    }

    private RuleResult existingProduct(RegistrySnapshot registry) {
        if (!registry.available()) {
            return RuleResult.existingProduct(
                    false, false, List.of(REGISTRY_UNAVAILABLE));
        }
        if (registry.activeProductHeld()) {
            return RuleResult.existingProduct(
                    false, true, List.of(EXISTING_PRODUCT_HELD));
        }
        return RuleResult.existingProduct(true, true, List.of());
    }

    private RuleResult taxResidency(
            Application.Applicant applicant, PolicyConfigDocument config) {
        Set<String> residencies = normalized(
                applicant == null ? null : applicant.taxResidencies());
        Set<String> excluded = normalized(config.excludedResidencies());
        Set<String> supported = normalized(config.supportedResidencies());

        if (residencies.stream().anyMatch(excluded::contains)) {
            return RuleResult.taxResidency(
                    false, "EXCLUDED", List.of(TAX_RESIDENCY_EXCLUDED));
        }
        if (residencies.stream().noneMatch(supported::contains)) {
            return RuleResult.taxResidency(
                    false, "NONE", List.of(TAX_RESIDENCY_UNSUPPORTED));
        }
        return RuleResult.taxResidency(true, "SUPPORTED", List.of());
    }

    private RuleResult restrictionList(
            Application.Applicant applicant, PolicyConfigDocument config) {
        String fullName = normalize(applicant == null ? null : applicant.fullName());
        String dateOfBirth = normalize(applicant == null ? null : applicant.dateOfBirth());
        boolean blocked = config.restrictionList().stream().anyMatch(entry ->
                normalize(entry.fullName()).equals(fullName)
                        && normalize(entry.dateOfBirth()).equals(dateOfBirth));
        return RuleResult.restrictionList(
                !blocked, blocked ? List.of(CUSTOMER_BLOCKED) : List.of());
    }

    private boolean isBusinessRejection(RuleResult existingProduct) {
        return existingProduct.reasonCodes().contains(EXISTING_PRODUCT_HELD);
    }

    private Set<String> normalized(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        values.stream().map(this::normalize).forEach(normalized::add);
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
