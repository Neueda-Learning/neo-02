package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.service.OverrideCaseWriter.OverrideResult;
import org.springframework.stereotype.Service;

/** Coordinates the committed UC06 write, one callback, and the updated read model. */
@Service
public class OverrideCaseService {

    private final OverrideCaseWriter writer;
    private final ManualDecisionReporter reporter;
    private final CaseDetailService cases;

    public OverrideCaseService(
            OverrideCaseWriter writer,
            ManualDecisionReporter reporter,
            CaseDetailService cases) {
        this.writer = writer;
        this.reporter = reporter;
        this.cases = cases;
    }

    public CaseDetailView override(String applicationId, OverrideCaseRequest request) {
        String reason = request.reason().trim();
        String operator = request.operator().trim();
        OverrideResult result =
                writer.apply(applicationId, request.newOutcome(), reason, operator);
        if (result.changed()) {
            reporter.report(
                    applicationId,
                    result.outcome(),
                    result.reason(),
                    result.operator());
        }
        return cases.find(applicationId);
    }
}
