package com.neobank.module.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.model.PolicyRecord;

/**
 * The search result row for GET /cases — a subset of PolicyRecord fields normalized for the UI board.
 * - applicationId, submittedAt, outcome are direct fields
 * - sampled comes from ruleResults.sampling.sampled
 * - reasonCount is calculated from ruleResults sections (non-null sections count as reasons)
 * 
 * The applicant's name is NOT included here — the UI hydrates it live via GET /cases/{id}/applicant.
 */
public record PolicyRecordView(
        String applicationId,
        Instant submittedAt,
        String outcome,
        boolean sampled,
        int reasonCount) {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static PolicyRecordView of(PolicyRecord row) {
        String outcome = row.getOutcome() != null ? row.getOutcome() : row.getMachineOutcome();
        boolean sampled = isSampled(row.getRuleResults());
        int reasonCount = countReasons(row.getRuleResults());
        
        return new PolicyRecordView(
                row.getApplicationId(),
                row.getSubmittedAt(),
                outcome,
                sampled,
                reasonCount);
    }

    /**
     * Extract sampled flag from ruleResults JSON.
     * ruleResults has structure: {sampling: {sampled: true/false, ...}, ...}
     */
    private static boolean isSampled(String ruleResultsJson) {
        if (ruleResultsJson == null) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(ruleResultsJson);
            JsonNode sampling = root.get("sampling");
            if (sampling != null) {
                JsonNode sampledNode = sampling.get("sampled");
                if (sampledNode != null) {
                    return sampledNode.asBoolean();
                }
            }
        } catch (Exception e) {
            // If JSON parsing fails, treat as not sampled
        }
        return false;
    }

    /**
     * Count non-null rule result sections.
     * ruleResults has sections: existingProduct, taxResidency, restrictionList, sampling
     * Each non-null section counts as one reason for the decision.
     */
    private static int countReasons(String ruleResultsJson) {
        if (ruleResultsJson == null) {
            return 0;
        }
        try {
            JsonNode root = mapper.readTree(ruleResultsJson);
            int count = 0;
            if (root.get("existingProduct") != null) count++;
            if (root.get("taxResidency") != null) count++;
            if (root.get("restrictionList") != null) count++;
            if (root.get("sampling") != null) count++;
            return count;
        } catch (Exception e) {
            // If JSON parsing fails, return 0
        }
        return 0;
    }
}
