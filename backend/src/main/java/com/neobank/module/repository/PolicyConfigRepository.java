package com.neobank.module.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.neobank.module.model.PolicyConfig;

import jakarta.persistence.LockModeType;

public interface PolicyConfigRepository extends JpaRepository<PolicyConfig, Integer> {

    /** UC08 — all versions oldest first; never empty (seed guarantees v1). */
    List<PolicyConfig> findAllByOrderByVersionAsc();

    /**
     * Version 1 is immutable and always present, so it is a stable row-level mutex for allocating
     * later versions. Locking it avoids the non-portable aggregate {@code MAX(...) FOR UPDATE}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select config from PolicyConfig config where config.version = 1")
    Optional<PolicyConfig> lockVersionAllocator();

    Optional<PolicyConfig> findFirstByOrderByVersionDesc();
}
