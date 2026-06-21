package com.ssafy.home.ai.controller;

import com.ssafy.home.ai.limit.AiChatRateLimiter;
import com.ssafy.home.ai.tool.HouseTools;
import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.member.auth.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiChatControllerTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final HouseTools houseTools = mock(HouseTools.class);
    private final AiChatRateLimiter rateLimiter = mock(AiChatRateLimiter.class);
    private final AiChatController controller = new AiChatController(chatClient, houseTools, rateLimiter, 500);

    @Test
    void rejectsMissingRequestWithoutCallingModel() {
        ResponseEntity<ApiResponse<String>> response = controller.chat(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("message is required.");
        assertThat(response.getBody().data()).isNull();
        verifyNoInteractions(chatClient);
    }

    @Test
    void rejectsBlankMessageWithoutCallingModel() {
        ResponseEntity<ApiResponse<String>> response = controller.chat(new AiChatController.ChatRequest("  "), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("message is required.");
        verifyNoInteractions(chatClient);
    }

    @Test
    void returnsModelAnswerUsingHouseTools() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("동작구 실거래가 답변입니다.");

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("동작구 2024년 5월 실거래가 알려줘"),
                httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo("동작구 실거래가 답변입니다.");
        verify(requestSpec).user("동작구 2024년 5월 실거래가 알려줘");
        verify(requestSpec).tools(houseTools);
        verify(requestSpec).options(any(ChatOptions.class));
    }

    @Test
    void returnsServiceUnavailableWithoutLeakingModelFailure() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        when(requestSpec.call()).thenThrow(new IllegalStateException("provider token rejected"));

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("질문"), httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message())
                .isEqualTo("AI 서비스에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                .doesNotContain("provider token rejected");
    }

    @Test
    void returnsGatewayTimeoutForNestedTimeoutFailure() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        when(requestSpec.call()).thenThrow(new CompletionException(new TimeoutException("internal timeout")));

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("질문"), httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message())
                .isEqualTo("AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.")
                .doesNotContain("internal timeout");
    }

    @Test
    void returnsServiceUnavailableForEmptyModelAnswer() {
        HttpServletRequest httpRequest = authenticatedRequest(1L);
        ChatClient.ChatClientRequestSpec requestSpec = requestSpec();
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("  ");

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("질문"), httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void rejectsMessageLongerThanConfiguredLimit() {
        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("가".repeat(501)), null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("message must be 500 characters or fewer.");
        verifyNoInteractions(chatClient, rateLimiter);
    }

    @Test
    void rejectsRequestWithoutAuthenticatedMember() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("질문"), httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("login is required.");
        verifyNoInteractions(chatClient, rateLimiter);
    }

    @Test
    void returnsTooManyRequestsWithRetryAfterHeader() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE)).thenReturn(1L);
        when(rateLimiter.acquire(1L)).thenReturn(AiChatRateLimiter.Decision.rateLimited(37));

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("질문"), httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("37");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("요청이 너무 많습니다");
        verifyNoInteractions(chatClient);
    }

    @Test
    void rejectsConcurrentRequestWithoutCallingModel() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE)).thenReturn(1L);
        when(rateLimiter.acquire(1L)).thenReturn(AiChatRateLimiter.Decision.concurrentRequest());

        ResponseEntity<ApiResponse<String>> response = controller.chat(
                new AiChatController.ChatRequest("새 질문"), httpRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("이미 답변을 생성하고 있습니다");
        verifyNoInteractions(chatClient);
    }

    private ChatClient.ChatClientRequestSpec requestSpec() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(houseTools)).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
        return requestSpec;
    }

    private HttpServletRequest authenticatedRequest(Long memberId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE)).thenReturn(memberId);
        when(rateLimiter.acquire(memberId)).thenReturn(AiChatRateLimiter.Decision.granted());
        return request;
    }
}
