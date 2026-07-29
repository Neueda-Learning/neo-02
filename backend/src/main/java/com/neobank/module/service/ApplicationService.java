package com.neobank.module.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.PolicyRecord;
import com.neobank.module.repository.PolicyRecordRepository;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final PolicyRecordWriter writer;
    private final PolicyRecordRepository records;
    private final OrchestratorClient orchestrator;

    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              PolicyRecordWriter writer,
                              PolicyRecordRepository records,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.writer = writer;
        this.records = records;
        this.orchestrator = orchestrator;
    }

    /**
     * Persist synchronously, then hand the payload to the worker only after commit. Duplicate
     * application ids are acknowledged but never scheduled a second time.
     */
    public void accept(ApplicationRequest request) {
        boolean inserted = writer.createIfAbsent(request.applicationId(), applicantFullName(request));
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
     * If the query looks like a name (multiple words or single name), resolve through the
     * orchestrator to get applicationIds, then search locally.
     *
     * @param query the search string (id or name)
     * @param limit the maximum number of results to return (capped at 10 per spec)
     * @return list of matching PolicyRecordView, newest first
     */
    @Transactional(readOnly = true)
    public List<PolicyRecordView> searchCases(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String q = query.trim();
        limit = Math.max(1, Math.min(limit, 10)); // Cap at 10 per spec and keep positive

        // Try to search by application ID first (direct local lookup)
        Optional<PolicyRecord> byId = records.findById(q);
        if (byId.isPresent()) {
            return List.of(PolicyRecordView.of(byId.get()));
        }

        // If not found by ID, treat as name search: resolve through orchestrator
        List<String> applicationIds = orchestrator.searchApplicationIdsByName(q);
        if (applicationIds.isEmpty()) {
            // Local resilience fallback (useful in sidecar/local where name search may be absent):
            // search application ids by substring, still capped and sorted newest first.
            return records.findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDesc(
                            q, PageRequest.of(0, limit))
                    .stream()
                    .map(PolicyRecordView::of)
                    .toList();
        }

        // Limit IDs to fetch
        List<String> limitedIds = applicationIds.size() > limit
                ? applicationIds.subList(0, limit)
                : applicationIds;

        // Fetch matching records from local table
        List<PolicyRecord> matches = records.findByApplicationIdInOrderBySubmittedAtDesc(limitedIds);
        return matches.stream()
                .limit(limit)
                .map(PolicyRecordView::of)
                .toList();
    }
}
