package com.neobank.module.service;

import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the short transactions that pin and complete one policy decision. */
@Service
public class PolicyDecisionWriter {

    private final JdbcTemplate jdbc;
    private final PolicyRecordRepository records;

    public PolicyDecisionWriter(JdbcTemplate jdbc, PolicyRecordRepository records) {
        this.jdbc = jdbc;
        this.records = records;
    }

    /** Reads the decision context that intake pinned before acknowledging the request. */
    @Transactional
    public DecisionContext pinContext(String applicationId) {
        List<DecisionContext> existing = jdbc.query("""
                        SELECT policy_config_version, sampling_position, processing_status
                        FROM policy_record
                        WHERE application_id = ?
                        FOR UPDATE
                        """,
                (rs, rowNumber) -> new DecisionContext(
                        (Integer) rs.getObject("policy_config_version"),
                        (Long) rs.getObject("sampling_position"),
                        "DECIDED".equals(rs.getString("processing_status"))),
                applicationId);
        if (existing.isEmpty()) {
            throw new CaseNotFoundException(applicationId);
        }

        DecisionContext context = existing.getFirst();
        if (context.policyConfigVersion() == null || context.samplingPosition() == null) {
            throw new IllegalStateException("Policy case " + applicationId
                    + " has an incomplete decision context");
        }
        return context;
    }

    /** Returns true only for the worker that changes the row to DECIDED. */
    @Transactional
    public boolean complete(String applicationId, DecisionResult result) {
        PolicyRecord record = records.findForUpdate(applicationId)
                .orElseThrow(() -> new CaseNotFoundException(applicationId));
        if (record.isDecided()) {
            return false;
        }
        record.completeDecision(result);
        records.saveAndFlush(record);
        return true;
    }

    public record DecisionContext(
            Integer policyConfigVersion,
            Long samplingPosition,
            boolean decided) {
    }
}
