package com.neobank.module.model;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the {@code restrictionList} as a JSON array of {@code {fullName, dateOfBirth, reason}}. */
@Converter
public class RestrictionListJsonConverter
        implements AttributeConverter<List<PolicyConfig.RestrictionEntry>, String> {

    private static final TypeReference<List<PolicyConfig.RestrictionEntry>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<PolicyConfig.RestrictionEntry> attribute) {
        return JsonColumnSupport.write(attribute);
    }

    @Override
    public List<PolicyConfig.RestrictionEntry> convertToEntityAttribute(String dbData) {
        return JsonColumnSupport.read(dbData, TYPE);
    }
}
