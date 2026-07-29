package com.neobank.module.service;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import org.springframework.stereotype.Service;

/** Fetches applicant data live from its owner without touching this module's database. */
@Service
public class ApplicantService {

    private final OrchestratorClient orchestrator;

    public ApplicantService(OrchestratorClient orchestrator) {
        this.orchestrator = orchestrator;
    }

    public ApplicantViewDto find(String applicationId) {
        Application application = orchestrator.getApplication(applicationId);
        if (application == null) {
            throw new IllegalStateException("Orchestrator returned no application for " + applicationId);
        }
        return ApplicantViewDto.from(application);
    }
}
