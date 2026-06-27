package com.example.shadowproxy.mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockLlmController {

    private final ObjectMapper objectMapper;

    public MockLlmController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/primary/v1/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> primary(@RequestBody String body) throws InterruptedException {
        simulateLatency(50, 150);
        return ResponseEntity.ok(buildCompletion(body, "primary-model-v1", assistantContent(body, false)));
    }

    @PostMapping(value = "/candidate/v1/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> candidate(@RequestBody String body) throws InterruptedException {
        simulateLatency(200, 800);
        boolean injectMismatch = shouldInjectMismatch(body);
        return ResponseEntity.ok(buildCompletion(body, "candidate-model-v2", assistantContent(body, injectMismatch)));
    }

    private static String assistantContent(String body, boolean injectMismatch) {
        String prompt = extractPrompt(body);
        return injectMismatch
                ? "Candidate shadow response (intentional mismatch) for: " + prompt
                : "Primary-aligned response for: " + prompt;
    }

    private String buildCompletion(String body, String model, String content) {
        Map<String, Object> payload = Map.of(
                "id", "chatcmpl-mock-" + ThreadLocalRandom.current().nextInt(100000),
                "object", "chat.completion",
                "model", model,
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop")));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize mock response", ex);
        }
    }

    private static void simulateLatency(int minMs, int maxMs) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
    }

    private static boolean shouldInjectMismatch(String body) {
        return body != null && body.contains("\"shadow_mismatch\":true");
    }

    private static String extractPrompt(String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                JsonNode last = messages.get(messages.size() - 1);
                JsonNode content = last.path("content");
                if (content.isTextual()) {
                    return content.asText();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "unknown prompt";
    }
}
