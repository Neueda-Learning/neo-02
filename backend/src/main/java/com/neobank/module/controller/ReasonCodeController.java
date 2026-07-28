package com.neobank.module.controller;

import com.neobank.module.dto.ReasonCodeCountView;
import com.neobank.module.service.ReasonCodeService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC05 - ranked rejection/review reason counts in a submittedAt date window. */
@RestController
@RequestMapping({"/api/v1/reason-codes", "/reason-codes"})
public class ReasonCodeController {

    private final ReasonCodeService reasons;

    public ReasonCodeController(ReasonCodeService reasons) {
        this.reasons = reasons;
    }

    @GetMapping
    public ResponseEntity<List<ReasonCodeCountView>> reasonCodes(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reasons.countReasonCodes(from, to));
    }
}
