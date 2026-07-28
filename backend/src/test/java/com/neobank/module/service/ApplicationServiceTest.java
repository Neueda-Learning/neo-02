package com.neobank.module.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(writer.createIfAbsent("SIM-01")).thenAnswer(invocation -> {
            assertThat(scheduled).isEmpty();
            return true;
        });

        service.accept(request("SIM-01"));

        verify(writer).createIfAbsent("SIM-01");
        assertThat(scheduled).hasSize(1);
    }

    @Test
    void duplicateRequestIsNotScheduledAgain() {
        when(writer.createIfAbsent("SIM-01")).thenReturn(false);

        service.accept(request("SIM-01"));

        assertThat(scheduled).isEmpty();
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schedulingFailureDoesNotUndoTheDurableAcceptance() {
        PolicyRecordWriter successfulWriter = mock(PolicyRecordWriter.class);
        when(successfulWriter.createIfAbsent("SIM-04")).thenReturn(true);
        Executor rejectingExecutor = task -> {
            throw new IllegalStateException("worker pool unavailable");
        };
        ApplicationService acceptingService =
                new ApplicationService(rejectingExecutor, successfulWriter, records, orchestrator);

        acceptingService.accept(request("SIM-04"));

        verify(successfulWriter).createIfAbsent("SIM-04");
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
}
