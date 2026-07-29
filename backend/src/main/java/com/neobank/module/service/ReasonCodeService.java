package com.neobank.module.service;

import com.neobank.module.dto.ReasonCodeCountDto;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.PolicyRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReasonCodeService {

    private static final int MAX_REASON_CODES = 10;

    private static final Set<String> REVIEW_CODES = Set.of(
            PolicyRuleEngine.SAMPLED_FOR_REVIEW,
            PolicyRuleEngine.REGISTRY_UNAVAILABLE);

    private final PolicyRecordRepository records;

    public ReasonCodeService(PolicyRecordRepository records) {
        this.records = records;
    }

    @Transactional(readOnly = true)
    public List<ReasonCodeCountDto> countReasonCodes(LocalDate from, LocalDate to) {
        Instant fromInclusive = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<String, Long> counts = new LinkedHashMap<>();
        List<PolicyRecord> rows = records.findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                fromInclusive, toExclusive);
        for (PolicyRecord row : rows) {
            for (RuleResult rule : row.getRuleResults()) {
                for (String code : rule.reasonCodes()) {
                    if (PolicyRuleEngine.ALL_CHECKS_PASSED.equals(code)) {
                        continue;
                    }
                    counts.merge(code, 1L, Long::sum);
                }
            }
        }

        return counts.entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Long>::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_REASON_CODES)
                .map(entry -> new ReasonCodeCountDto(entry.getKey(), entry.getValue(), kindOf(entry.getKey())))
                .toList();
    }

    private String kindOf(String code) {
        return REVIEW_CODES.contains(code) ? "review" : "rejection";
    }
}
