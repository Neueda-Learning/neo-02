package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.PolicyRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the UC00 entity and Liquibase schema against deployed-dialect MySQL. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PolicyRecordRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("neo_02");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    PolicyRecordRepository records;

    @Test
    void anInProgressRowRoundTripsThroughRealMysql() {
        PolicyRecord saved = records.saveAndFlush(new PolicyRecord("APP-1", "pol-0000000001"));

        PolicyRecord reloaded = records.findById(saved.getApplicationId()).orElseThrow();
        assertThat(reloaded.getProcessingStatus()).isEqualTo("IN_PROGRESS");
        assertThat(reloaded.getSubmittedAt()).isNotNull();
    }
}
