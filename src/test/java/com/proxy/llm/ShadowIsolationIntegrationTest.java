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

import java.util.Map;

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
            {"id":"primary-1","model":"primary-v1","content":"primary-ok","latencyMs":0}
            """;

    private static final WireMockServer primaryServer =
            new WireMockServer(wireMockConfig().dynamicPort());

    private static final WireMockServer candidateServer =
            new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerLlmEndpoints(DynamicPropertyRegistry registry) {
        registry.add("llm.primary.url", () -> primaryServer.baseUrl() + "/completions");
        registry.add("llm.candidate.url", () -> candidateServer.baseUrl() + "/completions");
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

        primaryServer.stubFor(post(urlEqualTo("/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PRIMARY_BODY)));
    }

    @Test
    void primaryReturnsBeforeSlowCandidateCompletes() {
        candidateServer.stubFor(post(urlEqualTo("/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5_000)
                        .withBody(
                                """
                                {"id":"candidate-1","model":"candidate-v2","content":"late","latencyMs":0}
                                """)));

        long startMs = System.currentTimeMillis();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/v1/completions",
                requestEntity("latency probe"),
                Map.class
        );
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elapsedMs).isLessThan(2_000);
        assertThat(response.getBody())
                .containsEntry("content", "primary-ok")
                .containsEntry("model", "primary-v1");

        primaryServer.verify(postRequestedFor(urlEqualTo("/completions")));
        // Shadow is fire-and-forget; candidate may still be in-flight after client response.
    }

    @Test
    void primarySucceedsWhenCandidateReturnsServerError() {
        candidateServer.stubFor(post(urlEqualTo("/completions"))
                .willReturn(aResponse().withStatus(500).withBody("candidate unavailable")));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/v1/completions",
                requestEntity("failure probe"),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("content", "primary-ok");
    }

    @Test
    void primarySucceedsWhenCandidateConnectionFails() {
        candidateServer.stop();

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/v1/completions",
                    requestEntity("connection failure probe"),
                    Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("content", "primary-ok");
        } finally {
            candidateServer.start();
            WireMock.configureFor("localhost", candidateServer.port());
        }
    }

    private HttpEntity<String> requestEntity(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(
                """
                {"prompt":"%s"}
                """.formatted(prompt),
                headers
        );
    }
}
