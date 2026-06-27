package com.proxy.llm.service;

import com.proxy.llm.dto.LlmResponse;
import com.proxy.llm.dto.PromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final RestClient restClient;
    private final ShadowService shadowService;
    private final String primaryUrl;

    public ProxyService(
            RestClient restClient,
            ShadowService shadowService,
            @Value("${llm.primary.url}") String primaryUrl
    ) {
        this.restClient = restClient;
        this.shadowService = shadowService;
        this.primaryUrl = primaryUrl;
    }

    public LlmResponse handlePrompt(PromptRequest request, String correlationId) {
        long start = System.currentTimeMillis();

        LlmResponse primaryResponse = restClient.post()
                .uri(primaryUrl)
                .body(request)
                .retrieve()
                .body(LlmResponse.class);

        long latencyMs = System.currentTimeMillis() - start;
        LlmResponse responseWithLatency = new LlmResponse(
                primaryResponse.id(),
                primaryResponse.model(),
                primaryResponse.content(),
                latencyMs
        );

        // Fire-and-forget on a dedicated pool; survives primary connection close.
        shadowService.executeShadow(request, responseWithLatency, correlationId);

        log.debug("Primary response returned for correlationId={}", correlationId);
        return responseWithLatency;
    }
}
