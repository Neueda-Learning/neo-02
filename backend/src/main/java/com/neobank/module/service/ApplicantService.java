package com.neobank.module.service;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.repository.PolicyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/** Fetches applicant data live from its owner without touching this module's database. */
@Service
public class ApplicantService {

    private final OrchestratorClient orchestrator;
    private final PolicyRecordRepository records;

    public ApplicantService(OrchestratorClient orchestrator, PolicyRecordRepository records) {
        this.orchestrator = orchestrator;
        this.records = records;
    }

    public ApplicantViewDto find(String applicationId) {
        if (!records.existsById(applicationId)) {
            throw new CaseNotFoundException(applicationId);
        }

        try {
            Application application = orchestrator.getApplication(applicationId);
            if (application == null) {
                throw new ApplicantUnavailableException(applicationId);
            }
            return ApplicantViewDto.from(application);
        } catch (RestClientException ex) {
            throw new ApplicantUnavailableException(applicationId, ex);
        }
    }
}
