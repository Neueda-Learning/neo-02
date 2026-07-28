package com.neobank.module.service;

import java.util.List;

/** A {@code POST /config} document failed the business rules in {@link PolicyConfigValidator}. */
public class PolicyConfigValidationException extends RuntimeException {

    private final List<String> errors;

    public PolicyConfigValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
