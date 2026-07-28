package com.neobank.module.integrations.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.DecisionResult;
import com.neobank.module.model.PolicyConfigDocument;
import com.neobank.module.model.PolicyConfigDocument.RestrictionEntry;
import com.neobank.module.model.PolicyOutcome;
import com.neobank.module.service.PolicyRuleEngine;
import com.neobank.module.service.RegistryLookupService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpRegistryClientTest {

    private static final String LOOKUP_URL =
            "https://orchestrator.test/registry/{applicationId}"
                    + "?fullName={fullName}&dateOfBirth={dateOfBirth}";

    private MockRestServiceServer server;
    private HttpRegistryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpRegistryClient(builder.build(), LOOKUP_URL);
    }

    @Test
    void mapsTheHttpResponseToTheRegistryBoundary() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/registry/app-1242");
                    assertThat(request.getURI().getQuery())
                            .contains("fullName=James Whitfield")
                            .contains("dateOfBirth=1988-03-12");
                })
                .andRespond(withSuccess(
                        "{\"activeProductHeld\":true}", MediaType.APPLICATION_JSON));

        RegistryClient.RegistryCustomer customer =
                client.findCustomer("app-1242", "James Whitfield", "1988-03-12");

        assertThat(customer.activeProductHeld()).isTrue();
        server.verify();
    }

    @Test
    void threeHttpFailuresProduceTheRequiredReferral() {
        server.expect(times(3), request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/registry/app-registry-down"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        RegistryLookupService.RegistrySnapshot registry =
                new RegistryLookupService(client)
                        .lookup("app-registry-down", "Maria Nowak", "1996-04-11");
        DecisionResult result = new PolicyRuleEngine().decide(
                application("Maria Nowak", "1996-04-11", List.of("GB")),
                config(),
                registry,
                1);

        assertThat(registry.available()).isFalse();
        assertThat(result.outcome()).isEqualTo(PolicyOutcome.REFERRED);
        assertThat(result.reasonCodes()).containsExactly(
                PolicyRuleEngine.REGISTRY_UNAVAILABLE);
        server.verify();
    }

    private static Application application(
            String fullName, String dateOfBirth, List<String> taxResidencies) {
        return new Application(
                "app-registry-down", "WEB", "2026-07-28T10:00:00Z",
                new Application.Applicant(
                        fullName, dateOfBirth, null, null, null, "GB",
                        taxResidencies, null, null, null, null),
                null, null, null, null, null, null);
    }

    private static PolicyConfigDocument config() {
        return new PolicyConfigDocument(
                1,
                List.of("GB", "IE", "PL", "DE", "FR", "ES", "NL"),
                List.of("US"),
                List.<RestrictionEntry>of(),
                7);
    }
}
