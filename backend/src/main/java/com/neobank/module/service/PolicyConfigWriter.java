package com.neobank.module.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neobank.module.model.PolicyConfig;
import com.neobank.module.repository.PolicyConfigRepository;

/**
 * Owns the short transaction that publishes a new, insert-only {@code policy_config} version.
 * Never updates or deletes an existing row — history is the audit trail (UC08).
 */
@Service
public class PolicyConfigWriter {

    private final JdbcTemplate jdbc;
    private final PolicyConfigRepository configs;

    public PolicyConfigWriter(JdbcTemplate jdbc, PolicyConfigRepository configs) {
        this.jdbc = jdbc;
        this.configs = configs;
    }

    /**
     * Locks the current maximum version, then inserts {@code version = MAX + 1} with the whole
     * document — both residency lists, the restriction list and sampleEvery together.
     */
    @Transactional
    public PolicyConfig publish(List<String> supportedResidencies, List<String> excludedResidencies,
                                List<PolicyConfig.RestrictionEntry> restrictionList, int sampleEvery) {
        Integer maxVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM policy_config FOR UPDATE", Integer.class);
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;
        PolicyConfig config = new PolicyConfig(nextVersion, supportedResidencies, excludedResidencies,
                restrictionList, sampleEvery);
        return configs.saveAndFlush(config);
    }
}
