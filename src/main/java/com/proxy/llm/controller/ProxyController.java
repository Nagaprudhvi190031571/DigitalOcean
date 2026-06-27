package com.proxy.llm.controller;

import com.proxy.llm.dto.LlmResponse;
import com.proxy.llm.dto.PromptRequest;
import com.proxy.llm.service.ProxyService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/completions")
    public ResponseEntity<LlmResponse> completions(
            @Valid @RequestBody PromptRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String id = correlationId != null && !correlationId.isBlank()
                ? correlationId
                : UUID.randomUUID().toString();

        MDC.put("correlationId", id);
        try {
            LlmResponse response = proxyService.handlePrompt(request, id);
            return ResponseEntity.ok()
                    .header("X-Correlation-Id", id)
                    .body(response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
