package com.neobank.module.service;

/** Raised when the orchestrator-owned applicant cannot currently be fetched. */
public class ApplicantUnavailableException extends RuntimeException {

    public ApplicantUnavailableException(String applicationId) {
        super(message(applicationId));
    }

    public ApplicantUnavailableException(String applicationId, Throwable cause) {
        super(message(applicationId), cause);
    }

    private static String message(String applicationId) {
        return "Applicant details are temporarily unavailable for "
                + applicationId
                + ". Retry the request.";
    }
}
