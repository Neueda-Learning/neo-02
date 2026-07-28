package com.neobank.module.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.model.PolicyConfig;

/** UC07 · Edit Policy Config — policy ships as data, never as a deploy. */
@Service
public class PolicyConfigService {

    private final PolicyConfigValidator validator;
    private final PolicyConfigWriter writer;

    public PolicyConfigService(PolicyConfigValidator validator, PolicyConfigWriter writer) {
        this.validator = validator;
        this.writer = writer;
    }

    /** Validates the full document, then publishes it as a brand-new version. Never updates. */
    public int createVersion(PolicyConfigRequest request) {
        validator.validate(request);
        List<PolicyConfig.RestrictionEntry> restrictions = request.restrictionList().stream()
                .map(entry -> new PolicyConfig.RestrictionEntry(
                        entry.fullName(), entry.dateOfBirth(), entry.reason()))
                .toList();
        PolicyConfig saved = writer.publish(request.supportedResidencies(), request.excludedResidencies(),
                restrictions, request.sampleEvery());
        return saved.getVersion();
    }
}
