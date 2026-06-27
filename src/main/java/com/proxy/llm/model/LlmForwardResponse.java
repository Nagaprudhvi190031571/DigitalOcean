package com.proxy.llm.model;

public record LlmForwardResponse(int statusCode, String body, String contentType) {
}
