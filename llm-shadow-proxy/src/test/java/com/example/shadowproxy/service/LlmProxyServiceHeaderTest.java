package com.example.shadowproxy.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class LlmProxyServiceHeaderTest {

    @Test
    void skipsHopByHopHeadersWhenForwarding() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer token");
        headers.add(HttpHeaders.HOST, "localhost:8080");
        headers.add(HttpHeaders.CONTENT_LENGTH, "42");
        headers.add("X-Request-Id", "req-123");

        var forwardHeaders = LlmProxyService.copyForwardableHeaders(headers);

        assertTrue(forwardHeaders.containsKey(HttpHeaders.AUTHORIZATION));
        assertTrue(forwardHeaders.containsKey("X-Request-Id"));
        assertFalse(forwardHeaders.containsKey(HttpHeaders.HOST));
        assertFalse(forwardHeaders.containsKey(HttpHeaders.CONTENT_LENGTH));
    }
}
