package com.neobank.module.dto;

import java.time.Instant;
import java.util.List;

import com.neobank.module.model.PolicyConfig;

/**
 * UC08 · View Config History — one entry in the GET /config/versions response.
 *
 * {@code isCurrent} is true for the single row whose version equals MAX(version); it is computed
 * in the service, never stored in the database.
 */
public record PolicyConfigVersionDto(
        int version,
        List<String> supportedResidencies,
        List<String> excludedResidencies,
        List<PolicyConfig.RestrictionEntry> restrictionList,
        int sampleEvery,
        Instant effectiveFrom,
        boolean isCurrent) {

    public static PolicyConfigVersionDto from(PolicyConfig config, int maxVersion) {
        return new PolicyConfigVersionDto(
                config.getVersion(),
                config.getSupportedResidencies(),
                config.getExcludedResidencies(),
                config.getRestrictionList(),
                config.getSampleEvery(),
                config.getEffectiveFrom(),
                config.getVersion() == maxVersion);
    }
}
