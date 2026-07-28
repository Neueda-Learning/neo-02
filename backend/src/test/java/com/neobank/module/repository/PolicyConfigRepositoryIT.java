package com.neobank.module.repository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.neobank.module.model.PolicyConfig;

/** Verifies the UC07 entity, JSON converters and Liquibase v1 seed against real MySQL. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PolicyConfigRepositoryIT {

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
    PolicyConfigRepository configs;

    @Test
    void theSeededV1DocumentRoundTripsThroughRealMysqlJson() {
        PolicyConfig v1 = configs.findById(1).orElseThrow();

        assertThat(v1.getSupportedResidencies()).containsExactly("GB", "IE", "PL", "DE", "FR", "ES", "NL");
        assertThat(v1.getExcludedResidencies()).containsExactly("US");
        assertThat(v1.getRestrictionList()).hasSize(2);
        assertThat(v1.getSampleEvery()).isEqualTo(7);
    }

    @Test
    void publishingANewVersionNeverTouchesTheOldOne() {
        PolicyConfig v2 = configs.saveAndFlush(new PolicyConfig(2,
                List.of("GB"), List.of("US"),
                List.of(new PolicyConfig.RestrictionEntry("Jane Doe", "1990-01-01", "test")),
                3));

        PolicyConfig v1 = configs.findById(1).orElseThrow();
        assertThat(v1.getSampleEvery()).isEqualTo(7);
        assertThat(v2.getSampleEvery()).isEqualTo(3);
        assertThat(configs.count()).isEqualTo(2);
    }
}
