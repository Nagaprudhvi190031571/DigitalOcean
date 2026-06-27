package com.example.shadowproxy.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shadowproxy.config.LlmProxyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ShadowComparisonServiceTest {

    private ShadowComparisonService service;

    @BeforeEach
    void setUp() {
        LlmProxyProperties properties = new LlmProxyProperties(
                "http://localhost/mock/primary",
                "http://localhost/mock/candidate",
                true,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30));
        service = new ShadowComparisonService(
                properties, RestClient.create(), new ObjectMapper(), Runnable::run);
    }

    @Test
    void matchesWhenAssistantContentIsEqual() {
        String primary =
                """
                {"choices":[{"message":{"content":"hello world"}}]}
                """;
        String candidate =
                """
                {"model":"other","choices":[{"message":{"content":"hello world"}}]}
                """;
        assertTrue(service.responsesMatch(primary, candidate));
    }

    @Test
    void mismatchesWhenAssistantContentDiffers() {
        String primary =
                """
                {"choices":[{"message":{"content":"hello"}}]}
                """;
        String candidate =
                """
                {"choices":[{"message":{"content":"goodbye"}}]}
                """;
        assertFalse(service.responsesMatch(primary, candidate));
    }
}
