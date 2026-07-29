package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseDetailService;
import com.neobank.module.service.OverrideCaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator-facing policy case endpoints. */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseDetailService cases;
    private final ApplicantService applicants;
    private final OverrideCaseService overrides;

    public CaseController(
            CaseDetailService cases,
            ApplicantService applicants,
            OverrideCaseService overrides) {
        this.cases = cases;
        this.applicants = applicants;
        this.overrides = overrides;
    }

    @GetMapping("/{applicationId}")
    public CaseDetailView detail(@PathVariable String applicationId) {
        return cases.find(applicationId);
    }

    @GetMapping("/{applicationId}/applicant")
    public ApplicantView applicant(@PathVariable String applicationId) {
        return applicants.find(applicationId);
    }

    @PostMapping("/{applicationId}/override")
    public CaseDetailView override(
            @PathVariable String applicationId,
            @RequestHeader("X-Expected-Version") long expectedVersion,
            @Valid @RequestBody OverrideCaseRequest request) {
        return overrides.override(applicationId, request, expectedVersion);
    }
}
