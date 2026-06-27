package com.proxy.llm;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the synchronous primary path is isolated from candidate latency and failures.
 * The shadow call runs on a separate async executor and must never block or fail the client response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShadowIsolationIntegrationTest {

    private static final String PRIMARY_BODY =
            """
            {"id":"primary-1","object":"chat.completion","model":"primary-v1","choices":[{"message":{"content":"primary-ok"}}]}
            """;

    private static final WireMockServer primaryServer =
            new WireMockServer(wireMockConfig().dynamicPort());

    private static final WireMockServer candidateServer =
            new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerLlmEndpoints(DynamicPropertyRegistry registry) {
        registry.add("llm.primary.url", () -> primaryServer.baseUrl() + "/v1/chat/completions");
        registry.add("llm.candidate.url", () -> candidateServer.baseUrl() + "/v1/chat/completions");
    }

    @BeforeAll
    static void startWireMock() {
        primaryServer.start();
        candidateServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        primaryServer.stop();
        candidateServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        primaryServer.resetAll();
        candidateServer.resetAll();

        primaryServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PRIMARY_BODY)));
    }

    @Test
    void primaryReturnsBeforeSlowCandidateCompletes() {
        candidateServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5_000)
                        .withBody(
                                """
                                {"id":"candidate-1","object":"chat.completion","model":"candidate-v2","choices":[{"message":{"content":"late"}}]}
                                """)));

        long startMs = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/v1/chat/completions",
                requestEntity("latency probe"),
                String.class
        );
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elapsedMs).isLessThan(2_000);
        assertThat(response.getBody()).contains("primary-ok");

        primaryServer.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void primarySucceedsWhenCandidateReturnsServerError() {
        candidateServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(500).withBody("candidate unavailable")));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/v1/chat/completions",
                requestEntity("failure probe"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("primary-ok");
    }

    @Test
    void primarySucceedsWhenCandidateConnectionFails() {
        candidateServer.stop();

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/v1/chat/completions",
                    requestEntity("connection failure probe"),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("primary-ok");
        } finally {
            candidateServer.start();
            WireMock.configureFor("localhost", candidateServer.port());
        }
    }

    @Test
    void healthEndpointIsAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private HttpEntity<String> requestEntity(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(
                """
                {"messages":[{"role":"user","content":"%s"}]}
                """.formatted(prompt),
                headers
        );
    }
}
