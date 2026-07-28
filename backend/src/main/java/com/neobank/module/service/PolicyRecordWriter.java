package com.neobank.module.service;

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

    public PolicyRecordWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns true only for the caller that inserted the row. The primary key makes the insert
     * atomic under concurrent retries; returning means the transaction has committed.
     *
     * <p>The current config row serializes first-time intake. Assigning the sampling position here
     * makes it follow durable acceptance order instead of whichever async worker happens to run
     * first.</p>
     */
    @Transactional
    public boolean createIfAbsent(String applicationId) {
        Integer currentVersion = jdbc.queryForObject("""
                        SELECT version
                        FROM policy_config
                        ORDER BY version DESC
                        LIMIT 1
                        FOR UPDATE
                        """,
                Integer.class);
        if (currentVersion == null) {
            throw new IllegalStateException("No policy config is available");
        }

        Long nextPosition = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sampling_position), 0) + 1 FROM policy_record", Long.class);
        if (nextPosition == null) {
            throw new IllegalStateException("Sampling position could not be allocated");
        }

        Instant now = Instant.now();
        int inserted = jdbc.update("""
                        INSERT IGNORE INTO policy_record
                          (application_id, processing_status, reference, submitted_at,
                           policy_config_version, sampling_position,
                           created_at, updated_at, lock_version)
                        VALUES (?, 'IN_PROGRESS', ?, ?, ?, ?, ?, ?, 0)
                        """,
                applicationId, newReference(), Timestamp.from(now), currentVersion, nextPosition,
                Timestamp.from(now), Timestamp.from(now));
        return inserted == 1;
    }

    private String newReference() {
        return "pol-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toLowerCase(Locale.ROOT);
    }
}
