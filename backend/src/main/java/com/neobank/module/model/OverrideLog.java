package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable audit evidence for one successful UC06 override. */
@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_outcome", nullable = false, length = 16)
    private PolicyOutcome oldOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_outcome", nullable = false, length = 16)
    private PolicyOutcome newOutcome;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 255)
    private String operator;

    @Column(name = "overridden_at", nullable = false)
    private Instant overriddenAt;

    protected OverrideLog() {
        // JPA
    }

    public OverrideLog(
            String applicationId,
            PolicyOutcome oldOutcome,
            PolicyOutcome newOutcome,
            String reason,
            String operator,
            Instant overriddenAt) {
        this.applicationId = applicationId;
        this.oldOutcome = oldOutcome;
        this.newOutcome = newOutcome;
        this.reason = reason;
        this.operator = operator;
        this.overriddenAt = overriddenAt;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public PolicyOutcome getOldOutcome() {
        return oldOutcome;
    }

    public PolicyOutcome getNewOutcome() {
        return newOutcome;
    }

    public String getReason() {
        return reason;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getOverriddenAt() {
        return overriddenAt;
    }
}
