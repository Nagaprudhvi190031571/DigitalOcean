package com.proxy.llm.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        @Valid @NotNull Upstream primary,
        @Valid @NotNull Upstream candidate,
        boolean shadowEnabled,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration shadowReadTimeout
) {

    public record Upstream(@NotBlank String url) {
    }
}
