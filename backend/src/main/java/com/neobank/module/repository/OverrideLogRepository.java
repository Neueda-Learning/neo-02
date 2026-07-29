package com.neobank.module.repository;

import com.neobank.module.model.OverrideLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideLogRepository extends JpaRepository<OverrideLog, Long> {

    List<OverrideLog> findByApplicationIdOrderByOverriddenAtAscIdAsc(String applicationId);

    Optional<OverrideLog> findFirstByApplicationIdOrderByOverriddenAtDescIdDesc(String applicationId);
}
