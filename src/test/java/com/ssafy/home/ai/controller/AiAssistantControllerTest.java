package com.ssafy.home.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.ai.assistant.AssistantResponse;
import com.ssafy.home.ai.limit.AiChatRateLimiter;
import com.ssafy.home.ai.tool.HouseTools;
import com.ssafy.home.ai.tool.PageActionTools;
import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.member.auth.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiAssistantControllerTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final HouseTools houseTools = mock(HouseTools.class);
    private final PageActionTools pageActionTools = mock(PageActionTools.class);
    private final AiChatRateLimiter rateLimiter = mock(AiChatRateLimiter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAssistantController controller =
            new AiAssistantController(chatClient, houseTools, pageActionTools, rateLimiter, objectMapper, 500);

    private static AiAssistantController.AssistantRequest req(String message) {
        return new AiAssistantController.AssistantRequest(message, List.of(), Map.of(), 1, 5, "c1");
    }

    @Test
    void rejectsMissingRequestWithoutCallingModel() {
        ResponseEntity<ApiResponse<AssistantResponse>> response = controller.assistant(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("message is required.");
        verifyNoInteractions(chatClient);
    }

    @Test
    void rejectsBlankMessageWithoutCallingModel() {
        ResponseEntity<ApiResponse<AssistantResponse>> response = controller.assistant(req("  "), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(chatClient);
    }

    @Test
    void rejectsMessageLongerThanConfiguredLimit() {
        ResponseEntity<ApiResponse<AssistantResponse>> response = controller.assistant(req("가".repeat(501)), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("message must be 500 characters or fewer.");
        verifyNoInteractions(chatClient, rateLimiter);
    }

    @Test
    void returnsTextAnswerWhenModelAnswersDirectly() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        stubChatResponse(requestSpec, "마포구 평균은 13억원입니다.", "STOP");

        ResponseEntity<ApiResponse<AssistantResponse>> response =
                controller.assistant(req("마포구 시세 알려줘"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AssistantResponse data = response.getBody().data();
        assertThat(data.type()).isEqualTo("answer");
        assertThat(data.answer()).isEqualTo("마포구 평균은 13억원입니다.");
        assertThat(data.command()).isNull();
        verify(requestSpec).tools(houseTools, pageActionTools);
        verify(requestSpec).options(any(ChatOptions.class));
    }

    @Test
    void returnsCommandWhenActionToolReturnsDirect() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        String commandJson = "{\"action\":\"search\",\"filters\":{\"sigungu\":\"강남구\"},"
                + "\"page\":null,\"direction\":null,\"itemIndex\":null,\"summary\":\"검색했어요\",\"clarify\":null}";
        stubChatResponse(requestSpec, commandJson, "returnDirect");

        ResponseEntity<ApiResponse<AssistantResponse>> response =
                controller.assistant(req("강남구로 검색해줘"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AssistantResponse data = response.getBody().data();
        assertThat(data.type()).isEqualTo("command");
        assertThat(data.command()).isNotNull();
        assertThat(data.command().action()).isEqualTo("search");
        assertThat(data.command().filters()).containsEntry("sigungu", "강남구");
    }

    @Test
    void degradesToClarifyCommandWhenReturnDirectContentUnparseable() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        stubChatResponse(requestSpec, "this is not json", "returnDirect");

        ResponseEntity<ApiResponse<AssistantResponse>> response =
                controller.assistant(req("뭔가 조작"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AssistantResponse data = response.getBody().data();
        assertThat(data.type()).isEqualTo("command");
        assertThat(data.command().action()).isEqualTo("clarify");
    }

    @Test
    void returnsServiceUnavailableWhenChatClientMissing() {
        AiAssistantController disabled =
                new AiAssistantController(null, houseTools, pageActionTools, rateLimiter, objectMapper, 500);

        ResponseEntity<ApiResponse<AssistantResponse>> response = disabled.assistant(req("질문"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).contains("비활성화");
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void returnsAuthFailureWhenProviderRejectsKey() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        when(requestSpec.call()).thenThrow(new RuntimeException("401 Unauthorized: invalid_api_key"));

        ResponseEntity<ApiResponse<AssistantResponse>> response =
                controller.assistant(req("질문"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).contains("인증").doesNotContain("invalid_api_key");
    }

    @Test
    void returnsServiceUnavailableWithoutLeakingModelFailure() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        when(requestSpec.call()).thenThrow(new IllegalStateException("provider token rejected"));

        ResponseEntity<ApiResponse<AssistantResponse>> response =
                controller.assistant(req("질문"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).doesNotContain("provider token rejected");
    }

    @Test
    void returnsGatewayTimeoutForNestedTimeoutFailure() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        when(requestSpec.call()).thenThrow(new CompletionException(new TimeoutException("internal timeout")));

        ResponseEntity<ApiResponse<AssistantResponse>> response =
                controller.assistant(req("질문"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().message()).doesNotContain("internal timeout");
    }

    @Test
    void rejectsRequestWithoutAuthenticatedMember() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        ResponseEntity<ApiResponse<AssistantResponse>> response = controller.assistant(req("질문"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chatClient, rateLimiter);
    }

    @Test
    void returnsTooManyRequestsWithRetryAfterHeader() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE)).thenReturn(1L);
        when(rateLimiter.acquire(1L)).thenReturn(AiChatRateLimiter.Decision.rateLimited(37));

        ResponseEntity<ApiResponse<AssistantResponse>> response = controller.assistant(req("질문"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("37");
        verifyNoInteractions(chatClient);
    }

    @Test
    void rejectsConcurrentRequestWithoutCallingModel() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE)).thenReturn(1L);
        when(rateLimiter.acquire(1L)).thenReturn(AiChatRateLimiter.Decision.concurrentRequest());

        ResponseEntity<ApiResponse<AssistantResponse>> response = controller.assistant(req("새 질문"), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("이미 답변을 생성하고 있습니다");
        verifyNoInteractions(chatClient);
    }

    private ChatClient.ChatClientRequestSpec requestSpec() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(houseTools, pageActionTools)).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
        return requestSpec;
    }

    private void stubChatResponse(ChatClient.ChatClientRequestSpec requestSpec, String content, String finishReason) {
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage output = mock(AssistantMessage.class);
        ChatGenerationMetadata metadata = mock(ChatGenerationMetadata.class);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(output);
        when(output.getText()).thenReturn(content);
        when(generation.getMetadata()).thenReturn(metadata);
        when(metadata.getFinishReason()).thenReturn(finishReason);
    }

    private HttpServletRequest authenticatedRequest(Long memberId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE)).thenReturn(memberId);
        when(rateLimiter.acquire(memberId)).thenReturn(AiChatRateLimiter.Decision.granted());
        return request;
    }
}
