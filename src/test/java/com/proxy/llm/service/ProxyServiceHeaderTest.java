package com.proxy.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ProxyServiceHeaderTest {

    @Test
    void skipsHopByHopHeadersWhenForwarding() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer token");
        headers.add(HttpHeaders.HOST, "localhost:8080");
        headers.add(HttpHeaders.CONTENT_LENGTH, "42");
        headers.add("X-Request-Id", "req-123");

        var forwardHeaders = ProxyService.copyForwardableHeaders(headers);

        assertThat(forwardHeaders).containsKey(HttpHeaders.AUTHORIZATION);
        assertThat(forwardHeaders).containsKey("X-Request-Id");
        assertThat(forwardHeaders).doesNotContainKey(HttpHeaders.HOST);
        assertThat(forwardHeaders).doesNotContainKey(HttpHeaders.CONTENT_LENGTH);
    }
}
