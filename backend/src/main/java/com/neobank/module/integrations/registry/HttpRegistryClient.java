package com.neobank.module.integrations.registry;

import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * HTTP implementation of the Registry boundary.
 *
 * <p>The endpoint is a URI template because the v5 brief has not yet published an authoritative
 * path. Integration environments must provide a template containing {@code applicationId},
 * {@code fullName}, and {@code dateOfBirth}. The minimal response owned by this module is
 * {@code {"activeProductHeld": true|false}}.</p>
 */
public class HttpRegistryClient implements RegistryClient {

    private final RestClient restClient;
    private final String lookupUrl;

    public HttpRegistryClient(RestClient restClient, String lookupUrl) {
        if (lookupUrl == null || lookupUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "REGISTRY_LOOKUP_URL is required when REGISTRY_MODE=http");
        }
        this.restClient = restClient;
        this.lookupUrl = lookupUrl;
    }

    @Override
    public RegistryCustomer findCustomer(
            String applicationId, String fullName, String dateOfBirth) {
        RegistryResponse response = restClient.get()
                .uri(lookupUrl, Map.of(
                        "applicationId", value(applicationId),
                        "fullName", value(fullName),
                        "dateOfBirth", value(dateOfBirth)))
                .retrieve()
                .body(RegistryResponse.class);
        if (response == null || response.activeProductHeld() == null) {
            throw new IllegalStateException(
                    "Registry response must contain activeProductHeld");
        }
        return new RegistryCustomer(response.activeProductHeld());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private record RegistryResponse(Boolean activeProductHeld) {
    }
}
