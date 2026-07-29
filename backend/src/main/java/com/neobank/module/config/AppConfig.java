package com.neobank.module.config;

import com.neobank.module.integrations.registry.HttpRegistryClient;
import com.neobank.module.integrations.registry.InMemoryRegistryClient;
import com.neobank.module.integrations.registry.RegistryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Infrastructure beans. Just the HTTP client this module calls the orchestrator back with.
 *
 * <p>The thread pool the decision runs on is Spring Boot's own
 * {@code applicationTaskExecutor} — no bean needed here. Size and naming are properties:
 * {@code spring.task.execution.*} in {@code application.yml}.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(
            RestClient.Builder builder,
            @Value("${http.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${http.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeoutMs);
        requests.setReadTimeout(readTimeoutMs);
        return builder.requestFactory(requests).build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "registry", name = "mode", havingValue = "in-memory", matchIfMissing = true)
    public RegistryClient inMemoryRegistryClient() {
        return new InMemoryRegistryClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "registry", name = "mode", havingValue = "http")
    public RegistryClient httpRegistryClient(
            RestClient restClient,
            @Value("${registry.lookup-url:}") String lookupUrl) {
        return new HttpRegistryClient(restClient, lookupUrl);
    }
}
