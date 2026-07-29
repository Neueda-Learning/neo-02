package com.neobank.module.dto;

import com.neobank.module.model.PolicyOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManualDecisionRequest(
        @NotNull PolicyOutcome outcome,
        @NotBlank String reason,
        @NotBlank String operator) {
}
