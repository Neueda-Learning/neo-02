package com.neobank.module.service;

/** Raised when an operator asks for a policy case this module has never received. */
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String applicationId) {
        super("Policy case " + applicationId + " does not exist");
    }
}
