package com.neobank.module.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.neobank.module.dto.PolicyConfigRequest;

/**
 * Cross-field business rules for a new {@code policy_config} document that Bean Validation cannot
 * express alone (see docs/schema-design.md §6.2 "Validation before insert").
 */
@Component
public class PolicyConfigValidator {

    private static final Pattern ISO_ALPHA2 = Pattern.compile("^[A-Z]{2}$");

    public void validate(PolicyConfigRequest request) {
        List<String> errors = new ArrayList<>();

        checkCountryCodes(request.supportedResidencies(), "supportedResidencies", errors);
        checkCountryCodes(request.excludedResidencies(), "excludedResidencies", errors);
        checkNoOverlap(request.supportedResidencies(), request.excludedResidencies(), errors);
        checkRestrictionList(request.restrictionList(), errors);

        if (!errors.isEmpty()) {
            throw new PolicyConfigValidationException(errors);
        }
    }

    private void checkCountryCodes(List<String> countries, String field, List<String> errors) {
        for (String country : countries) {
            if (country == null || !ISO_ALPHA2.matcher(country).matches()) {
                errors.add(field + " entry '" + country + "' must be an uppercase ISO alpha-2 country code");
            }
        }
    }

    private void checkNoOverlap(List<String> supported, List<String> excluded, List<String> errors) {
        Set<String> both = new HashSet<>(supported);
        both.retainAll(excluded);
        if (!both.isEmpty()) {
            errors.add("residencies " + both
                    + " cannot appear on both supportedResidencies and excludedResidencies");
        }
    }

    private void checkRestrictionList(List<PolicyConfigRequest.RestrictionEntryRequest> entries,
                                       List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            PolicyConfigRequest.RestrictionEntryRequest entry = entries.get(i);
            String prefix = "restrictionList[" + i + "]";
            if (isBlank(entry.fullName())) {
                errors.add(prefix + ".fullName must not be blank");
            }
            if (isBlank(entry.reason())) {
                errors.add(prefix + ".reason must not be blank");
            }
            if (isBlank(entry.dateOfBirth()) || !isIsoDate(entry.dateOfBirth())) {
                errors.add(prefix + ".dateOfBirth must be an ISO date (yyyy-MM-dd)");
            }
            String key = normalize(entry.fullName()) + "|" + normalize(entry.dateOfBirth());
            if (!seen.add(key)) {
                errors.add(prefix + " duplicates an earlier entry with the same fullName and dateOfBirth");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
