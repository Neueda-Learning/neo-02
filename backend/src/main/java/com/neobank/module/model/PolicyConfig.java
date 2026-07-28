package com.neobank.module.model;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One complete, insert-only policy document (UC07 · Edit Policy Config). {@code MAX(version)} is
 * the current one; existing rows are never updated or deleted so that old cases stay explainable
 * by the version they were decided under.
 */
@Entity
@Table(name = "policy_config")
public class PolicyConfig {

    @Id
    @Column(nullable = false)
    private int version;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "supported_residencies", nullable = false, columnDefinition = "json")
    private List<String> supportedResidencies;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "excluded_residencies", nullable = false, columnDefinition = "json")
    private List<String> excludedResidencies;

    @Convert(converter = RestrictionListJsonConverter.class)
    @Column(name = "restriction_list", nullable = false, columnDefinition = "json")
    private List<RestrictionEntry> restrictionList;

    @Column(name = "sample_every", nullable = false)
    private int sampleEvery;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    protected PolicyConfig() {
        // JPA
    }

    public PolicyConfig(int version, List<String> supportedResidencies, List<String> excludedResidencies,
                         List<RestrictionEntry> restrictionList, int sampleEvery) {
        this.version = version;
        this.supportedResidencies = supportedResidencies;
        this.excludedResidencies = excludedResidencies;
        this.restrictionList = restrictionList;
        this.sampleEvery = sampleEvery;
    }

    @PrePersist
    void onCreate() {
        effectiveFrom = Instant.now();
    }

    public int getVersion() {
        return version;
    }

    public List<String> getSupportedResidencies() {
        return supportedResidencies;
    }

    public List<String> getExcludedResidencies() {
        return excludedResidencies;
    }

    public List<RestrictionEntry> getRestrictionList() {
        return restrictionList;
    }

    public int getSampleEvery() {
        return sampleEvery;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    /** One bank-owned blocked-person entry inside {@link #restrictionList}. */
    public record RestrictionEntry(String fullName, String dateOfBirth, String reason) {
    }
}
