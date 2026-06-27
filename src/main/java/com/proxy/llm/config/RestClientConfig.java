package com.proxy.llm.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class RestClientConfig {

    @Bean
    RestClient primaryRestClient(LlmProperties properties) {
        return buildRestClient(properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    RestClient shadowRestClient(LlmProperties properties) {
        return buildRestClient(properties.connectTimeout(), properties.shadowReadTimeout());
    }

    private static RestClient buildRestClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
