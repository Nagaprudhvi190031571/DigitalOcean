package com.proxy.llm.service;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proxy.llm.model.ShadowRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);
    private static final int LOG_TRUNCATE_LENGTH = 2_000;

    private final ObjectMapper objectMapper;

    public ComparisonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean compareAndLog(ShadowRequestContext context, String candidateBody, int candidateStatusCode) {
        if (responsesMatch(context.primaryResponseBody(), candidateBody)) {
            log.info(
                    "Shadow match requestId={} path={} primaryStatus={} candidateStatus={}",
                    context.requestId(),
                    context.path(),
                    context.primaryStatusCode(),
                    candidateStatusCode
            );
            return true;
        }

        log.warn(
                """
                Shadow MISMATCH requestId={} path={} primaryStatus={} candidateStatus={}
                primaryResponse={}
                candidateResponse={}
                """,
                context.requestId(),
                context.path(),
                context.primaryStatusCode(),
                candidateStatusCode,
                truncate(context.primaryResponseBody()),
                truncate(candidateBody)
        );
        return false;
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
        if (content.isTextual()) {
            return content.asText();
        }
        JsonNode legacyContent = choices.get(0).path("text");
        return legacyContent.isTextual() ? legacyContent.asText() : null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= LOG_TRUNCATE_LENGTH
                ? value
                : value.substring(0, LOG_TRUNCATE_LENGTH) + "...[truncated]";
    }
}
