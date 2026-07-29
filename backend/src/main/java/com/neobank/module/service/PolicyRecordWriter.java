package com.neobank.module.service;

import com.neobank.module.repository.PolicyConfigRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the short transaction that establishes the durable hand-off row. */
@Service
public class PolicyRecordWriter {

    private final JdbcTemplate jdbc;
    private final PolicyConfigRepository configs;

    public PolicyRecordWriter(JdbcTemplate jdbc, PolicyConfigRepository configs) {
        this.jdbc = jdbc;
        this.configs = configs;
    }

    @Transactional
    public boolean createIfAbsent(String applicationId) {
        return createIfAbsent(applicationId, null);
    }

    /**
     * Returns true only for the caller that inserted the row. The primary key makes the insert
     * atomic under concurrent retries; returning means the transaction has committed.
     *
     * <p>UC07's immutable version-1 allocator row serializes config publishers and first-time
     * intake. Assigning the config and sampling position under that shared lock gives both
     * operations one deterministic order.</p>
     *
     * @param applicantFullName captured once at intake for UC01 name search; may be null
     */
    @Transactional
    public boolean createIfAbsent(String applicationId, String applicantFullName) {
        configs.lockVersionAllocator()
                .orElseThrow(() -> new IllegalStateException(
                        "Seeded policy config version 1 is missing"));
        int currentVersion = configs.findFirstByOrderByVersionDesc()
                .orElseThrow(() -> new IllegalStateException("No policy config is available"))
                .getVersion();

        Long nextPosition = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sampling_position), 0) + 1 FROM policy_record", Long.class);
        if (nextPosition == null) {
            throw new IllegalStateException("Sampling position could not be allocated");
        }

        Instant now = Instant.now();
        int inserted = jdbc.update("""
                        INSERT IGNORE INTO policy_record
                          (application_id, processing_status, reference, applicant_full_name,
                           submitted_at,
                           policy_config_version, sampling_position,
                           created_at, updated_at, lock_version)
                        VALUES (?, 'IN_PROGRESS', ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                applicationId, newReference(), applicantFullName, Timestamp.from(now),
                currentVersion, nextPosition,
                Timestamp.from(now), Timestamp.from(now));
        return inserted == 1;
    }

    private String newReference() {
        return "pol-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toLowerCase(Locale.ROOT);
    }
}
