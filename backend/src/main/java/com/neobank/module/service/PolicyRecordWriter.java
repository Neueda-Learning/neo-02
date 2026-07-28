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
     */
    @Transactional
    public boolean createIfAbsent(String applicationId) {
        Instant now = Instant.now();
        int inserted = jdbc.update("""
                        INSERT IGNORE INTO policy_record
                          (application_id, processing_status, reference, submitted_at,
                           created_at, updated_at, lock_version)
                        VALUES (?, 'IN_PROGRESS', ?, ?, ?, ?, 0)
                        """,
                applicationId, newReference(), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now));
        return inserted == 1;
    }

    private String newReference() {
        return "pol-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toLowerCase(Locale.ROOT);
    }
}
