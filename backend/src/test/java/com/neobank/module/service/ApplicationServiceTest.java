package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyConfigDocument;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.model.RuleResult;
import com.neobank.module.repository.PolicyConfigReader;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ApplicationServiceTest {

    private PolicyRecordWriter writer;
    private PolicyRecordRepository records;
    private PolicyDecisionWriter decisions;
    private PolicyConfigReader configs;
    private RegistryLookupService registry;
    private PolicyRuleEngine rules;
    private OrchestratorClient orchestrator;
    private List<Runnable> scheduled;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        writer = mock(PolicyRecordWriter.class);
        records = mock(PolicyRecordRepository.class);
        decisions = mock(PolicyDecisionWriter.class);
        configs = mock(PolicyConfigReader.class);
        registry = mock(RegistryLookupService.class);
        rules = mock(PolicyRuleEngine.class);
        orchestrator = mock(OrchestratorClient.class);
        scheduled = new ArrayList<>();
        service = service(scheduled::add);
    }

    @Test
    void commitsTheRowBeforeSchedulingTheWorker() {
        when(writer.createIfAbsent("SIM-01")).thenAnswer(invocation -> {
            assertThat(scheduled).isEmpty();
            return true;
        });

        service.accept(request("SIM-01"));

        verify(writer).createIfAbsent("SIM-01");
        assertThat(scheduled).hasSize(1);
    }

    @Test
    void inProgressDuplicateIsNotScheduledOrReprocessed() {
        when(writer.createIfAbsent("SIM-01")).thenReturn(false);
        when(records.findById("SIM-01"))
                .thenReturn(Optional.of(new PolicyRecord("SIM-01", "pol-1234567890")));

        service.accept(request("SIM-01"));

        assertThat(scheduled).isEmpty();
        verify(registry, never()).lookup(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(rules, never()).decide(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void decidedDuplicateOnlyReplaysTheStoredCallback() {
        PolicyRecord decided = decidedRecord("SIM-02");
        when(writer.createIfAbsent("SIM-02")).thenReturn(false);
        when(records.findById("SIM-02")).thenReturn(Optional.of(decided));

        service.accept(request("SIM-02"));
        assertThat(scheduled).hasSize(1);
        scheduled.getFirst().run();

        verify(orchestrator).applicationStatusUpdate(
                "SIM-02", Decision.ACCEPTED, PolicyRuleEngine.ALL_CHECKS_PASSED);
        verify(registry, never()).lookup(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(rules, never()).decide(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void workerPinsConfigStoresDecisionThenReportsIt() {
        PolicyConfigDocument config = config();
        RegistryLookupService.RegistrySnapshot registryResult =
                RegistryLookupService.RegistrySnapshot.available(false);
        DecisionResult result = approvedResult();
        when(decisions.pinContext("SIM-03"))
                .thenReturn(new PolicyDecisionWriter.DecisionContext(1, 1L, false));
        when(configs.findVersion(1)).thenReturn(config);
        when(registry.lookup("SIM-03", null, null)).thenReturn(registryResult);
        when(rules.decide(null, config, registryResult, 1L)).thenReturn(result);
        when(decisions.complete("SIM-03", result)).thenReturn(true);

        service.processApplication(request("SIM-03"));

        verify(decisions).complete("SIM-03", result);
        verify(orchestrator).applicationStatusUpdate(
                "SIM-03", Decision.ACCEPTED, PolicyRuleEngine.ALL_CHECKS_PASSED);
    }

    @Test
    void schedulingFailureDoesNotUndoTheDurableAcceptance() {
        when(writer.createIfAbsent("SIM-04")).thenReturn(true);
        ApplicationService rejectingService = service(task -> {
            throw new IllegalStateException("worker pool unavailable");
        });

        rejectingService.accept(request("SIM-04"));

        verify(writer).createIfAbsent("SIM-04");
    }

    @Test
    void boardShowsTheEffectiveOutcomeAfterDecision() {
        PolicyRecord row = decidedRecord("SIM-05");
        PageRequest firstPage = PageRequest.of(0, 10);
        when(records.findAllByOrderByCreatedAtDescApplicationIdDesc(firstPage))
                .thenReturn(new PageImpl<>(List.of(row), firstPage, 1));

        Page<PolicyRecordView> result = service.findAll(0, 10);

        assertThat(result.getContent()).singleElement().satisfies(view -> {
            assertThat(view.applicationId()).isEqualTo("SIM-05");
            assertThat(view.status()).isEqualTo("APPROVED");
            assertThat(view.reference()).isEqualTo("pol-1234567890");
        });
    }

    @Test
    void boardCapsPageSizeAtTen() {
        PageRequest cappedPage = PageRequest.of(2, 10);
        when(records.findAllByOrderByCreatedAtDescApplicationIdDesc(cappedPage))
                .thenReturn(Page.empty(cappedPage));

        service.findAll(2, 50);

        verify(records).findAllByOrderByCreatedAtDescApplicationIdDesc(cappedPage);
    }

    @Test
    void capturesApplicantNameForUc01SearchAtIntake() {
        when(writer.createIfAbsent("SIM-06", "Maria Nowak")).thenReturn(true);

        service.accept(namedRequest("SIM-06", "Maria Nowak"));

        verify(writer).createIfAbsent("SIM-06", "Maria Nowak");
        assertThat(scheduled).hasSize(1);
    }

    @Test
    void uc01NameSearchCapsLocalMatchesAndReportsMore() {
        when(records.findById("Maria")).thenReturn(Optional.empty());
        List<PolicyRecord> matches = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(index -> new PolicyRecord(
                        "SIM-%02d".formatted(index), "pol-%010d".formatted(index)))
                .toList();
        when(records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                org.mockito.ArgumentMatchers.eq("Maria"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(matches);

        CaseSearchResult result = service.searchCases("Maria", 10);

        assertThat(result.results()).hasSize(10);
        assertThat(result.more()).isTrue();
        verify(orchestrator, never()).searchApplicationIdsByName("Maria");
    }

    private ApplicationService service(Executor executor) {
        return new ApplicationService(
                executor, writer, records, decisions, configs, registry, rules, orchestrator);
    }

    private static ApplicationRequest request(String id) {
        return new ApplicationRequest(id, "corr-1", "check-policy", null);
    }

    private static ApplicationRequest namedRequest(String id, String fullName) {
        Application.Applicant applicant = new Application.Applicant(
                fullName, null, null, null, null, null, null, null, null, null, null);
        Application application = new Application(
                id, null, null, applicant, null, null, null, null, null, null);
        return new ApplicationRequest(id, "corr-1", "check-policy", application);
    }

    private PolicyRecord decidedRecord(String id) {
        PolicyRecord row = new PolicyRecord(id, "pol-1234567890");
        row.completeDecision(approvedResult());
        return row;
    }

    private DecisionResult approvedResult() {
        return new DecisionResult(
                PolicyOutcome.APPROVED,
                PolicyOutcome.APPROVED,
                List.of(
                        RuleResult.existingProduct(true, true, List.of()),
                        RuleResult.taxResidency(true, "SUPPORTED", List.of()),
                        RuleResult.restrictionList(true, List.of()),
                        RuleResult.sampling(
                                false, 1, List.of(PolicyRuleEngine.ALL_CHECKS_PASSED))));
    }

    private PolicyConfigDocument config() {
        return new PolicyConfigDocument(
                1, List.of("GB"), List.of("US"), List.of(), 7);
    }
}
