package com.example.shadowproxy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LlmProxyProperties.class)
public class RestClientConfig {

    @Bean
    RestClient primaryRestClient(LlmProxyProperties properties) {
        return buildRestClient(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    RestClient shadowRestClient(LlmProxyProperties properties) {
        return buildRestClient(properties.connectTimeout(), properties.shadowReadTimeout());
    }

    private static RestClient buildRestClient(java.time.Duration connectTimeout, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
