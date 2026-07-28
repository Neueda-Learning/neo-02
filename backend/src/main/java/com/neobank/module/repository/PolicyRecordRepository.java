package com.neobank.module.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.neobank.module.model.PolicyRecord;

public interface PolicyRecordRepository extends JpaRepository<PolicyRecord, String> {

    List<PolicyRecord> findTop10ByOrderByCreatedAtDescApplicationIdDesc();

    @Query("""
            select p from PolicyRecord p
            where p.submittedAt >= :fromInclusive and p.submittedAt <= :toInclusive
            """)
    List<PolicyRecord> findBySubmittedAtBetweenInclusive(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toInclusive") Instant toInclusive);
}
