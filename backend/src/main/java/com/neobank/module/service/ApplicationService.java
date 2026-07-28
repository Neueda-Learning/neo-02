package com.neobank.module.service;

import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.repository.PolicyRecordRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final PolicyRecordWriter writer;
    private final PolicyRecordRepository records;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              PolicyRecordWriter writer,
                              PolicyRecordRepository records) {
        this.executor = executor;
        this.writer = writer;
        this.records = records;
    }

    /**
     * Persist synchronously, then hand the payload to the worker only after commit. Duplicate
     * application ids are acknowledged but never scheduled a second time.
     */
    public void accept(ApplicationRequest request) {
        boolean inserted = writer.createIfAbsent(request.applicationId());
        if (inserted) {
            try {
                executor.execute(() -> processApplication(request));
            } catch (RuntimeException schedulingFailure) {
                // The committed row remains the hand-off point and can be recovered independently.
                log.error("Policy worker could not be scheduled for {}",
                        request.applicationId(), schedulingFailure);
            }
        } else {
            log.info("Duplicate application {} acknowledged without re-processing",
                    request.applicationId());
        }
    }

    /** UC00 establishes the durable hand-off; policy decisions are implemented by later UCs. */
    void processApplication(ApplicationRequest request) {
        log.info("Policy case ready for decision — {}", request.summary());
    }

    @Transactional(readOnly = true)
    public List<PolicyRecordView> findAll() {
        return records.findTop10ByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(PolicyRecordView::of)
                .toList();
    }
}
