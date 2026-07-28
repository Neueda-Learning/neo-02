package com.neobank.module.service;

import java.util.List;
import java.util.stream.Collectors;

/** A {@code POST /config} document failed the business rules in {@link PolicyConfigValidator}. */
public class PolicyConfigValidationException extends RuntimeException {

    private final List<Violation> errors;

    public PolicyConfigValidationException(List<Violation> errors) {
        super(errors.stream()
                .map(error -> error.field() + " " + error.message())
                .collect(Collectors.joining("; ")));
        this.errors = List.copyOf(errors);
    }

    public List<Violation> getErrors() {
        return errors;
    }

    /** A machine-readable validation failure tied to one request field. */
    public record Violation(String field, String message) {
    }
}
