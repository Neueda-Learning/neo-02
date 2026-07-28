package com.neobank.module.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The durable hand-off between the request thread and the policy worker. */
@Entity
@Table(name = "policy_record")
public class PolicyRecord {

    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;

    @Column(name = "outcome", length = 16)
    private String outcome;

    @Column(name = "machine_outcome", length = 16)
    private String machineOutcome;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(name = "policy_config_version")
    private Integer policyConfigVersion;

    @Column(name = "sampling_position")
    private Long samplingPosition;

    @Column(name = "rule_results", columnDefinition = "JSON")
    private String ruleResults;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected PolicyRecord() {
        // JPA
    }

    public PolicyRecord(String applicationId, String reference) {
        this.applicationId = applicationId;
        this.reference = reference;
        this.processingStatus = "IN_PROGRESS";
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        submittedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getMachineOutcome() {
        return machineOutcome;
    }

    public String getReference() {
        return reference;
    }

    public Integer getPolicyConfigVersion() {
        return policyConfigVersion;
    }

    public Long getSamplingPosition() {
        return samplingPosition;
    }

    public String getRuleResults() {
        return ruleResults;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
