package com.neobank.module.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.model.PolicyConfigDocument;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only access to the versioned policy document owned by UC07. */
@Repository
public class PolicyConfigReader {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<PolicyConfigDocument.RestrictionEntry>> RESTRICTIONS =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PolicyConfigReader(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PolicyConfigDocument findVersion(int version) {
        return jdbc.query("""
                        SELECT version, supported_residencies, excluded_residencies,
                               restriction_list, sample_every
                        FROM policy_config
                        WHERE version = ?
                        """,
                this::map, version).stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Policy config version " + version + " does not exist"));
    }

    private PolicyConfigDocument map(ResultSet rs, int rowNumber) throws SQLException {
        return new PolicyConfigDocument(
                rs.getInt("version"),
                read(rs.getString("supported_residencies"), STRING_LIST),
                read(rs.getString("excluded_residencies"), STRING_LIST),
                read(rs.getString("restriction_list"), RESTRICTIONS),
                rs.getInt("sample_every"));
    }

    private <T> T read(String raw, TypeReference<T> type) {
        try {
            JsonNode node = json.readTree(raw);
            if (node.isTextual()) {
                node = json.readTree(node.textValue());
            }
            return json.convertValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid policy config JSON", e);
        }
    }
}
