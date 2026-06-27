package com.proxy.llm.model;

import java.util.Map;

public record ShadowRequestContext(
        String requestId,
        String path,
        String requestBody,
        Map<String, String> forwardHeaders,
        String primaryResponseBody,
        int primaryStatusCode
) {
}
