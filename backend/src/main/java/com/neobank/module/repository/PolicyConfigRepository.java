package com.neobank.module.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neobank.module.model.PolicyConfig;

public interface PolicyConfigRepository extends JpaRepository<PolicyConfig, Integer> {
}
