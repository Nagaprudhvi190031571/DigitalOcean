package com.example.shadowproxy.model;

public record LlmForwardResponse(int statusCode, String body, String contentType) {
}
