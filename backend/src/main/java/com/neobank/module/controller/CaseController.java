package com.neobank.module.controller;

import com.neobank.module.dto.CaseDetailView;
import com.neobank.module.service.CaseDetailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator-facing policy case endpoints. */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseDetailService cases;

    public CaseController(CaseDetailService cases) {
        this.cases = cases;
    }

    @GetMapping("/{applicationId}")
    public CaseDetailView detail(@PathVariable String applicationId) {
        return cases.find(applicationId);
    }
}
