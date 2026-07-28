package com.neobank.module.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared Jackson plumbing for the JPA attribute converters that back {@code JSON} columns. */
final class JsonColumnSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonColumnSupport() {
    }

    static String write(Object value) {
        try {
            return value == null ? null : MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON column", e);
        }
    }

    static <T> T read(String json, TypeReference<T> type) {
        try {
            if (json == null) {
                return null;
            }
            JsonNode value = MAPPER.readTree(json);
            // H2's JSON column exposes a bound VARCHAR as a JSON string, while MySQL exposes the
            // array/object itself. Accept both representations so the test profile covers reads.
            String normalized = value.isTextual() ? value.textValue() : json;
            return MAPPER.readValue(normalized, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize JSON column", e);
        }
    }
}
