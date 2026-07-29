package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.service.OverrideCaseWriter.OverrideResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OverrideCaseServiceTest {

    private OverrideCaseWriter writer;
    private ManualDecisionReporter reporter;
    private CaseDetailService cases;
    private OverrideCaseService service;

    @BeforeEach
    void setUp() {
        writer = mock(OverrideCaseWriter.class);
        reporter = mock(ManualDecisionReporter.class);
        cases = mock(CaseDetailService.class);
        service = new OverrideCaseService(writer, reporter, cases);
    }

    @Test
    void reportsExactlyOnceAfterAChangedWriteAndReturnsFreshDetail() {
        OverrideCaseRequest request =
                new OverrideCaseRequest(PolicyOutcome.APPROVED, "  stale registry  ", "  b.dimovski ");
        OverrideResult result =
                new OverrideResult(true, PolicyOutcome.APPROVED, "stale registry", "b.dimovski");
        CaseDetailView detail =
                new CaseDetailView("APPROVED", "REJECTED", "pol-1", 1, List.of());
        when(writer.apply(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski")).thenReturn(result);
        when(cases.find("app-1242")).thenReturn(detail);

        assertThat(service.override("app-1242", request)).isSameAs(detail);

        verify(reporter).report(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski");
        verify(cases).find("app-1242");
    }

    @Test
    void exactRetryDoesNotSendASecondCallback() {
        OverrideCaseRequest request =
                new OverrideCaseRequest(PolicyOutcome.APPROVED, "stale registry", "b.dimovski");
        when(writer.apply(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski"))
                .thenReturn(new OverrideResult(
                        false,
                        PolicyOutcome.APPROVED,
                        "stale registry",
                        "b.dimovski"));
        when(cases.find("app-1242"))
                .thenReturn(new CaseDetailView(
                        "APPROVED", "REJECTED", "pol-1", 1, List.of()));

        service.override("app-1242", request);

        verify(reporter, never()).report(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski");
    }
}
