package com.neobank.module.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neobank.module.model.PolicyConfig;

public interface PolicyConfigRepository extends JpaRepository<PolicyConfig, Integer> {

    /** UC08 — all versions oldest first; never empty (seed guarantees v1). */
    List<PolicyConfig> findAllByOrderByVersionAsc();
}
