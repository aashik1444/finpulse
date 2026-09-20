package com.finpulse.ledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * HTTP client for audit-service.
     *
     * <p>RestClient rather than RestTemplate: RestTemplate has been in maintenance
     * mode since Spring 5 and its own Javadoc points new code here. RestClient is
     * synchronous and blocking, which suits this caller exactly, since the poller
     * works through one small batch every two seconds and has nothing else to do
     * while waiting. WebClient's reactive model would add complexity for no gain.
     *
     * <p>The timeouts are the important part. Left at their defaults they are
     * effectively infinite, so an audit-service that accepts the TCP connection but
     * never replies would hang the poller thread forever on one event, starving
     * every event queued behind it. Bounded timeouts guarantee the poller always
     * makes forward progress: at worst it logs a failure and retries next tick.
     */
    @Bean
    public RestClient auditServiceClient(@Value("${audit-service.base-url}") String baseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
