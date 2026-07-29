package com.neobank.module.integrations.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OrchestratorClientTest {

    private MockRestServiceServer server;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrchestratorClient(builder.build(), "neo02", "https://orchestrator.test");
    }

    @Test
    void fetchesTheApplicationByTheUnmappedApplicationId() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/api/v1/applications/app-1240"))
                .andRespond(withSuccess("""
                        {
                          "applicationId":"app-1240",
                          "channel":"WEB",
                          "applicant":{
                            "fullName":"Sofia Ruiz",
                            "countryOfResidence":"GB",
                            "taxResidencies":["GB","US"]
                          },
                          "product":{"productCode":"CREDIT_CARD_REWARDS"}
                        }
                        """, MediaType.APPLICATION_JSON));

        Application result = client.getApplication("app-1240");

        assertThat(result.applicant().fullName()).isEqualTo("Sofia Ruiz");
        assertThat(result.applicant().taxResidencies()).containsExactly("GB", "US");
        server.verify();
    }
}
