package com.neobank.module.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Pageable;

import com.neobank.module.dto.CaseSearchResult;
import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;

class ApplicationServiceTest {

    private PolicyRecordWriter writer;
    private PolicyRecordRepository records;
    private OrchestratorClient orchestrator;
    private List<Runnable> scheduled;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        writer = mock(PolicyRecordWriter.class);
        records = mock(PolicyRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        scheduled = new ArrayList<>();
        Executor executor = scheduled::add;
        service = new ApplicationService(executor, writer, records, orchestrator);
    }

    private static ApplicationRequest request(String id) {
        return new ApplicationRequest(id, "corr-1", "check-policy", null);
    }

    @Test
    void commitsTheRowBeforeSchedulingTheWorker() {
        when(writer.createIfAbsent("SIM-01", null)).thenAnswer(invocation -> {
            assertThat(scheduled).isEmpty();
            return true;
        });

        service.accept(request("SIM-01"));

        verify(writer).createIfAbsent("SIM-01", null);
        assertThat(scheduled).hasSize(1);
    }

    @Test
    void duplicateRequestIsNotScheduledAgain() {
        when(writer.createIfAbsent("SIM-01", null)).thenReturn(false);

        service.accept(request("SIM-01"));

        assertThat(scheduled).isEmpty();
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schedulingFailureDoesNotUndoTheDurableAcceptance() {
        PolicyRecordWriter successfulWriter = mock(PolicyRecordWriter.class);
        when(successfulWriter.createIfAbsent("SIM-04", null)).thenReturn(true);
        Executor rejectingExecutor = task -> {
            throw new IllegalStateException("worker pool unavailable");
        };
        ApplicationService acceptingService =
                new ApplicationService(rejectingExecutor, successfulWriter, records, orchestrator);

        acceptingService.accept(request("SIM-04"));

        verify(successfulWriter).createIfAbsent("SIM-04", null);
    }

    @Test
    void boardShowsTheDurableInProgressRecord() {
        PolicyRecord row = new PolicyRecord("SIM-01", "pol-1234567890");
        when(records.findTop10ByOrderByCreatedAtDescApplicationIdDesc()).thenReturn(List.of(row));

        List<PolicyRecordView> result = service.findAll();

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.applicationId()).isEqualTo("SIM-01");
            assertThat(view.outcome()).isNull(); // outcome is null initially
            assertThat(view.sampled()).isFalse();
            assertThat(view.reasonCount()).isEqualTo(0);
        });
    }

    @Test
    void searchApplicationsReturnsEmptyListForBlankOrNullQuery() {
        assertThat(service.searchApplications(null)).isEmpty();
        assertThat(service.searchApplications("")).isEmpty();
        assertThat(service.searchApplications("   ")).isEmpty();
    }

    @Test
    void searchApplicationsDelegatesToRepositoryWithPageCappedAtTen() {
        PolicyRecord row = new PolicyRecord("APP-001", "ref-001");
        when(records.findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("APP"), any(Pageable.class))).thenReturn(List.of(row));

        List<PolicyRecordView> results = service.searchApplications("APP");

        assertThat(results).singleElement()
                .satisfies(v -> assertThat(v.applicationId()).isEqualTo("APP-001"));
    }

    @Test
    void searchCasesReturnsEmptyResultForBlankOrNullQuery() {
        assertThat(service.searchCases(null, 10).results()).isEmpty();
        assertThat(service.searchCases(null, 10).more()).isFalse();
        assertThat(service.searchCases("   ", 10).results()).isEmpty();
    }

    @Test
    void searchCasesFindsByApplicationIdWithoutEverCallingTheOrchestrator() {
        PolicyRecord row = new PolicyRecord("APP-777", "ref-777");
        when(records.findById("APP-777")).thenReturn(java.util.Optional.of(row));

        CaseSearchResult result = service.searchCases("APP-777", 10);

        assertThat(result.results()).singleElement()
                .satisfies(v -> assertThat(v.applicationId()).isEqualTo("APP-777"));
        assertThat(result.more()).isFalse();
        verify(orchestrator, never()).searchApplicationIdsByName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void searchCasesFindsByLocalApplicantFullNameBeforeAskingTheOrchestrator() {
        when(records.findById("Maria")).thenReturn(java.util.Optional.empty());
        PolicyRecord row = new PolicyRecord("APP-001", "ref-001");
        when(records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("Maria"), any(Pageable.class))).thenReturn(List.of(row));

        CaseSearchResult result = service.searchCases("Maria", 10);

        assertThat(result.results()).singleElement()
                .satisfies(v -> assertThat(v.applicationId()).isEqualTo("APP-001"));
        assertThat(result.more()).isFalse();
        verify(orchestrator, never()).searchApplicationIdsByName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void searchCasesFlagsMoreWhenLocalNameMatchesExceedTheLimit() {
        when(records.findById("Nowak")).thenReturn(java.util.Optional.empty());
        List<PolicyRecord> elevenRows = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            elevenRows.add(new PolicyRecord("APP-%03d".formatted(i), "ref-%03d".formatted(i)));
        }
        when(records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("Nowak"), any(Pageable.class))).thenReturn(elevenRows);

        CaseSearchResult result = service.searchCases("Nowak", 10);

        assertThat(result.results()).hasSize(10);
        assertThat(result.more()).isTrue();
    }

    @Test
    void searchCasesFallsBackToTheOrchestratorWhenNoLocalNameMatches() {
        when(records.findById("Sofia")).thenReturn(java.util.Optional.empty());
        when(records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("Sofia"), any(Pageable.class))).thenReturn(List.of());
        when(orchestrator.searchApplicationIdsByName("Sofia")).thenReturn(List.of("APP-002"));
        PolicyRecord row = new PolicyRecord("APP-002", "ref-002");
        when(records.findByApplicationIdInOrderBySubmittedAtDesc(List.of("APP-002")))
                .thenReturn(List.of(row));

        CaseSearchResult result = service.searchCases("Sofia", 10);

        assertThat(result.results()).singleElement()
                .satisfies(v -> assertThat(v.applicationId()).isEqualTo("APP-002"));
        assertThat(result.more()).isFalse();
        verify(orchestrator).searchApplicationIdsByName("Sofia");
    }

    @Test
    void searchCasesFlagsMoreWhenTheOrchestratorResolvesMoreIdsThanTheLimit() {
        when(records.findById("Common")).thenReturn(java.util.Optional.empty());
        when(records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("Common"), any(Pageable.class))).thenReturn(List.of());
        List<String> elevenIds = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            elevenIds.add("APP-%03d".formatted(i));
        }
        when(orchestrator.searchApplicationIdsByName("Common")).thenReturn(elevenIds);
        List<String> firstTen = elevenIds.subList(0, 10);
        List<PolicyRecord> tenRows = firstTen.stream()
                .map(id -> new PolicyRecord(id, "ref-" + id))
                .toList();
        when(records.findByApplicationIdInOrderBySubmittedAtDesc(firstTen)).thenReturn(tenRows);

        CaseSearchResult result = service.searchCases("Common", 10);

        assertThat(result.results()).hasSize(10);
        assertThat(result.more()).isTrue();
    }

    @Test
    void searchCasesFallsBackToApplicationIdSubstringWhenNameResolvesToNothing() {
        when(records.findById("ZZZ")).thenReturn(java.util.Optional.empty());
        when(records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("ZZZ"), any(Pageable.class))).thenReturn(List.of());
        when(orchestrator.searchApplicationIdsByName("ZZZ")).thenReturn(List.of());
        PolicyRecord row = new PolicyRecord("ZZZ-001", "ref-zzz");
        when(records.findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDesc(
                eq("ZZZ"), any(Pageable.class))).thenReturn(List.of(row));

        CaseSearchResult result = service.searchCases("ZZZ", 10);

        assertThat(result.results()).singleElement()
                .satisfies(v -> assertThat(v.applicationId()).isEqualTo("ZZZ-001"));
        assertThat(result.more()).isFalse();
    }
}
