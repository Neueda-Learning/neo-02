package com.neobank.module.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.dto.PolicyConfigRequest.RestrictionEntryRequest;

/** UC07 acceptance criterion 4 — invalid payloads are rejected with field-level errors. */
class PolicyConfigValidatorTest {

    private final PolicyConfigValidator validator = new PolicyConfigValidator();

    @Test
    void acceptsTheSeededV1DocumentUnchanged() {
        PolicyConfigRequest request = new PolicyConfigRequest(
                List.of("GB", "IE", "PL", "DE", "FR", "ES", "NL"),
                List.of("US"),
                List.of(new RestrictionEntryRequest("Victor Sable", "1978-03-02", "prior fraud loss"),
                        new RestrictionEntryRequest("Dana Kovacs", "1984-11-19", "account abuse")),
                7);

        validator.validate(request);
    }

    @Test
    void rejectsACountryOnBothLists() {
        PolicyConfigRequest request = new PolicyConfigRequest(
                List.of("GB"), List.of("GB"), List.of(), 7);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(PolicyConfigValidationException.class)
                .hasMessageContaining("cannot also appear");
    }

    @Test
    void rejectsARestrictionEntryWithoutAReason() {
        PolicyConfigRequest request = new PolicyConfigRequest(
                List.of("GB"), List.of("US"),
                List.of(new RestrictionEntryRequest("Jane Doe", "1990-01-01", "  ")),
                7);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(PolicyConfigValidationException.class)
                .hasMessageContaining("reason must not be blank");
    }

    @Test
    void rejectsSampleEveryBelowOneAtTheRequestLayer() {
        // sampleEvery < 1 is enforced by @Min on PolicyConfigRequest itself (Bean Validation),
        // exercised end-to-end in PolicyConfigControllerTest.
        assertThat(new PolicyConfigRequest(List.of(), List.of(), List.of(), 1).sampleEvery()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateRestrictionEntries() {
        PolicyConfigRequest request = new PolicyConfigRequest(
                List.of("GB"), List.of("US"),
                List.of(new RestrictionEntryRequest("Jane Doe", "1990-01-01", "reason one"),
                        new RestrictionEntryRequest("jane doe", "1990-01-01", "reason two")),
                7);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(PolicyConfigValidationException.class)
                .hasMessageContaining("duplicates an earlier entry");
    }

    @Test
    void rejectsANonIsoAlpha2CountryCode() {
        PolicyConfigRequest request = new PolicyConfigRequest(
                List.of("gb"), List.of("US"), List.of(), 7);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(PolicyConfigValidationException.class)
                .hasMessageContaining("must be an assigned uppercase ISO 3166-1 alpha-2 country code");
    }

    @Test
    void rejectsAnUnassignedAlpha2ShapeSuchAsZz() {
        PolicyConfigRequest request = new PolicyConfigRequest(
                List.of("ZZ"), List.of("US"), List.of(), 7);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(PolicyConfigValidationException.class)
                .hasMessageContaining("supportedResidencies")
                .hasMessageContaining("assigned uppercase ISO 3166-1 alpha-2");
    }
}
