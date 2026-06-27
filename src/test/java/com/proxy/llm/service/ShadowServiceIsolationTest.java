package com.proxy.llm.service;

import com.proxy.llm.dto.LlmResponse;
import com.proxy.llm.dto.PromptRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShadowServiceIsolationTest {

    @Mock
    private RestClient restClient;

    @Mock
    private ComparisonService comparisonService;

    @InjectMocks
    private ShadowService shadowService;

    @Test
    void swallowsCandidateFailureWithoutPropagating() {
        ReflectionTestUtils.setField(shadowService, "candidateUrl", "http://candidate/completions");

        RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri("http://candidate/completions")).thenReturn(bodySpec);
        when(bodySpec.body(any(PromptRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(LlmResponse.class)).thenThrow(new RuntimeException("candidate timeout"));

        PromptRequest request = new PromptRequest("hello", null, null);
        LlmResponse primary = new LlmResponse("id-1", "primary-v1", "primary-ok", 10);

        assertThatCode(() -> shadowService.executeShadow(request, primary, "corr-1"))
                .doesNotThrowAnyException();
    }
}
