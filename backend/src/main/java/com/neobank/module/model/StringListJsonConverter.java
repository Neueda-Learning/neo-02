package com.neobank.module.model;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a {@code List<String>} (a residency list) as a JSON array column. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return JsonColumnSupport.write(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        return JsonColumnSupport.read(dbData, TYPE);
    }
}
