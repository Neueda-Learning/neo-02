package com.neobank.module.service;

/** The requested case transition conflicts with its current persisted state. */
public class CaseConflictException extends RuntimeException {

    public CaseConflictException(String message) {
        super(message);
    }
}
