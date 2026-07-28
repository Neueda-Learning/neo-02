package com.neobank.module.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neobank.module.dto.PolicyConfigRequest;
import com.neobank.module.service.PolicyConfigService;

import jakarta.validation.Valid;

/**
 * UC07 · Edit Policy Config — a compliance officer publishes an insert-only
 * {@code policy_config} version. An exact replay returns the current version without another
 * insert. Nothing is ever updated or deleted, so earlier cases keep the version that decided them.
 */
@RestController
@RequestMapping("/config")
public class PolicyConfigController {

    private final PolicyConfigService configs;

    public PolicyConfigController(PolicyConfigService configs) {
        this.configs = configs;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createVersion(
            @Valid @RequestBody PolicyConfigRequest request) {
        int version = configs.createVersion(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
