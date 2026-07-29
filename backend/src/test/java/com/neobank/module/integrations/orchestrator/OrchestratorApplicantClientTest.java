package com.neobank.module.integrations.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OrchestratorApplicantClientTest {

    private MockRestServiceServer server;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrchestratorClient(
                builder.build(), "neo02", "https://orchestrator.test");
    }

    @Test
    void fetchesTheWholeApplicationFromTheStandardHydrationEndpoint() {
        server.expect(requestTo(
                        "https://orchestrator.test/api/v1/applications/app-1240"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "applicationId": "app-1240",
                          "channel": "WEB",
                          "applicant": {
                            "fullName": "Sofia Ruiz",
                            "dateOfBirth": "1991-05-20",
                            "countryOfResidence": "GB",
                            "taxResidencies": ["GB", "US"]
                          },
                          "product": {
                            "productCode": "CREDIT_CARD_STANDARD",
                            "requestedCreditLimit": 2000
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Application application = client.application("app-1240");

        assertThat(application.applicationId()).isEqualTo("app-1240");
        assertThat(application.applicant().fullName()).isEqualTo("Sofia Ruiz");
        assertThat(application.applicant().taxResidencies()).containsExactly("GB", "US");
        assertThat(application.product().productCode()).isEqualTo("CREDIT_CARD_STANDARD");
        server.verify();
    }
}
