package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.dto.ClaimRequest;
import com.neobank.module.dto.ManualDecisionRequest;
import com.neobank.module.dto.ReferralQueueItem;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseDetailService;
import com.neobank.module.service.ReferralQueueService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operator-facing policy case endpoints. */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseDetailService cases;
    private final ApplicantService applicants;
    private final ReferralQueueService referrals;

    public CaseController(
            CaseDetailService cases,
            ApplicantService applicants,
            ReferralQueueService referrals) {
        this.cases = cases;
        this.applicants = applicants;
        this.referrals = referrals;
    }

    @GetMapping
    public List<ReferralQueueItem> referralQueue(
            @RequestParam String outcome,
            @RequestParam(name = "unclaimed-first", required = false) String unclaimedFirst) {
        if (!"REFERRED".equalsIgnoreCase(outcome)) {
            throw new IllegalArgumentException("outcome must be REFERRED");
        }
        return referrals.findOpenReferrals();
    }

    @GetMapping("/{applicationId}")
    public CaseDetailView detail(@PathVariable String applicationId) {
        return cases.find(applicationId);
    }

    @GetMapping("/{applicationId}/applicant")
    public ApplicantViewDto applicant(@PathVariable String applicationId) {
        return applicants.find(applicationId);
    }

    @PostMapping("/{applicationId}/claim")
    public CaseDetailView claim(
            @PathVariable String applicationId, @Valid @RequestBody ClaimRequest request) {
        return referrals.claim(applicationId, request.operator());
    }

    @PostMapping("/{applicationId}/release")
    public CaseDetailView release(
            @PathVariable String applicationId, @Valid @RequestBody ClaimRequest request) {
        return referrals.release(applicationId, request.operator());
    }

    @PostMapping("/{applicationId}/decision")
    public CaseDetailView decide(
            @PathVariable String applicationId,
            @Valid @RequestBody ManualDecisionRequest request) {
        return referrals.decide(
                applicationId, request.outcome(), request.reason(), request.operator());
    }
}
