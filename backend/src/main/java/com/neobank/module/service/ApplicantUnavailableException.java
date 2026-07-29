package com.neobank.module.service;

/** The orchestrator could not supply the live applicant view for a policy case. */
public class ApplicantUnavailableException extends RuntimeException {

    public ApplicantUnavailableException(String applicationId) {
        super(message(applicationId));
    }

    public ApplicantUnavailableException(String applicationId, Throwable cause) {
        super(message(applicationId), cause);
    }

    private static String message(String applicationId) {
        return "Applicant details are temporarily unavailable for "
                + applicationId + ". Retry the request.";
    }
}
