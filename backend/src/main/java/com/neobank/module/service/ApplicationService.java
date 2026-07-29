package com.neobank.module.service;

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
import com.neobank.module.repository.PolicyConfigReader;
import com.neobank.module.repository.PolicyRecordRepository;
import com.neobank.module.service.PolicyDecisionWriter.DecisionContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
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
        String applicantFullName = applicantFullName(request);
        boolean inserted = applicantFullName == null
                ? writer.createIfAbsent(request.applicationId())
                : writer.createIfAbsent(request.applicationId(), applicantFullName);
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

    /**
     * Reads {@code application.applicant.fullName} from the inbound payload, null-guarded at
     * every level since a malformed request can still omit {@code application} or
     * {@code applicant} entirely (see {@link ApplicationRequest#summary()}).
     */
    private static String applicantFullName(ApplicationRequest request) {
        if (request.application() == null || request.application().applicant() == null) {
            return null;
        }
        return request.application().applicant().fullName();
    }

    @Transactional(readOnly = true)
    public List<PolicyRecordView> findAll() {
        return records.findTop10ByOrderByCreatedAtDescApplicationIdDesc().stream()
                .map(PolicyRecordView::of)
                .toList();
    }

    /**
     * UC-00 board search: find applications whose id contains {@code query} (case-insensitive),
     * newest first, capped at 10.
     *
     * <p>Returns an empty list when the query is blank — the board is empty by default; the caller
     * should only invoke this when the user has typed something.</p>
     */
    @Transactional(readOnly = true)
    public List<PolicyRecordView> searchApplications(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return records.findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDesc(
                        query.trim(), PageRequest.of(0, 10))
                .stream()
                .map(PolicyRecordView::of)
                .toList();
    }

    /**
     * UC-01 search cases by id or applicant name.
     *
     * <p>If the query looks like an applicationId (contains non-space chars), search locally.
     * Otherwise it is treated as a name search: the locally captured {@code applicant_full_name}
     * column (populated at intake, see {@link #applicantFullName(ApplicationRequest)}) is tried
     * first; only when that yields nothing does the orchestrator get asked to resolve the name
     * to ids, covering records captured before that column existed.
     *
     * <p><b>Deviation from the v5 spec on file</b> (see {@code uc-01-search-cases.md}): the spec
     * says the schema holds zero applicant columns and every name search resolves through the
     * orchestrator first. This module persists {@code applicant_full_name} at intake (see
     * {@link PolicyRecordWriter}) and searches it locally before falling back to the
     * orchestrator — a deliberate, explicitly-requested change, kept as-is by product decision.
     *
     * @param query the search string (id or name)
     * @param limit the maximum number of results to return (capped at 10 per spec)
     * @return the matching rows (newest first, capped at {@code limit}) plus whether the true
     *         match count exceeded that cap
     */
    @Transactional(readOnly = true)
    public CaseSearchResult searchCases(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return new CaseSearchResult(List.of(), false);
        }

        String q = query.trim();
        limit = Math.max(1, Math.min(limit, 10)); // Cap at 10 per spec and keep positive

        // Try to search by application ID first (direct local lookup)
        Optional<PolicyRecord> byId = records.findById(q);
        if (byId.isPresent()) {
            return new CaseSearchResult(List.of(PolicyRecordView.of(byId.get())), false);
        }

        // Name search: try the locally captured applicant name first. One extra row is fetched
        // so a match past the cap can be reported as "more" without a separate count query.
        List<PolicyRecord> byName = records.findByApplicantFullNameContainingIgnoreCaseOrderBySubmittedAtDesc(
                q, PageRequest.of(0, limit + 1));
        if (!byName.isEmpty()) {
            return cappedResult(byName, limit);
        }

        // No local name match — resolve through the orchestrator (covers records captured
        // before applicant_full_name existed, and any orchestrator-only records).
        List<String> applicationIds = orchestrator.searchApplicationIdsByName(q);
        if (applicationIds.isEmpty()) {
            // Local resilience fallback (useful in sidecar/local where name search may be absent):
            // search application ids by substring, still capped and sorted newest first.
            List<PolicyRecord> byIdSubstring = records.findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDesc(
                    q, PageRequest.of(0, limit + 1));
            return cappedResult(byIdSubstring, limit);
        }

        // The orchestrator's full match count (before capping) is the true "more" signal.
        boolean more = applicationIds.size() > limit;
        List<String> limitedIds = more ? applicationIds.subList(0, limit) : applicationIds;

        // Fetch matching records from local table
        List<PolicyRecord> matches = records.findByApplicationIdInOrderBySubmittedAtDesc(limitedIds);
        List<PolicyRecordView> views = matches.stream()
                .limit(limit)
                .map(PolicyRecordView::of)
                .toList();
        return new CaseSearchResult(views, more);
    }

    /**
     * Trims a locally-fetched, over-by-one row list down to {@code limit} and reports whether
     * the extra row means there are more matches than the cap.
     */
    private static CaseSearchResult cappedResult(List<PolicyRecord> rows, int limit) {
        boolean more = rows.size() > limit;
        List<PolicyRecordView> views = rows.stream()
                .limit(limit)
                .map(PolicyRecordView::of)
                .toList();
        return new CaseSearchResult(views, more);
    }
}
