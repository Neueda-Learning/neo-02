package com.neobank.module.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.integrations.registry.HttpRegistryClient;
import com.neobank.module.integrations.registry.InMemoryRegistryClient;
import com.neobank.module.integrations.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

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
}
