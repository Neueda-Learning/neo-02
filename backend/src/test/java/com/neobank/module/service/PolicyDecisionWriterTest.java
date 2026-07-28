package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    void concurrentFirstDecisionsReceiveDistinctSamplingPositions() throws Exception {
        intake.createIfAbsent("PIN-CONCURRENT-A");
        intake.createIfAbsent("PIN-CONCURRENT-B");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PolicyDecisionWriter.DecisionContext> first =
                    executor.submit(() -> pin("PIN-CONCURRENT-A", ready, start));
            Future<PolicyDecisionWriter.DecisionContext> second =
                    executor.submit(() -> pin("PIN-CONCURRENT-B", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> positions = List.of(
                    first.get(5, TimeUnit.SECONDS).samplingPosition(),
                    second.get(5, TimeUnit.SECONDS).samplingPosition());
            assertThat(positions).doesNotHaveDuplicates();
        }
    }

    private PolicyDecisionWriter.DecisionContext pin(
            String applicationId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return decisions.pinContext(applicationId);
    }
}
