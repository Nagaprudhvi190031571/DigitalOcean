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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level proof that the primary path returns immediately after the sync LLM call
 * and always dispatches shadow work separately.
 */
@ExtendWith(MockitoExtension.class)
class ProxyServiceIsolationTest {

    @Mock
    private RestClient restClient;

    @Mock
    private ShadowService shadowService;

    @InjectMocks
    private ProxyService proxyService;

    @Test
    void returnsPrimaryResponseAndDispatchesShadowWithoutWaiting() {
        ReflectionTestUtils.setField(proxyService, "primaryUrl", "http://primary/completions");

        RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri("http://primary/completions")).thenReturn(bodySpec);
        when(bodySpec.body(any(PromptRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(LlmResponse.class)).thenReturn(
                new LlmResponse("id-1", "primary-v1", "primary-ok", 0)
        );

        PromptRequest request = new PromptRequest("hello", null, null);
        LlmResponse result = proxyService.handlePrompt(request, "corr-1");

        assertThat(result.content()).isEqualTo("primary-ok");
        verify(shadowService).executeShadow(any(), any(), eq("corr-1"));
    }
}
