package com.neobank.module.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /config} body (UC07) — the whole policy document. A document different from the
 * current one becomes a new version; an exact replay is a no-op. There is no partial update.
 */
public record PolicyConfigRequest(
        @NotNull List<String> supportedResidencies,
        @NotNull List<String> excludedResidencies,
        @NotNull @Valid List<RestrictionEntryRequest> restrictionList,
        @Min(1) int sampleEvery) {

    /** One {@code {fullName, dateOfBirth, reason}} entry in the restriction list. */
    public record RestrictionEntryRequest(
            @NotBlank String fullName,
            @NotBlank String dateOfBirth,
            @NotBlank String reason) {
    }
}
