package com.neobank.module.model;

import java.util.List;

/** The immutable policy document used by one decision. */
public record PolicyConfigDocument(
        int version,
        List<String> supportedResidencies,
        List<String> excludedResidencies,
        List<RestrictionEntry> restrictionList,
        int sampleEvery) {

    public PolicyConfigDocument {
        supportedResidencies = List.copyOf(supportedResidencies);
        excludedResidencies = List.copyOf(excludedResidencies);
        restrictionList = List.copyOf(restrictionList);
        if (sampleEvery < 1) {
            throw new IllegalArgumentException("sampleEvery must be at least 1");
        }
    }

    public record RestrictionEntry(String fullName, String dateOfBirth, String reason) {
    }
}
