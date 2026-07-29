package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
                "b.dimovski",
                7L)).thenReturn(result);
        when(cases.find("app-1242")).thenReturn(detail);

        assertThat(service.override("app-1242", request, 7L)).isSameAs(detail);

        verify(reporter).report(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski");
        verify(cases).find("app-1242");
    }

    @Test
    void exactRetryReissuesTheIdempotentCallback() {
        OverrideCaseRequest request =
                new OverrideCaseRequest(PolicyOutcome.APPROVED, "stale registry", "b.dimovski");
        when(writer.apply(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski",
                7L))
                .thenReturn(new OverrideResult(
                        false,
                        PolicyOutcome.APPROVED,
                        "stale registry",
                        "b.dimovski"));
        when(cases.find("app-1242"))
                .thenReturn(new CaseDetailView(
                        "APPROVED", "REJECTED", "pol-1", 1, List.of()));

        service.override("app-1242", request, 7L);

        verify(reporter).report(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski");
    }

    @Test
    void exactRetryRecoversAfterTheFirstCallbackTransportFailure() {
        OverrideCaseRequest request =
                new OverrideCaseRequest(PolicyOutcome.APPROVED, "stale registry", "b.dimovski");
        OverrideResult changed =
                new OverrideResult(true, PolicyOutcome.APPROVED, "stale registry", "b.dimovski");
        OverrideResult duplicate =
                new OverrideResult(false, PolicyOutcome.APPROVED, "stale registry", "b.dimovski");
        CaseDetailView detail =
                new CaseDetailView("APPROVED", "REJECTED", "pol-1", 1, List.of());
        when(writer.apply(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski",
                7L)).thenReturn(changed, duplicate);
        doThrow(new IllegalStateException("transport failed"))
                .doNothing()
                .when(reporter)
                .report(
                        "app-1242",
                        PolicyOutcome.APPROVED,
                        "stale registry",
                        "b.dimovski");
        when(cases.find("app-1242")).thenReturn(detail);

        assertThatThrownBy(() -> service.override("app-1242", request, 7L))
                .hasMessage("transport failed");
        assertThat(service.override("app-1242", request, 7L)).isSameAs(detail);

        verify(reporter, times(2)).report(
                "app-1242",
                PolicyOutcome.APPROVED,
                "stale registry",
                "b.dimovski");
        verify(cases).find("app-1242");
    }
}
