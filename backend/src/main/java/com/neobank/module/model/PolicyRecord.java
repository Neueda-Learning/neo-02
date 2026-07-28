package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The durable hand-off between the request thread and the policy worker. */
@Entity
@Table(name = "policy_record")
public class PolicyRecord {

    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PolicyOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "machine_outcome", length = 16)
    private PolicyOutcome machineOutcome;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(name = "policy_config_version")
    private Integer policyConfigVersion;

    @Column(name = "sampling_position")
    private Long samplingPosition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results", columnDefinition = "json")
    private List<RuleResult> ruleResults;

    @Column(name = "decided_at")
    private Instant decidedAt;

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

    public PolicyOutcome getOutcome() {
        return outcome;
    }

    public PolicyOutcome getMachineOutcome() {
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

    public List<RuleResult> getRuleResults() {
        return ruleResults == null ? List.of() : List.copyOf(ruleResults);
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public boolean isDecided() {
        return "DECIDED".equals(processingStatus);
    }

    public void completeDecision(DecisionResult result) {
        this.outcome = result.outcome();
        this.machineOutcome = result.machineOutcome();
        this.ruleResults = new ArrayList<>(result.ruleResults());
        this.processingStatus = "DECIDED";
        this.decidedAt = Instant.now();
    }
}
