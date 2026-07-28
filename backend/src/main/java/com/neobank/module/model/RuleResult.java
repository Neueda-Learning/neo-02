package com.neobank.module.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** One persisted section of the policy decision breakdown. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleResult(
        String ruleName,
        boolean passed,
        List<String> reasonCodes,
        Boolean registryChecked,
        String matchedList,
        Boolean sampled,
        Long position) {

    public RuleResult {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public static RuleResult existingProduct(
            boolean passed, boolean registryChecked, List<String> reasonCodes) {
        return new RuleResult(
                "existingProduct", passed, reasonCodes, registryChecked, null, null, null);
    }

    public static RuleResult taxResidency(
            boolean passed, String matchedList, List<String> reasonCodes) {
        return new RuleResult(
                "taxResidency", passed, reasonCodes, null, matchedList, null, null);
    }

    public static RuleResult restrictionList(boolean passed, List<String> reasonCodes) {
        return new RuleResult(
                "restrictionList", passed, reasonCodes, null, null, null, null);
    }

    public static RuleResult sampling(
            boolean sampled, long position, List<String> reasonCodes) {
        return new RuleResult(
                "sampling", !sampled, reasonCodes, null, null, sampled, position);
    }
}
