package com.neobank.module.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.service.PolicyConfigValidationException.Violation;

/**
 * Cross-field business rules for a new {@code policy_config} document that Bean Validation cannot
 * express alone (see docs/schema-design.md §6.2 "Validation before insert").
 */
@Component
public class PolicyConfigValidator {

    private static final Pattern ISO_ALPHA2 = Pattern.compile("^[A-Z]{2}$");
    private static final Set<String> ISO_COUNTRIES = Set.copyOf(Arrays.asList(Locale.getISOCountries()));

    public void validate(PolicyConfigRequest request) {
        List<Violation> errors = new ArrayList<>();

        checkCountryCodes(request.supportedResidencies(), "supportedResidencies", errors);
        checkCountryCodes(request.excludedResidencies(), "excludedResidencies", errors);
        checkNoOverlap(request.supportedResidencies(), request.excludedResidencies(), errors);
        checkRestrictionList(request.restrictionList(), errors);

        if (!errors.isEmpty()) {
            throw new PolicyConfigValidationException(errors);
        }
    }

    private void checkCountryCodes(List<String> countries, String field, List<Violation> errors) {
        for (String country : countries) {
            if (country == null || !ISO_ALPHA2.matcher(country).matches()
                    || !ISO_COUNTRIES.contains(country)) {
                errors.add(new Violation(field,
                        "entry '" + country + "' must be an assigned uppercase ISO 3166-1 alpha-2 country code"));
            }
        }
    }

    private void checkNoOverlap(List<String> supported, List<String> excluded, List<Violation> errors) {
        Set<String> both = new HashSet<>(supported);
        both.retainAll(excluded);
        if (!both.isEmpty()) {
            errors.add(new Violation("excludedResidencies", "residencies " + both
                    + " cannot also appear on supportedResidencies"));
        }
    }

    private void checkRestrictionList(List<PolicyConfigRequest.RestrictionEntryRequest> entries,
                                       List<Violation> errors) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            PolicyConfigRequest.RestrictionEntryRequest entry = entries.get(i);
            String prefix = "restrictionList[" + i + "]";
            if (isBlank(entry.fullName())) {
                errors.add(new Violation(prefix + ".fullName", "must not be blank"));
            }
            if (isBlank(entry.reason())) {
                errors.add(new Violation(prefix + ".reason", "must not be blank"));
            }
            if (isBlank(entry.dateOfBirth()) || !isIsoDate(entry.dateOfBirth())) {
                errors.add(new Violation(prefix + ".dateOfBirth", "must be an ISO date (yyyy-MM-dd)"));
            }
            String key = normalize(entry.fullName()) + "|" + normalize(entry.dateOfBirth());
            if (!seen.add(key)) {
                errors.add(new Violation(prefix,
                        "duplicates an earlier entry with the same fullName and dateOfBirth"));
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
