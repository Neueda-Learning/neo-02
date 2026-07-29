package com.neobank.module.repository;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.PolicyOutcome;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRecordRepository extends JpaRepository<PolicyRecord, String> {

    Page<PolicyRecord> findAllByOrderByCreatedAtDescApplicationIdDesc(Pageable pageable);

    Page<PolicyRecord> findByOutcomeOrderByCreatedAtDescApplicationIdDesc(
            PolicyOutcome outcome, Pageable pageable);

    Page<PolicyRecord> findByOutcomeIsNullAndProcessingStatusOrderByCreatedAtDescApplicationIdDesc(
            String processingStatus, Pageable pageable);

    long countByOutcome(PolicyOutcome outcome);

    long countByOutcomeIsNullAndProcessingStatus(String processingStatus);

    List<PolicyRecord> findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
            Instant fromInclusive,
            Instant toExclusive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from PolicyRecord record where record.applicationId = :applicationId")
    Optional<PolicyRecord> findForUpdate(@Param("applicationId") String applicationId);

    List<PolicyRecord> findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDesc(
            String query, Pageable pageable);

    @Query("select record from PolicyRecord record "
            + "where record.applicationId in :applicationIds order by record.submittedAt desc")
    List<PolicyRecord> findByApplicationIdInOrderBySubmittedAtDesc(
            @Param("applicationIds") List<String> applicationIds);

    List<PolicyRecord> findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
            String name, Pageable pageable);

    @Query("select record from PolicyRecord record "
            + "where record.outcome = :outcome and record.decidedBy is null "
            + "order by case when record.claimedBy is null then 0 else 1 end, "
            + "record.submittedAt asc, record.applicationId asc")
    List<PolicyRecord> findOpenReferrals(
            @Param("outcome") PolicyOutcome outcome, Pageable pageable);
}
