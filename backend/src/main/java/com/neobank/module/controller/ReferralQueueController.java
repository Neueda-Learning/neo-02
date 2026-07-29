package com.neobank.module.controller;

import com.neobank.module.dto.ReferralQueueItem;
import com.neobank.module.service.ReferralQueueService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dedicated UI-facing entry point for the open referral queue. */
@RestController
@RequestMapping("/api/v1/referrals")
public class ReferralQueueController {

    private final ReferralQueueService referrals;

    public ReferralQueueController(ReferralQueueService referrals) {
        this.referrals = referrals;
    }

    @GetMapping
    public List<ReferralQueueItem> referralQueue() {
        return referrals.findOpenReferrals();
    }
}
