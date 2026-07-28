package com.neobank.module.model;

import java.util.List;

/** The complete machine decision before it is persisted and reported. */
public record DecisionResult(
        PolicyOutcome outcome,
        PolicyOutcome machineOutcome,
        List<RuleResult> ruleResults) {

    public DecisionResult {
        ruleResults = List.copyOf(ruleResults);
    }

    public List<String> reasonCodes() {
        return ruleResults.stream()
                .flatMap(result -> result.reasonCodes().stream())
                .distinct()
                .toList();
    }
}
