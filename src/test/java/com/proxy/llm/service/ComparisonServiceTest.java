package com.proxy.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {

    private ComparisonService service;

    @BeforeEach
    void setUp() {
        service = new ComparisonService(new ObjectMapper());
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
        assertThat(service.responsesMatch(primary, candidate)).isTrue();
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
        assertThat(service.responsesMatch(primary, candidate)).isFalse();
    }
}
