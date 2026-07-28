package com.neobank.module.service;

import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyConfigDocument;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyConfigReader;
import com.neobank.module.repository.PolicyRecordRepository;
import com.neobank.module.service.PolicyDecisionWriter.DecisionContext;
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
    private final PolicyDecisionWriter decisions;
    private final PolicyConfigReader configs;
    private final RegistryLookupService registry;
    private final PolicyRuleEngine rules;
    private final OrchestratorClient orchestrator;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              PolicyRecordWriter writer,
                              PolicyRecordRepository records,
                              PolicyDecisionWriter decisions,
                              PolicyConfigReader configs,
                              RegistryLookupService registry,
                              PolicyRuleEngine rules,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.writer = writer;
        this.records = records;
        this.decisions = decisions;
        this.configs = configs;
        this.registry = registry;
        this.rules = rules;
        this.orchestrator = orchestrator;
    }

    /**
     * Persists synchronously, then hands the payload to the worker after commit. A duplicate never
     * runs rules or calls Registry again; once decided, it only replays the stored callback.
     */
    public void accept(ApplicationRequest request) {
        boolean inserted = writer.createIfAbsent(request.applicationId());
        if (inserted) {
            schedule(request.applicationId(), () -> processApplication(request));
            return;
        }

        log.info("Duplicate application {} acknowledged without re-processing",
                request.applicationId());
        records.findById(request.applicationId())
                .filter(PolicyRecord::isDecided)
                .ifPresent(record ->
                        schedule(request.applicationId(), () -> reportStored(record)));
    }

    /** Runs once for the first accepted request and stores the decision before callback. */
    void processApplication(ApplicationRequest request) {
        try {
            DecisionContext context = decisions.pinContext(request.applicationId());
            if (context.decided()) {
                records.findById(request.applicationId()).ifPresent(this::reportStored);
                return;
            }

            PolicyConfigDocument config = configs.findVersion(context.policyConfigVersion());
            Application application = request.application();
            Application.Applicant applicant = application == null ? null : application.applicant();
            RegistryLookupService.RegistrySnapshot registryResult = registry.lookup(
                    request.applicationId(),
                    applicant == null ? null : applicant.fullName(),
                    applicant == null ? null : applicant.dateOfBirth());
            DecisionResult result =
                    rules.decide(application, config, registryResult, context.samplingPosition());

            if (decisions.complete(request.applicationId(), result)) {
                report(request.applicationId(), result.outcome(), result.reasonCodes());
                log.info("DECIDED {} -> {} using config v{} at position {}",
                        request.applicationId(), result.outcome(),
                        context.policyConfigVersion(), context.samplingPosition());
            }
        } catch (RuntimeException failure) {
            log.error("Policy decision failed for {}", request.applicationId(), failure);
        }
    }

    private void schedule(String applicationId, Runnable task) {
        try {
            executor.execute(task);
        } catch (RuntimeException schedulingFailure) {
            log.error("Policy worker could not be scheduled for {}",
                    applicationId, schedulingFailure);
        }
    }

    private void reportStored(PolicyRecord record) {
        report(record.getApplicationId(), record.getOutcome(),
                record.getRuleResults().stream()
                        .flatMap(rule -> rule.reasonCodes().stream())
                        .distinct()
                        .toList());
    }

    private void report(String applicationId, PolicyOutcome outcome, List<String> reasonCodes) {
        orchestrator.applicationStatusUpdate(
                applicationId,
                callbackStatus(outcome),
                String.join(", ", reasonCodes));
    }

    private Decision callbackStatus(PolicyOutcome outcome) {
        return switch (outcome) {
            case APPROVED -> Decision.ACCEPTED;
            case REJECTED -> Decision.REJECTED;
            case REFERRED -> Decision.REFERRED;
        };
    }

    @Transactional(readOnly = true)
    public List<PolicyRecordView> findAll() {
        return records.findTop10ByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(PolicyRecordView::of)
                .toList();
    }
}
