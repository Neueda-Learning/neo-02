package com.neobank.module.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.neobank.module.integrations.registry.HttpRegistryClient;
import com.neobank.module.integrations.registry.InMemoryRegistryClient;
import com.neobank.module.integrations.registry.RegistryClient;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

class AppConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AppConfig.class)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void localModeRegistersOnlyTheInMemoryClient() {
        context.withPropertyValues("registry.mode=in-memory")
                .run(application -> {
                    assertThat(application).hasSingleBean(RegistryClient.class);
                    assertThat(application.getBean(RegistryClient.class))
                            .isInstanceOf(InMemoryRegistryClient.class);
                });
    }

    @Test
    void httpModeRegistersOnlyTheHttpClient() {
        context.withPropertyValues(
                        "registry.mode=http",
                        "registry.lookup-url=https://orchestrator.test/registry/{applicationId}")
                .run(application -> {
                    assertThat(application).hasSingleBean(RegistryClient.class);
                    assertThat(application.getBean(RegistryClient.class))
                            .isInstanceOf(HttpRegistryClient.class);
                });
    }

    @Test
    void restClientStopsWaitingWhenTheReadTimeoutExpires() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(300);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            RestClient client = new AppConfig().restClient(RestClient.builder(), 100, 50);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

            assertThatThrownBy(() -> client.get().uri(url).retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
        } finally {
            server.stop(0);
        }
    }
}
