package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.repository.PolicyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * UC03 live applicant hydration.
 *
 * <p>The local repository is consulted only to enforce the case boundary. Applicant data remains
 * orchestrator-owned and is neither cached nor persisted by this path.</p>
 */
@Service
public class ApplicantService {

    private final OrchestratorClient orchestrator;
    private final PolicyRecordRepository records;

    public ApplicantService(OrchestratorClient orchestrator, PolicyRecordRepository records) {
        this.orchestrator = orchestrator;
        this.records = records;
    }

    public ApplicantView find(String applicationId) {
        if (!records.existsById(applicationId)) {
            throw new CaseNotFoundException(applicationId);
        }

        try {
            Application application = orchestrator.application(applicationId);
            if (application == null) {
                throw new ApplicantUnavailableException(applicationId);
            }
            return ApplicantView.of(applicationId, application);
        } catch (RestClientException exception) {
            throw new ApplicantUnavailableException(applicationId, exception);
        }
    }
}
