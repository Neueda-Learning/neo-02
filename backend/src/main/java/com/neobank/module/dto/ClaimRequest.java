package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimRequest(@NotBlank String operator) {
}
