package com.proxy.llm.service;

import com.proxy.llm.dto.LlmResponse;
import com.proxy.llm.dto.PromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);

    public void compareAndLog(
            PromptRequest request,
            LlmResponse primary,
            LlmResponse candidate,
            String correlationId
    ) {
        boolean contentMatch = Objects.equals(
                normalize(primary.content()),
                normalize(candidate.content())
        );

        if (contentMatch) {
            log.info(
                    "Shadow match correlationId={} primaryModel={} candidateModel={} primaryLatencyMs={} candidateLatencyMs={}",
                    correlationId,
                    primary.model(),
                    candidate.model(),
                    primary.latencyMs(),
                    candidate.latencyMs()
            );
            return;
        }

        log.warn(
                """
                Shadow MISMATCH correlationId={}
                  prompt: {}
                  primary  [model={}, latencyMs={}]: {}
                  candidate[model={}, latencyMs={}]: {}
                """,
                correlationId,
                request.prompt(),
                primary.model(),
                primary.latencyMs(),
                primary.content(),
                candidate.model(),
                candidate.latencyMs(),
                candidate.content()
        );
    }

    private String normalize(String content) {
        return content == null ? "" : content.strip();
    }
}
