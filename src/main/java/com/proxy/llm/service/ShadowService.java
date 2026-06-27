package com.proxy.llm.service;

import com.proxy.llm.config.AsyncConfig;
import com.proxy.llm.dto.LlmResponse;
import com.proxy.llm.dto.PromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ShadowService {

    private static final Logger log = LoggerFactory.getLogger(ShadowService.class);

    private final RestClient restClient;
    private final ComparisonService comparisonService;
    private final String candidateUrl;

    public ShadowService(
            RestClient restClient,
            ComparisonService comparisonService,
            @Value("${llm.candidate.url}") String candidateUrl
    ) {
        this.restClient = restClient;
        this.comparisonService = comparisonService;
        this.candidateUrl = candidateUrl;
    }

    /**
     * Runs on {@link AsyncConfig#SHADOW_EXECUTOR}, decoupled from the servlet thread
     * and the primary HTTP response lifecycle.
     */
    @Async(AsyncConfig.SHADOW_EXECUTOR)
    public void executeShadow(PromptRequest request, LlmResponse primaryResponse, String correlationId) {
        MDC.put("correlationId", correlationId);
        try {
            log.debug("Shadow request started for correlationId={}", correlationId);

            long start = System.currentTimeMillis();
            LlmResponse candidateResponse = restClient.post()
                    .uri(candidateUrl)
                    .body(request)
                    .retrieve()
                    .body(LlmResponse.class);

            long latencyMs = System.currentTimeMillis() - start;
            LlmResponse candidateWithLatency = new LlmResponse(
                    candidateResponse.id(),
                    candidateResponse.model(),
                    candidateResponse.content(),
                    latencyMs
            );

            comparisonService.compareAndLog(request, primaryResponse, candidateWithLatency, correlationId);
        } catch (Exception ex) {
            log.warn("Shadow request failed for correlationId={}: {}", correlationId, ex.getMessage());
        } finally {
            MDC.remove("correlationId");
        }
    }
}
