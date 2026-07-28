package com.neobank.module.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.dto.PolicyConfigVersionDto;
import com.neobank.module.model.PolicyConfig;
import com.neobank.module.repository.PolicyConfigRepository;

/** UC07 · Edit Policy Config — policy ships as data, never as a deploy. */
@Service
public class PolicyConfigService {

    private final PolicyConfigValidator validator;
    private final PolicyConfigWriter writer;
    private final PolicyConfigRepository configs;

    public PolicyConfigService(PolicyConfigValidator validator, PolicyConfigWriter writer,
            PolicyConfigRepository configs) {
        this.validator = validator;
        this.writer = writer;
        this.configs = configs;
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

    /**
     * UC08 · View Config History — returns every version oldest first; the row with the highest
     * version number is flagged {@code isCurrent = true}.
     */
    public List<PolicyConfigVersionDto> versions() {
        List<PolicyConfig> all = configs.findAllByOrderByVersionAsc();
        int maxVersion = all.stream().mapToInt(PolicyConfig::getVersion).max().orElse(-1);
        return all.stream()
                .map(c -> PolicyConfigVersionDto.from(c, maxVersion))
                .toList();
    }
}
