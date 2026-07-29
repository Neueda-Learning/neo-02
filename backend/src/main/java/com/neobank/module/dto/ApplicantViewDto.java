package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;
import java.util.List;

/** Live applicant subset returned by the orchestrator proxy; never persisted locally. */
public record ApplicantViewDto(
        String fullName,
        String dateOfBirth,
        List<String> taxResidencies,
        String productCode,
        String channel,
        String countryOfResidence) {

    public static ApplicantViewDto from(Application application) {
        Application.Applicant applicant = application == null ? null : application.applicant();
        Application.Product product = application == null ? null : application.product();
        return new ApplicantViewDto(
                applicant == null ? null : applicant.fullName(),
                applicant == null ? null : applicant.dateOfBirth(),
                applicant == null || applicant.taxResidencies() == null
                        ? List.of()
                        : List.copyOf(applicant.taxResidencies()),
                product == null ? null : product.productCode(),
                application == null ? null : application.channel(),
                applicant == null ? null : applicant.countryOfResidence());
    }
}
