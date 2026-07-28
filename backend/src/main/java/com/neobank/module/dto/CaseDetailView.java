package com.neobank.module.dto;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import java.util.List;

/** UC02 response: the stored decision and all four rule sections. */
public record CaseDetailView(
        String outcome,
        String machineOutcome,
        String reference,
        Integer policyConfigVersion,
        List<RuleResult> ruleResults) {

    public static CaseDetailView of(PolicyRecord record) {
        return new CaseDetailView(
                record.getOutcome() == null ? null : record.getOutcome().name(),
                record.getMachineOutcome() == null ? null : record.getMachineOutcome().name(),
                record.getReference(),
                record.getPolicyConfigVersion(),
                record.getRuleResults());
    }
}
