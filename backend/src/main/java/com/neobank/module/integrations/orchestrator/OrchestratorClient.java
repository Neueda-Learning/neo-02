package com.neobank.module.integrations.orchestrator;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.neobank.module.model.Decision;
import com.neobank.module.model.PolicyOutcome;

/**
 * The outbound half of the contract: telling the orchestrator what this module decided.
 *
 * <p>{@code ApplicationController} is the way in, this is the way out. Everything in the
 * {@code orchestrator} package is the wire; everything else in the module is local.</p>
 */
@Component
public class OrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

    private final RestClient http;
    private final String serviceId;
    private final String applicationsUrl;

    public OrchestratorClient(RestClient http,
                              @Value("${service.id:neo02}") String serviceId,
                              @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.http = http;
        this.serviceId = serviceId;
        this.applicationsUrl = orchestratorUrl + "/api/v1/applications";
    }

    /**
     * Report the outcome: {@code PUT /api/v1/applications/{applicationId}}.
     *
     * <p>A {@code PUT} on the application, not a post to a mailbox — this is an update to the
     * status of something the orchestrator already has, which is why the id is in the URL and not
     * in the body.</p>
     *
     * <p><b>Failures are logged, never thrown.</b> The decision is already committed to our own
     * database, so re-throwing would roll nothing back and would only kill the worker thread. If
     * the orchestrator cannot be reached it treats the step as timed out — that is its job, not
     * ours.</p>
     */
    public void applicationStatusUpdate(String applicationId, Decision status, String comment) {
        ApplicationStatusUpdate body = new ApplicationStatusUpdate(serviceId, status.name(), comment);
        try {
            http.put()
                    .uri(applicationsUrl + "/" + applicationId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("REPORTED {} -> {}", applicationId, status);
        } catch (Exception e) {
            log.warn("Status update to the orchestrator failed for {}: {} — its timeout sweeper "
                    + "will notice", applicationId, e.toString());
        }
    }

    /**
     * Reports a UC04 human answer over the system's fixed three-field callback contract.
     * The local-manual origin and policy code travel in {@code comment}; the wire status remains
     * ACCEPTED/REJECTED as required by the orchestrator contract.
     */
    public void manualPolicyDecision(
            String applicationId, PolicyOutcome outcome, String reason) {
        Decision status = outcome == PolicyOutcome.APPROVED
                ? Decision.ACCEPTED
                : Decision.REJECTED;
        String code = outcome == PolicyOutcome.APPROVED
                ? "POL_MANUAL_APPROVED"
                : "POL_MANUAL_DECLINED";
        applicationStatusUpdate(applicationId, status, "local-manual " + code + ": " + reason);
    }

    /** Fetches the orchestrator-owned application live for the standard applicant proxy. */
    public Application getApplication(String applicationId) {
        return http.get()
                .uri(applicationsUrl + "/{applicationId}", applicationId)
                .retrieve()
                .body(Application.class);
    }

    /**
     * Search for application IDs by applicant name via the orchestrator.
     * UC-01 name search: GET /api/v1/applications?name={query} returns a list of application IDs
     * that match the given name.
     *
     * @param name the applicant name to search for
     * @return a list of matching application IDs, or empty list if none found or orchestrator
     *         is unreachable
     */
    public List<String> searchApplicationIdsByName(String name) {
        try {
            SearchApplicationsResponse response = http.get()
                    .uri(applicationsUrl + "?name={name}", name)
                    .retrieve()
                    .body(SearchApplicationsResponse.class);
            return response != null ? response.applicationIds() : List.of();
        } catch (RestClientException e) {
            log.warn("Name search to the orchestrator failed for '{}': {} — returning empty list",
                    name, e.toString());
            return List.of();
        }
    }

    /**
     * Fetch applicant full name for one application via orchestrator payload.
     *
     * <p>Expected shape contains <code>application.applicant.fullName</code>; missing fields
     * fall back to {@code "—"}.</p>
     *
     * @param applicationId application id
     * @return applicant full name or {@code "—"} when missing
     * @throws RestClientException when orchestrator is unreachable or non-2xx
     */
    @SuppressWarnings("unchecked")
    public String fetchApplicantName(String applicationId) {
        Map<String, Object> response = http.get()
                .uri(applicationsUrl + "/{applicationId}", applicationId)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return "—";
        }

        Object application = response.get("application");
        if (!(application instanceof Map<?, ?> applicationMap)) {
            return "—";
        }

        Object applicant = ((Map<String, Object>) applicationMap).get("applicant");
        if (!(applicant instanceof Map<?, ?> applicantMap)) {
            return "—";
        }

        Object fullName = ((Map<String, Object>) applicantMap).get("fullName");
        if (fullName == null) {
            return "—";
        }

        String name = String.valueOf(fullName).trim();
        return name.isEmpty() ? "—" : name;
    }

    /**
     * DTO for the orchestrator's search response.
     * Expected format: {"applicationIds": ["app-1", "app-2", ...]}
     */
    public record SearchApplicationsResponse(List<String> applicationIds) {
    }
}
