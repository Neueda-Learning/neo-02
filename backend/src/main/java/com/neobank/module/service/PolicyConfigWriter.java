package com.neobank.module.service;

import java.util.List;

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

    private final PolicyConfigRepository configs;

    public PolicyConfigWriter(PolicyConfigRepository configs) {
        this.configs = configs;
    }

    /**
     * Serializes publishers by locking the immutable seeded row. If the current document is
     * identical, returns it unchanged; otherwise inserts {@code current version + 1}.
     */
    @Transactional
    public PolicyConfig publish(List<String> supportedResidencies, List<String> excludedResidencies,
                                List<PolicyConfig.RestrictionEntry> restrictionList, int sampleEvery) {
        configs.lockVersionAllocator()
                .orElseThrow(() -> new IllegalStateException("seeded policy config version 1 is missing"));

        PolicyConfig current = configs.findFirstByOrderByVersionDesc()
                .orElseThrow(() -> new IllegalStateException("no policy config is available"));
        if (sameDocument(current, supportedResidencies, excludedResidencies,
                restrictionList, sampleEvery)) {
            return current;
        }

        int nextVersion = current.getVersion() + 1;
        PolicyConfig config = new PolicyConfig(nextVersion, supportedResidencies, excludedResidencies,
                restrictionList, sampleEvery);
        return configs.saveAndFlush(config);
    }

    private boolean sameDocument(PolicyConfig current, List<String> supportedResidencies,
                                 List<String> excludedResidencies,
                                 List<PolicyConfig.RestrictionEntry> restrictionList,
                                 int sampleEvery) {
        return current.getSupportedResidencies().equals(supportedResidencies)
                && current.getExcludedResidencies().equals(excludedResidencies)
                && current.getRestrictionList().equals(restrictionList)
                && current.getSampleEvery() == sampleEvery;
    }
}
