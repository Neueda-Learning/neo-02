package com.neobank.module.dto;

import com.neobank.module.model.PolicyOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC06 command. The operator and reason form part of the permanent audit record. */
public record OverrideCaseRequest(
        @NotNull PolicyOutcome newOutcome,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String operator) {
}
