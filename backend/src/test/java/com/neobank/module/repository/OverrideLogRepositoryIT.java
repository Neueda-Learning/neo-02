package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.service.OverrideCaseWriter;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Proves UC06 entity mappings and the atomic update/audit write against deployed MySQL. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OverrideLogRepositoryIT {

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
    private PolicyRecordRepository records;

    @Autowired
    private OverrideLogRepository overrides;

    @Autowired
    private OverrideCaseWriter writer;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearCases() {
        overrides.deleteAll();
        records.deleteAll();
    }

    @Test
    void overrideAndAuditRoundTripThroughRealMysql() {
        PolicyRecord record = new PolicyRecord("MYSQL-OVERRIDE", "pol-mysql-override");
        record.completeDecision(new DecisionResult(
                PolicyOutcome.REJECTED,
                PolicyOutcome.REJECTED,
                List.of(RuleResult.existingProduct(
                        false,
                        true,
                        List.of("POL_EXISTING_PRODUCT_HELD")))));
        records.saveAndFlush(record);

        writer.apply(
                "MYSQL-OVERRIDE",
                PolicyOutcome.APPROVED,
                "registry entry stale",
                "b.dimovski");
        entityManager.clear();

        PolicyRecord updated = records.findById("MYSQL-OVERRIDE").orElseThrow();
        assertThat(updated.getOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(updated.getMachineOutcome()).isEqualTo(PolicyOutcome.REJECTED);
        assertThat(updated.getRuleResults()).hasSize(1);
        assertThat(updated.getDecidedBy()).isEqualTo("b.dimovski");

        assertThat(overrides.findByApplicationIdOrderByOverriddenAtAscIdAsc("MYSQL-OVERRIDE"))
                .singleElement()
                .satisfies(audit -> {
                    assertThat(audit.getOldOutcome()).isEqualTo(PolicyOutcome.REJECTED);
                    assertThat(audit.getNewOutcome()).isEqualTo(PolicyOutcome.APPROVED);
                    assertThat(audit.getReason()).isEqualTo("registry entry stale");
                    assertThat(audit.getOperator()).isEqualTo("b.dimovski");
                    assertThat(audit.getOverriddenAt()).isNotNull();
                });
    }
}
