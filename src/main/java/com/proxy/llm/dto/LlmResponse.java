package com.proxy.llm.dto;

public record LlmResponse(
        String id,
        String model,
        String content,
        long latencyMs
) {
}
