package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.RuleResult;
import jakarta.persistence.EntityManager;
import java.util.List;
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

    @Autowired
    EntityManager entityManager;

    @Test
    void anInProgressRowRoundTripsThroughRealMysql() {
        PolicyRecord saved = records.saveAndFlush(new PolicyRecord("APP-1", "pol-0000000001"));

        PolicyRecord reloaded = records.findById(saved.getApplicationId()).orElseThrow();
        assertThat(reloaded.getProcessingStatus()).isEqualTo("IN_PROGRESS");
        assertThat(reloaded.getSubmittedAt()).isNotNull();
    }

    @Test
    void aCompleteDecisionAndItsRuleJsonRoundTripThroughRealMysql() {
        PolicyRecord record = new PolicyRecord("APP-DECIDED", "pol-0000000002");
        record.completeDecision(new DecisionResult(
                PolicyOutcome.APPROVED,
                PolicyOutcome.APPROVED,
                List.of(
                        RuleResult.existingProduct(true, true, List.of()),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(false, 1, List.of("POL_ALL_CHECKS_PASSED")))));
        records.saveAndFlush(record);
        entityManager.clear();

        PolicyRecord reloaded = records.findById("APP-DECIDED").orElseThrow();
        assertThat(reloaded.getOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(reloaded.getMachineOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(reloaded.getRuleResults()).hasSize(4);
        assertThat(reloaded.getRuleResults().get(3).reasonCodes())
                .containsExactly("POL_ALL_CHECKS_PASSED");
    }
}
