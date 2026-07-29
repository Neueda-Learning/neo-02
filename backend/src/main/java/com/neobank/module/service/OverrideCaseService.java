package com.neobank.module.service;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.service.OverrideCaseWriter.OverrideResult;
import org.springframework.stereotype.Service;

/** Coordinates the committed UC06 write, idempotent callback, and updated read model. */
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

    public CaseDetailView override(
            String applicationId,
            OverrideCaseRequest request,
            long expectedVersion) {
        String reason = request.reason().trim();
        String operator = request.operator().trim();
        OverrideResult result =
                writer.apply(
                        applicationId,
                        request.newOutcome(),
                        reason,
                        operator,
                        expectedVersion);
        // The callback uses PUT. Reissuing it for an exact command retry closes the failure
        // window where the durable decision committed but the first transport attempt failed.
        reporter.report(
                applicationId,
                result.outcome(),
                result.reason(),
                result.operator());
        return cases.find(applicationId);
    }
}
