package com.example.shadowproxy.controller;

import com.example.shadowproxy.service.LlmProxyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class LlmProxyController {

    private final LlmProxyService llmProxyService;

    public LlmProxyController(LlmProxyService llmProxyService) {
        this.llmProxyService = llmProxyService;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<String> chatCompletions(
            @RequestBody String body, @RequestHeader HttpHeaders headers) {
        return llmProxyService.proxyChatCompletion("/v1/chat/completions", body, headers);
    }
}
