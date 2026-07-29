package com.neobank.module.service;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * UC03 live applicant hydration.
 *
 * <p>No repository is injected here by design: this path must not read or write MySQL, and the
 * orchestrator remains the single owner of applicant data.</p>
 */
@Service
public class ApplicantService {

    private final OrchestratorClient orchestrator;

    public ApplicantService(OrchestratorClient orchestrator) {
        this.orchestrator = orchestrator;
    }

    public Application find(String applicationId) {
        try {
            Application application = orchestrator.application(applicationId);
            if (application == null) {
                throw new ApplicantUnavailableException(applicationId);
            }
            return application;
        } catch (RestClientException exception) {
            throw new ApplicantUnavailableException(applicationId, exception);
        }
    }
}
