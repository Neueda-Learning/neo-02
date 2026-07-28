package com.neobank.module.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.neobank.module.model.PolicyRecord;

public interface PolicyRecordRepository extends JpaRepository<PolicyRecord, String> {

    List<PolicyRecord> findTop10ByOrderByCreatedAtDescApplicationIdDesc();

    /**
     * UC-01 search: find records by a list of applicationIds, newest first, limited to 10.
     */
    @Query(value = "SELECT p FROM PolicyRecord p WHERE p.applicationId IN :applicationIds ORDER BY p.submittedAt DESC")
    List<PolicyRecord> findByApplicationIdInOrderBySubmittedAtDesc(
            @Param("applicationIds") List<String> applicationIds);
}
