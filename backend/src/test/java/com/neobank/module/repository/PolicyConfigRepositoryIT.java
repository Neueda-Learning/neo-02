package com.neobank.module.repository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.neobank.module.model.PolicyConfig;
import com.neobank.module.service.PolicyConfigWriter;

/** Verifies the UC07/UC08 entity, JSON converters and Liquibase v1 seed against real MySQL. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
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

    @Autowired
    PolicyConfigWriter writer;

    @Test
    void theSeededV1DocumentRoundTripsThroughRealMysqlJson() {
        PolicyConfig v1 = configs.findById(1).orElseThrow();

        assertThat(v1.getSupportedResidencies()).containsExactly("GB", "IE", "PL", "DE", "FR", "ES", "NL");
        assertThat(v1.getExcludedResidencies()).containsExactly("US");
        assertThat(v1.getRestrictionList()).hasSize(2);
        assertThat(v1.getSampleEvery()).isEqualTo(7);
    }

    @Test
    void publishingANewVersionThroughTheWriterNeverTouchesTheOldOne() {
        PolicyConfig before = current();
        long countBefore = configs.count();
        PolicyConfig published = writer.publish(
                List.of("GB"), List.of("US"),
                List.of(new PolicyConfig.RestrictionEntry("Jane Doe", "1990-01-01", "test")),
                3);

        PolicyConfig v1 = configs.findById(1).orElseThrow();
        assertThat(v1.getSampleEvery()).isEqualTo(7);
        assertThat(published.getVersion()).isEqualTo(before.getVersion() + 1);
        assertThat(published.getSampleEvery()).isEqualTo(3);
        assertThat(configs.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void concurrentPublishersAllocateDistinctConsecutiveVersionsInMysql() throws Exception {
        int versionBefore = current().getVersion();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<PolicyConfig> first = pool.submit(() -> publishAfterBarrier(
                    ready, start, List.of("GB", "IE"), 29));
            Future<PolicyConfig> second = pool.submit(() -> publishAfterBarrier(
                    ready, start, List.of("GB", "DE"), 31));

            ready.await();
            start.countDown();

            assertThat(Set.of(first.get().getVersion(), second.get().getVersion()))
                    .containsExactlyInAnyOrder(versionBefore + 1, versionBefore + 2);
        }

        assertThat(current().getVersion()).isEqualTo(versionBefore + 2);
    }

    private PolicyConfig publishAfterBarrier(CountDownLatch ready, CountDownLatch start,
                                             List<String> supported, int sampleEvery) throws Exception {
        ready.countDown();
        start.await();
        return writer.publish(supported, List.of("US"), List.of(), sampleEvery);
    }

    private PolicyConfig current() {
        return configs.findFirstByOrderByVersionDesc().orElseThrow();
    }

    // ── UC08 tests ────────────────────────────────────────────────────────────

    @Test
    void versionsAreReturnedOldestFirst() {
        PolicyConfig published = writer.publish(List.of("GB", "FR"), List.of("US"), List.of(), 37);

        List<PolicyConfig> all = configs.findAllByOrderByVersionAsc();

        assertThat(all.get(0).getVersion()).isEqualTo(1);
        assertThat(all.get(all.size() - 1).getVersion()).isEqualTo(published.getVersion());
        assertThat(all).extracting(PolicyConfig::getVersion).isSorted();
    }

    @Test
    void historyIsNeverEmpty_seedGuaranteesV1() {
        List<PolicyConfig> all = configs.findAllByOrderByVersionAsc();
        assertThat(all).isNotEmpty();
        assertThat(all.get(0).getVersion()).isEqualTo(1);
    }

    @Test
    void afterUc07DemoInsertHistoryShowsV1AndV2WithV2Current() {
        PolicyConfig published = writer.publish(
                List.of("GB", "IE"), List.of("US"),
                List.of(new PolicyConfig.RestrictionEntry("Victor Sable", "1978-03-02", "prior fraud loss")),
                41);

        List<PolicyConfig> all = configs.findAllByOrderByVersionAsc();
        int maxVersion = all.stream().mapToInt(PolicyConfig::getVersion).max().orElseThrow();

        assertThat(all.get(0).getVersion()).isEqualTo(1);
        assertThat(maxVersion).isEqualTo(published.getVersion());
    }
}
