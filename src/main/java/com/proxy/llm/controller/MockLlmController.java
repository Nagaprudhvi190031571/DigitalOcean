package com.proxy.llm.controller;

import com.proxy.llm.dto.LlmResponse;
import com.proxy.llm.dto.PromptRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Simulated upstream LLM endpoints for local development and testing.
 */
@RestController
@RequestMapping("/mock")
public class MockLlmController {

    @PostMapping("/primary/completions")
    public ResponseEntity<LlmResponse> primary(@RequestBody PromptRequest request) throws InterruptedException {
        Thread.sleep(50);
        return ResponseEntity.ok(new LlmResponse(
                UUID.randomUUID().toString(),
                request.model() != null ? request.model() : "primary-mock-v1",
                "Primary response for: " + request.prompt(),
                0
        ));
    }

    @PostMapping("/candidate/completions")
    public ResponseEntity<LlmResponse> candidate(@RequestBody PromptRequest request) throws InterruptedException {
        // Slightly slower to exercise async decoupling from the primary path.
        Thread.sleep(200);
        return ResponseEntity.ok(new LlmResponse(
                UUID.randomUUID().toString(),
                request.model() != null ? request.model() : "candidate-mock-v2",
                "Candidate response for: " + request.prompt(),
                0
        ));
    }
}
