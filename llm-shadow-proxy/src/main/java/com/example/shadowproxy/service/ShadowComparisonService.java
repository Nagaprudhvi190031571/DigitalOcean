package com.example.shadowproxy.service;

import java.util.Objects;
import java.util.concurrent.Executor;

import com.example.shadowproxy.config.LlmProxyProperties;
import com.example.shadowproxy.model.LlmForwardResponse;
import com.example.shadowproxy.model.ShadowRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ShadowComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ShadowComparisonService.class);

    private final LlmProxyProperties properties;
    private final RestClient shadowRestClient;
    private final ObjectMapper objectMapper;
    private final Executor shadowExecutor;

    public ShadowComparisonService(
            LlmProxyProperties properties,
            @Qualifier("shadowRestClient") RestClient shadowRestClient,
            ObjectMapper objectMapper,
            @Qualifier("shadowExecutor") Executor shadowExecutor) {
        this.properties = properties;
        this.shadowRestClient = shadowRestClient;
        this.objectMapper = objectMapper;
        this.shadowExecutor = shadowExecutor;
    }

    public void executeShadow(ShadowRequestContext context) {
        shadowExecutor.execute(() -> runShadow(context));
    }

    private void runShadow(ShadowRequestContext context) {
        long startedAt = System.currentTimeMillis();
        try {
            LlmForwardResponse candidateResponse = forwardToCandidate(context);
            long durationMs = System.currentTimeMillis() - startedAt;

            if (responsesMatch(context.primaryResponseBody(), candidateResponse.body())) {
                log.debug(
                        "Shadow match requestId={} candidateStatus={} durationMs={}",
                        context.requestId(),
                        candidateResponse.statusCode(),
                        durationMs);
                return;
            }

            log.warn(
                    """
                    Shadow mismatch detected requestId={} path={} primaryStatus={} candidateStatus={} durationMs={}
                    primaryResponse={}
                    candidateResponse={}
                    """,
                    context.requestId(),
                    context.path(),
                    context.primaryStatusCode(),
                    candidateResponse.statusCode(),
                    durationMs,
                    truncate(context.primaryResponseBody()),
                    truncate(candidateResponse.body()));
        } catch (Exception ex) {
            log.error(
                    "Shadow request failed requestId={} path={} durationMs={} error={}",
                    context.requestId(),
                    context.path(),
                    System.currentTimeMillis() - startedAt,
                    ex.toString());
        }
    }

    private LlmForwardResponse forwardToCandidate(ShadowRequestContext context) {
        try {
            RestClient.RequestBodySpec spec = shadowRestClient.post().uri(properties.candidateUrl());
            context.forwardHeaders().forEach(spec::header);

            return spec.contentType(MediaType.APPLICATION_JSON)
                    .body(context.requestBody())
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

    boolean responsesMatch(String primaryBody, String candidateBody) {
        if (Objects.equals(primaryBody, candidateBody)) {
            return true;
        }

        try {
            JsonNode primaryJson = objectMapper.readTree(primaryBody);
            JsonNode candidateJson = objectMapper.readTree(candidateBody);

            if (primaryJson.equals(candidateJson)) {
                return true;
            }

            String primaryContent = extractAssistantContent(primaryJson);
            String candidateContent = extractAssistantContent(candidateJson);
            return primaryContent != null
                    && candidateContent != null
                    && Objects.equals(primaryContent, candidateContent);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractAssistantContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return content.isTextual() ? content.asText() : null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        int max = 2000;
        return value.length() <= max ? value : value.substring(0, max) + "...[truncated]";
    }
}
