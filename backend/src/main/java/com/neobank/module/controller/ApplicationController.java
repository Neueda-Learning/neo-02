package com.neobank.module.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.neobank.module.dto.PolicyRecordView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.service.ApplicationService;

import jakarta.validation.Valid;

/**
 * This module's entire HTTP surface: one endpoint the orchestrator calls, one your own UI reads.
 *
 * <p><b>Accept now, work later.</b> {@code POST} answers {@code 202} immediately and hands the
 * application to {@link ApplicationService} — which does the work off the request thread and then
 * PUTs the outcome back to the orchestrator. Never do the work inside
 * {@link #processApplication}: the orchestrator is holding a connection open, and a module that
 * blocks turns a fast journey into a slow one.</p>
 *
 * <p>Add the endpoints your operator screen needs — a search, a manual override, a detail lookup —
 * here or in a new controller. Leave the {@code POST} alone: its shape is the contract.</p>
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applications;
    private final String serviceId;

    public ApplicationController(ApplicationService applications,
                                 @Value("${service.id:neo02}") String serviceId) {
        this.applications = applications;
        this.serviceId = serviceId;
    }

    /**
     * The contract entry point. {@code 202} means "received and working on it" — the real answer
     * arrives later, as a status update on the application.
     *
     * <p>{@code @Valid} rejects an envelope with no {@code applicationId} or command as {@code 400}
     * before any work starts. Everything
     * else is accepted, <em>including</em> malformed dates and unknown product codes — judging those
     * is the module's job, and a {@code 400} would rob it of the chance to say which field was
     * wrong.</p>
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> processApplication(
            @Valid @RequestBody ApplicationRequest request) {
        applications.accept(request);
        return ResponseEntity.accepted().body(ack(request));
    }

    /**
     * The {@code 202} body — the shape in {@code api-contract.md} §2, spelled out here rather than
     * in a record of its own. Three of its four fields are a constant or an echo of the request,
     * the orchestrator throws the body away (it dispatches with {@code toBodilessEntity}), and the
     * sibling acknowledgements in the orchestrator and the sidecar are inline maps too.
     * {@code ApplicationControllerTest} pins all four fields.
     *
     * <p><b>{@code LinkedHashMap}, not {@code Map.of}.</b> This keeps the contract's field order
     * explicit and readable.</p>
     */
    private Map<String, Object> ack(ApplicationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "in-progress");
        body.put("applicationId", request.applicationId());
        body.put("serviceId", serviceId);
        body.put("command", request.command());
        return body;
    }

    /**
     * Everything this module has answered, newest first. Read by this module's own UI; the
     * orchestrator never calls it.
     *
     * <p>Optional {@code ?q=} parameter: when present (even if empty), delegates to
     * {@link ApplicationService#searchApplications} which searches the full table and caps at 10.
     * An empty {@code q} returns {@code []} — the board is empty until the user types.
     * Without {@code q}, {@code ?page=} selects a 10-row page. Pagination metadata is returned in
     * {@code X-Page}, {@code X-More-Results}, and {@code X-Total-Count}; the JSON body remains the
     * same array shape used by existing clients.</p>
     */
    @GetMapping
    public ResponseEntity<List<PolicyRecordView>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        if (q != null) {
            return ResponseEntity.ok(applications.searchApplications(q));
        }
        Page<PolicyRecordView> result = applications.findAll(page, 10);
        return ResponseEntity.ok()
                .header("X-Page", String.valueOf(result.getNumber()))
                .header("X-More-Results", String.valueOf(result.hasNext()))
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }
}
