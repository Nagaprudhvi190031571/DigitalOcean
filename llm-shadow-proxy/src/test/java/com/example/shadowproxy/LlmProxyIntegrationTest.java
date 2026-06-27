package com.example.shadowproxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LlmProxyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoadsAndMockCandidateEndpointWorks() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body =
                """
                {"shadow_mismatch":true,"messages":[{"role":"user","content":"trigger mismatch"}]}
                """;

        ResponseEntity<String> candidate = restTemplate.postForEntity(
                "/mock/candidate/v1/chat/completions", new HttpEntity<>(body, headers), String.class);

        assertTrue(candidate.getStatusCode().is2xxSuccessful());
        assertTrue(candidate.getBody().contains("intentional mismatch"));
    }
}
