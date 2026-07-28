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

    /**
     * Pins the current config and next first-decision position exactly once.
     *
     * <p>The current config row is a concrete lock target, so concurrent decision workers cannot
     * allocate the same sampling position. No applicant payload is stored.</p>
     */
    @Transactional
    public DecisionContext pinContext(String applicationId) {
        Integer currentVersion = jdbc.queryForObject("""
                        SELECT version
                        FROM policy_config
                        ORDER BY version DESC
                        LIMIT 1
                        FOR UPDATE
                        """,
                Integer.class);
        if (currentVersion == null) {
            throw new IllegalStateException("No policy config is available");
        }

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
        if (context.policyConfigVersion() != null && context.samplingPosition() != null) {
            return context;
        }
        if (context.policyConfigVersion() != null || context.samplingPosition() != null) {
            throw new IllegalStateException("Policy case " + applicationId
                    + " has an incomplete decision context");
        }

        Long nextPosition = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sampling_position), 0) + 1 FROM policy_record", Long.class);
        if (nextPosition == null) {
            throw new IllegalStateException("Sampling position could not be allocated");
        }
        jdbc.update("""
                        UPDATE policy_record
                        SET policy_config_version = ?, sampling_position = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE application_id = ?
                        """,
                currentVersion, nextPosition, applicationId);
        return new DecisionContext(currentVersion, nextPosition, false);
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
