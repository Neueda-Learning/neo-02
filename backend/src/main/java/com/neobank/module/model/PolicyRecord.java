package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** The durable hand-off between the request thread and the policy worker. */
@Entity
@Table(name = "policy_record")
public class PolicyRecord {

    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(name = "rule_results", columnDefinition = "JSON")
    private String ruleResults;

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

    public String getReference() {
        return reference;
    }

    public String getRuleResults() {
        return ruleResults;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
