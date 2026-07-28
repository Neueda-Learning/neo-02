package com.neobank.module.repository;

import com.neobank.module.model.PolicyRecord;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRecordRepository extends JpaRepository<PolicyRecord, String> {

    List<PolicyRecord> findTop10ByOrderByCreatedAtDescApplicationIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from PolicyRecord record where record.applicationId = :applicationId")
    Optional<PolicyRecord> findForUpdate(@Param("applicationId") String applicationId);
}
