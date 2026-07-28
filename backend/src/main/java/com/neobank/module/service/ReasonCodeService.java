package com.neobank.module.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.ReasonCodeCountView;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReasonCodeService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CODE_SAMPLED = "POL_SAMPLED_FOR_REVIEW";
    private static final String CODE_REGISTRY_DOWN = "POL_REGISTRY_UNAVAILABLE";
    private static final String CODE_EXISTING_PRODUCT = "POL_EXISTING_PRODUCT_HELD";
    private static final String CODE_TAX_UNSUPPORTED = "POL_TAX_RESIDENCY_UNSUPPORTED";
    private static final String CODE_TAX_EXCLUDED = "POL_TAX_RESIDENCY_EXCLUDED";
    private static final String CODE_CUSTOMER_BLOCKED = "POL_CUSTOMER_BLOCKED";

    private static final Set<String> REVIEW_CODES = Set.of(
            CODE_SAMPLED,
            CODE_REGISTRY_DOWN);

    private final PolicyRecordRepository records;

    public ReasonCodeService(PolicyRecordRepository records) {
        this.records = records;
    }

    @Transactional(readOnly = true)
    public List<ReasonCodeCountView> countReasonCodes(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }

        Instant fromInclusive = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInclusive = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);

        List<PolicyRecord> window = records.findBySubmittedAtBetweenInclusive(fromInclusive, toInclusive);
        if (window.isEmpty()) {
            return List.of();
        }

        Map<String, Long> counts = new HashMap<>();
        for (PolicyRecord row : window) {
            for (String code : extractReasonCodes(row.getRuleResults())) {
                counts.merge(code, 1L, Long::sum);
            }
        }

        if (counts.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Comparator
                .comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed()
                .thenComparing(Map.Entry::getKey));

        List<ReasonCodeCountView> rows = new ArrayList<>(ranked.size());
        for (Map.Entry<String, Long> entry : ranked) {
            rows.add(new ReasonCodeCountView(entry.getKey(), entry.getValue(), kindOf(entry.getKey())));
        }
        return rows;
    }

    private String kindOf(String code) {
        return REVIEW_CODES.contains(code) ? "review" : "rejection";
    }

    private List<String> extractReasonCodes(String ruleResultsJson) {
        if (ruleResultsJson == null || ruleResultsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JSON.readTree(ruleResultsJson);

            List<String> explicit = extractExplicitReasons(root);
            if (!explicit.isEmpty()) {
                return explicit;
            }

            // Backward compatibility: derive UC05 codes from older UC01-style ruleResults.
            return extractLegacyReasons(root);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> extractExplicitReasons(JsonNode root) {
        JsonNode reasons = root.get("reasons");
        if (reasons == null || !reasons.isArray()) {
            return List.of();
        }

        List<String> codes = new ArrayList<>();
        for (JsonNode reason : reasons) {
            JsonNode codeNode = reason.get("code");
            if (codeNode != null && codeNode.isTextual() && !codeNode.asText().isBlank()) {
                codes.add(codeNode.asText());
            }
        }
        return codes;
    }

    private List<String> extractLegacyReasons(JsonNode root) {
        Map<String, Boolean> dedupe = new LinkedHashMap<>();

        JsonNode existingProduct = root.get("existingProduct");
        if (existingProduct != null) {
            if (existingProduct.path("registryChecked").isBoolean()
                    && !existingProduct.path("registryChecked").asBoolean()) {
                dedupe.put(CODE_REGISTRY_DOWN, true);
            }
            if (existingProduct.path("registered").asBoolean(false)) {
                dedupe.put(CODE_EXISTING_PRODUCT, true);
            }
        }

        JsonNode taxResidency = root.get("taxResidency");
        if (taxResidency != null) {
            if (taxResidency.path("excluded").asBoolean(false)) {
                dedupe.put(CODE_TAX_EXCLUDED, true);
            } else if (taxResidency.path("matchedList").isArray()
                    && taxResidency.path("matchedList").isEmpty()) {
                dedupe.put(CODE_TAX_UNSUPPORTED, true);
            }
        }

        JsonNode restrictionList = root.get("restrictionList");
        if (restrictionList != null) {
            if (restrictionList.path("blocked").asBoolean(false)
                    || (restrictionList.path("notRestricted").isBoolean()
                    && !restrictionList.path("notRestricted").asBoolean())) {
                dedupe.put(CODE_CUSTOMER_BLOCKED, true);
            }
        }

        JsonNode sampling = root.get("sampling");
        if (sampling != null && sampling.path("sampled").asBoolean(false)) {
            dedupe.put(CODE_SAMPLED, true);
        }

        return new ArrayList<>(dedupe.keySet());
    }
}
