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

import com.neobank.module.dto.CaseSearchResult;
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
