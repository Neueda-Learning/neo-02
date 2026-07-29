package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.PolicyRecordRepository;
import java.sql.Timestamp;
import java.time.Instant;
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
        "spring.datasource.url=jdbc:h2:mem:referralqueue;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class ReferralCaseWriterTest {

    @Autowired
    private PolicyRecordWriter intake;

    @Autowired
    private PolicyDecisionWriter machineDecisions;

    @Autowired
    private ReferralCaseWriter writer;

    @Autowired
    private ReferralQueueService queue;

    @Autowired
    private PolicyRecordRepository records;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCases() {
        jdbc.update("DELETE FROM policy_record");
    }

    @Test
    void claimIsIdempotentForOwnerAndConflictsForAnotherOperator() {
        makeReferral("claim-me", true, false, "2026-07-15T08:00:00Z");

        PolicyRecord first = writer.claim("claim-me", "s.chen");
        PolicyRecord replay = writer.claim("claim-me", "s.chen");

        assertThat(first.getClaimedBy()).isEqualTo("s.chen");
        assertThat(replay.getClaimedAt()).isEqualTo(first.getClaimedAt());
        assertThatThrownBy(() -> writer.claim("claim-me", "a.patel"))
                .isInstanceOf(ReferralConflictException.class);

        writer.release("claim-me", "s.chen");
        assertThat(writer.claim("claim-me", "a.patel").getClaimedBy()).isEqualTo("a.patel");
    }

    @Test
    void simultaneousReviewersProduceOneClaimAndOneConflict() throws Exception {
        makeReferral("race-case", true, false, "2026-07-15T08:00:00Z");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> raceClaim("s.chen", ready, start));
            Future<String> second = executor.submit(() -> raceClaim("a.patel", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CLAIMED", "CONFLICT");
        }
    }

    @Test
    void approvingApp1287KeepsTheMachineAnswerAndStoresHumanTrace() {
        makeReferral("app-1287", true, false, "2026-07-15T08:00:00Z");
        writer.claim("app-1287", "s.chen");

        var result = writer.decide(
                "app-1287", PolicyOutcome.APPROVED,
                "sampling QA — machine confirmed", "s.chen");
        PolicyRecord saved = records.findById("app-1287").orElseThrow();

        assertThat(result.changed()).isTrue();
        assertThat(saved.getOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(saved.getMachineOutcome()).isEqualTo(PolicyOutcome.APPROVED);
        assertThat(saved.getDecidedBy()).isEqualTo("s.chen");
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(saved.getDecisionReason()).isEqualTo("sampling QA — machine confirmed");
        assertThat(saved.getRuleResults()).hasSize(4);
        assertThat(queue.findOpenReferrals()).isEmpty();

        var replay = writer.decide(
                "app-1287", PolicyOutcome.APPROVED,
                "sampling QA — machine confirmed", "s.chen");
        assertThat(replay.changed()).isFalse();
        assertThat(replay.record().getDecidedAt()).isEqualTo(result.record().getDecidedAt());
    }

    @Test
    void fixtureReferenceTimeHasExactlyThreeOpenCasesWithRequiredCauses() {
        makeReferral("app-1287", true, false, "2026-07-15T06:00:00Z");
        makeReferral("app-sampled-2", true, false, "2026-07-15T07:00:00Z");
        makeReferral("app-registry", false, true, "2026-07-15T08:00:00Z");
        makeReferral("already-decided", true, false, "2026-07-15T05:00:00Z");
        writer.decide("already-decided", PolicyOutcome.REJECTED, "manual decline", "s.chen");

        var result = queue.findOpenReferrals();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(item -> item.referralCause())
                .containsExactly("sampled", "sampled", "registry-outage");
        assertThat(result).extracting(item -> item.applicationId())
                .containsExactly("app-1287", "app-sampled-2", "app-registry");
    }

    @Test
    void queueNeverReturnsMoreThanTenRows() {
        IntStream.rangeClosed(1, 12).forEach(index -> makeReferral(
                "queue-" + index, true, false,
                "2026-07-15T%02d:00:00Z".formatted(index)));

        assertThat(queue.findOpenReferrals()).hasSize(10);
    }

    @Test
    void queuePlacesUnclaimedRowsBeforeClaimedRowsThenUsesOldestFirst() {
        makeReferral("old-claimed", true, false, "2026-07-15T06:00:00Z");
        makeReferral("new-unclaimed", true, false, "2026-07-15T07:00:00Z");
        writer.claim("old-claimed", "s.chen");

        assertThat(queue.findOpenReferrals()).extracting(item -> item.applicationId())
                .containsExactly("new-unclaimed", "old-claimed");
    }

    private String raceClaim(
            String operator, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            writer.claim("race-case", operator);
            return "CLAIMED";
        } catch (ReferralConflictException expected) {
            return "CONFLICT";
        }
    }

    private void makeReferral(
            String applicationId, boolean sampled, boolean outage, String submittedAt) {
        assertThat(intake.createIfAbsent(applicationId)).isTrue();
        List<String> registryReasons = outage
                ? List.of(PolicyRuleEngine.REGISTRY_UNAVAILABLE)
                : List.of();
        assertThat(machineDecisions.complete(applicationId, new DecisionResult(
                PolicyOutcome.REFERRED,
                PolicyOutcome.APPROVED,
                List.of(
                        RuleResult.existingProduct(!outage, !outage, registryReasons),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(sampled, 7,
                                sampled ? List.of(PolicyRuleEngine.SAMPLED_FOR_REVIEW) : List.of())))))
                .isTrue();
        jdbc.update("UPDATE policy_record SET submitted_at = ? WHERE application_id = ?",
                Timestamp.from(Instant.parse(submittedAt)), applicationId);
    }
}
