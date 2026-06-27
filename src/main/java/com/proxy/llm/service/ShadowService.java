package com.proxy.llm.service;

import com.proxy.llm.config.AsyncConfig;
import com.proxy.llm.config.LlmProperties;
import com.proxy.llm.model.LlmForwardResponse;
import com.proxy.llm.model.ShadowRequestContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ShadowService {

    private static final Logger log = LoggerFactory.getLogger(ShadowService.class);

    private final LlmProperties properties;
    private final RestClient shadowRestClient;
    private final ComparisonService comparisonService;
    private final Counter shadowMatchCounter;
    private final Counter shadowMismatchCounter;
    private final Counter shadowFailureCounter;

    public ShadowService(
            LlmProperties properties,
            @Qualifier("shadowRestClient") RestClient shadowRestClient,
            ComparisonService comparisonService,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.shadowRestClient = shadowRestClient;
        this.comparisonService = comparisonService;
        this.shadowMatchCounter = meterRegistry.counter("llm.shadow.comparison", "result", "match");
        this.shadowMismatchCounter = meterRegistry.counter("llm.shadow.comparison", "result", "mismatch");
        this.shadowFailureCounter = meterRegistry.counter("llm.shadow.comparison", "result", "failure");
    }

    /**
     * Runs on {@link AsyncConfig#SHADOW_EXECUTOR}, decoupled from the servlet thread
     * and the primary HTTP response lifecycle.
     */
    @Async(AsyncConfig.SHADOW_EXECUTOR)
    public void executeShadow(ShadowRequestContext context) {
        MDC.put("requestId", context.requestId());
        long startedAt = System.currentTimeMillis();
        try {
            log.debug("Shadow request started requestId={} path={}", context.requestId(), context.path());

            LlmForwardResponse candidateResponse = forwardToCandidate(context);
            long durationMs = System.currentTimeMillis() - startedAt;

            if (comparisonService.compareAndLog(context, candidateResponse.body(), candidateResponse.statusCode())) {
                shadowMatchCounter.increment();
            } else {
                shadowMismatchCounter.increment();
            }

            log.debug(
                    "Shadow request finished requestId={} path={} durationMs={}",
                    context.requestId(),
                    context.path(),
                    durationMs
            );
        } catch (Exception ex) {
            shadowFailureCounter.increment();
            log.warn(
                    "Shadow request failed requestId={} path={} durationMs={} error={}",
                    context.requestId(),
                    context.path(),
                    System.currentTimeMillis() - startedAt,
                    ex.toString()
            );
        } finally {
            MDC.remove("requestId");
        }
    }

    private LlmForwardResponse forwardToCandidate(ShadowRequestContext context) {
        try {
            RestClient.RequestBodySpec spec = shadowRestClient.post().uri(properties.candidate().url());
            context.forwardHeaders().forEach(spec::header);

            return spec.contentType(MediaType.APPLICATION_JSON)
                    .body(context.requestBody())
                    .exchange((request, response) -> new LlmForwardResponse(
                            response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes()),
                            response.getHeaders().getContentType() != null
                                    ? response.getHeaders().getContentType().toString()
                                    : MediaType.APPLICATION_JSON_VALUE));
        } catch (RestClientResponseException ex) {
            return new LlmForwardResponse(
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null
                            ? ex.getResponseHeaders().getContentType().toString()
                            : MediaType.APPLICATION_JSON_VALUE);
        }
    }
}
