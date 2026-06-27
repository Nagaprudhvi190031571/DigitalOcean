package com.proxy.llm.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.proxy.llm.config.LlmProperties;
import com.proxy.llm.model.LlmForwardResponse;
import com.proxy.llm.model.ShadowRequestContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ProxyService {

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            HttpHeaders.HOST.toLowerCase(),
            HttpHeaders.CONTENT_LENGTH.toLowerCase(),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(),
            HttpHeaders.CONNECTION.toLowerCase()
    );

    private final LlmProperties properties;
    private final RestClient primaryRestClient;
    private final ShadowService shadowService;

    public ProxyService(
            LlmProperties properties,
            @Qualifier("primaryRestClient") RestClient primaryRestClient,
            ShadowService shadowService
    ) {
        this.properties = properties;
        this.primaryRestClient = primaryRestClient;
        this.shadowService = shadowService;
    }

    public ResponseEntity<String> proxyChatCompletion(String path, String body, HttpHeaders incomingHeaders) {
        String requestId = resolveRequestId(incomingHeaders);
        Map<String, String> forwardHeaders = copyForwardableHeaders(incomingHeaders);

        LlmForwardResponse primaryResponse = forwardToPrimary(body, forwardHeaders);

        if (properties.shadowEnabled()) {
            ShadowRequestContext context = new ShadowRequestContext(
                    requestId,
                    path,
                    body,
                    forwardHeaders,
                    primaryResponse.body(),
                    primaryResponse.statusCode()
            );
            shadowService.executeShadow(context);
        }

        return buildResponse(primaryResponse, requestId);
    }

    private LlmForwardResponse forwardToPrimary(String body, Map<String, String> forwardHeaders) {
        try {
            RestClient.RequestBodySpec spec = primaryRestClient.post().uri(properties.primary().url());
            forwardHeaders.forEach(spec::header);

            return spec.contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> new LlmForwardResponse(
                            response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes()),
                            response.getHeaders().getContentType() != null
                                    ? response.getHeaders().getContentType().toString()
                                    : MediaType.APPLICATION_JSON_VALUE));
        } catch (RestClientResponseException ex) {
            return new LlmForwardResponse(
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null
                            ? ex.getResponseHeaders().getContentType().toString()
                            : MediaType.APPLICATION_JSON_VALUE);
        }
    }

    private static ResponseEntity<String> buildResponse(LlmForwardResponse response, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(response.contentType()));
        headers.set("X-Request-Id", requestId);
        return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
    }

    private static String resolveRequestId(HttpHeaders headers) {
        List<String> requestIds = headers.get("X-Request-Id");
        if (requestIds != null && !requestIds.isEmpty() && !requestIds.get(0).isBlank()) {
            return requestIds.get(0);
        }
        List<String> correlationIds = headers.get("X-Correlation-Id");
        if (correlationIds != null && !correlationIds.isEmpty() && !correlationIds.get(0).isBlank()) {
            return correlationIds.get(0);
        }
        return UUID.randomUUID().toString();
    }

    static Map<String, String> copyForwardableHeaders(HttpHeaders headers) {
        return headers.entrySet().stream()
                .filter(entry -> !SKIP_REQUEST_HEADERS.contains(entry.getKey().toLowerCase()))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue()),
                        (left, right) -> right
                ));
    }
}
