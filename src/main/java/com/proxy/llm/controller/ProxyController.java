package com.proxy.llm.controller;

import com.proxy.llm.service.ProxyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<String> chatCompletions(
            @RequestBody String body,
            @RequestHeader HttpHeaders headers
    ) {
        return proxyService.proxyChatCompletion("/v1/chat/completions", body, headers);
    }
}
