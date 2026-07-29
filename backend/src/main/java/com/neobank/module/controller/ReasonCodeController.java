package com.neobank.module.controller;

import com.neobank.module.dto.ReasonCodeCountDto;
import com.neobank.module.service.ReasonCodeService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReasonCodeController {

    private final ReasonCodeService reasonCodes;

    public ReasonCodeController(ReasonCodeService reasonCodes) {
        this.reasonCodes = reasonCodes;
    }

    @GetMapping("/reason-codes")
    public List<ReasonCodeCountDto> list(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        validateWindow(from, to);
        return reasonCodes.countReasonCodes(from, to);
    }

    private static void validateWindow(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
    }
}
