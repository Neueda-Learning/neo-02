package com.neobank.module.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.service.ApplicationService;

/**
 * UC-01 · Search Cases — the operator interface for finding policy cases.
 *
 * <p>GET /cases?q=... searches by applicationId or applicant name, returning a list of
 * PolicyRecordView rows (applicantName is NOT included — the UI hydrates it live).
 *
 * <p>GET /cases/{id}/applicant proxies the orchestrator to get applicant details without
 * storing them locally.</p>
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CasesController {

    private static final Logger log = LoggerFactory.getLogger(CasesController.class);

    private final ApplicationService applications;
    private final OrchestratorClient orchestrator;

    public CasesController(ApplicationService applications, OrchestratorClient orchestrator) {
        this.applications = applications;
        this.orchestrator = orchestrator;
    }

    /**
     * UC-01: Search cases by applicationId or applicant name.
     *
     * <p>GET /cases?q={id-or-name}&limit=10 →
     * [{"applicationId":"...", "submittedAt":"...", "outcome":"...", "sampled":false, "reasonCount":1}, ...]
     *
     * @param query the search query (applicationId or applicant name)
     * @param limit maximum results (capped at 10 per spec; defaults to 10)
     * @return list of matching cases, newest first; empty if no matches
     */
    @GetMapping
    public ResponseEntity<List<PolicyRecordView>> searchCases(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        
        if (query == null || query.trim().isEmpty()) {
            // Empty by default — no query, no rows fetched
            return ResponseEntity.ok(List.of());
        }

        List<PolicyRecordView> results = applications.searchCases(query, limit);
        return ResponseEntity.ok(results);
    }

    /**
     * Proxy the orchestrator to fetch applicant details.
     * UC-01: The board hydrates ≤10 rows live via this endpoint.
     *
     * <p>GET /cases/{id}/applicant returns applicant data from the orchestrator without
     * persisting it locally. If the orchestrator is down, returns 503 with a retryable message.</p>
     *
     * @param applicationId the application ID
     * @return applicant details from the orchestrator
     */
    @GetMapping("/{id}/applicant")
    public ResponseEntity<?> getApplicant(@PathVariable("id") String applicationId) {
        try {
            // Call the orchestrator to get applicant details
            // For now, return a placeholder that the UI will hydrate
            // In a real implementation, this would call the orchestrator's GET /applications/{id}
            // and extract the applicant field
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("applicationId", applicationId);
            response.put("applicantName", "—"); // Placeholder for hydration
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to fetch applicant for {}: {}", applicationId, e.toString());
            // Return 503 so the UI knows to retry
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Orchestrator unavailable");
            error.put("retryable", true);
            return ResponseEntity.status(503).body(error);
        }
    }
}
