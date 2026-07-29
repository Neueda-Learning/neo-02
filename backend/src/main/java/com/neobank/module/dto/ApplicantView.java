package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;
import java.util.List;

/**
 * The minimum live applicant projection needed by the UC03 operator panel.
 *
 * <p>The orchestrator wire model contains additional identity, contact, address, employment and
 * financial data. None of those fields cross this service's browser-facing boundary.</p>
 */
public record ApplicantView(
        String applicationId,
        String channel,
        Applicant applicant,
        Product product) {

    public static ApplicantView of(String applicationId, Application source) {
        Application.Applicant sourceApplicant = source.applicant();
        Application.Product sourceProduct = source.product();

        Applicant applicant = sourceApplicant == null
                ? null
                : new Applicant(
                        sourceApplicant.fullName(),
                        sourceApplicant.dateOfBirth(),
                        sourceApplicant.taxResidencies() == null
                                ? List.of()
                                : List.copyOf(sourceApplicant.taxResidencies()),
                        sourceApplicant.countryOfResidence());
        Product product = sourceProduct == null ? null : new Product(sourceProduct.productCode());

        return new ApplicantView(applicationId, source.channel(), applicant, product);
    }

    public record Applicant(
            String fullName,
            String dateOfBirth,
            List<String> taxResidencies,
            String countryOfResidence) {
    }

    public record Product(String productCode) {
    }
}
