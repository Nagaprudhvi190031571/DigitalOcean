package com.proxy.llm.dto;

import jakarta.validation.constraints.NotBlank;

public record PromptRequest(
        @NotBlank String prompt,
        String model,
        Double temperature
) {
}
