package com.example.shadowproxy.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.proxy")
public record LlmProxyProperties(
        String primaryUrl,
        String candidateUrl,
        boolean shadowEnabled,
        Duration connectTimeout,
        Duration readTimeout,
        Duration shadowReadTimeout) {
}
