package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.PolicyConfig;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:decisionwriter;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class PolicyDecisionWriterTest {

    @Autowired
    private PolicyRecordWriter intake;

    @Autowired
    private PolicyDecisionWriter decisions;

    @Autowired
    private PolicyConfigWriter configWriter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCases() {
        jdbc.update("DELETE FROM policy_record");
    }

    @Test
    void pinningTheSameCaseTwiceReusesItsOriginalContext() {
        intake.createIfAbsent("PIN-IDEMPOTENT");

        PolicyDecisionWriter.DecisionContext first =
                decisions.pinContext("PIN-IDEMPOTENT");
        PolicyDecisionWriter.DecisionContext second =
                decisions.pinContext("PIN-IDEMPOTENT");

        assertThat(second.policyConfigVersion()).isEqualTo(first.policyConfigVersion());
        assertThat(second.samplingPosition()).isEqualTo(first.samplingPosition());
    }

    @Test
    void intakePinsTheLatestVersionPublishedThroughUc07() {
        PolicyConfig published = configWriter.publish(
                List.of("GB", "CA"), List.of("US"), List.of(), 11);

        assertThat(intake.createIfAbsent("PIN-UC07-CONFIG")).isTrue();

        assertThat(decisions.pinContext("PIN-UC07-CONFIG").policyConfigVersion())
                .isEqualTo(published.getVersion());
    }

    @Test
    void concurrentFirstDecisionsReceiveDistinctSamplingPositions() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PolicyDecisionWriter.DecisionContext> first =
                    executor.submit(() -> accept("PIN-CONCURRENT-A", ready, start));
            Future<PolicyDecisionWriter.DecisionContext> second =
                    executor.submit(() -> accept("PIN-CONCURRENT-B", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> positions = List.of(
                    first.get(5, TimeUnit.SECONDS).samplingPosition(),
                    second.get(5, TimeUnit.SECONDS).samplingPosition());
            assertThat(positions).doesNotHaveDuplicates();
        }
    }

    @Test
    void theTwentyFirstAcceptedApplicationIsApp1287() {
        assertThat(intake.createIfAbsent("app-1234")).isTrue();
        assertThat(intake.createIfAbsent("app-1240")).isTrue();
        assertThat(intake.createIfAbsent("app-1242")).isTrue();
        IntStream.rangeClosed(4, 20)
                .forEach(position ->
                        assertThat(intake.createIfAbsent("app-filler-" + position)).isTrue());
        assertThat(intake.createIfAbsent("app-1287")).isTrue();

        assertThat(decisions.pinContext("app-1234").samplingPosition()).isEqualTo(1);
        assertThat(decisions.pinContext("app-1287").samplingPosition()).isEqualTo(21);
    }

    private PolicyDecisionWriter.DecisionContext accept(
            String applicationId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        assertThat(intake.createIfAbsent(applicationId)).isTrue();
        return decisions.pinContext(applicationId);
    }
}
