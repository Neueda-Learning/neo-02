package com.neobank.module.repository;

import com.neobank.module.model.PolicyRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRecordRepository extends JpaRepository<PolicyRecord, String> {

    List<PolicyRecord> findTop10ByOrderByCreatedAtDescApplicationIdDesc();
}
