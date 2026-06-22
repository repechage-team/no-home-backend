package com.ssafy.home.ai.controller;

import com.ssafy.home.ai.limit.AiChatRateLimiter;
import com.ssafy.home.ai.tool.HouseTools;
import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.member.auth.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * AI 챗봇 엔드포인트. 로그인 사용자만 접근 가능하다(/api/ai/** 인터셉터 보호).
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final String TIMEOUT_MESSAGE = "AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
    private static final String UNAVAILABLE_MESSAGE = "AI 서비스에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    private static final String RATE_LIMIT_MESSAGE = "AI 질문 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String CONCURRENT_REQUEST_MESSAGE = "이미 답변을 생성하고 있습니다. 현재 답변이 끝난 뒤 다시 질문해주세요.";

    private static final String SYSTEM_PROMPT = """
            너는 'no-home' 서울 아파트 실거래가 서비스의 AI 도우미다.
            - 아파트 실거래가/시세 관련 질문은 반드시 제공된 Tool(searchSeoulAptDeals)로 DB를 조회한 뒤 답하라. 추측 금지.
            - 서울특별시 25개 자치구(종로구·중구·용산구·성동구·광진구·동대문구·중랑구·성북구·강북구·도봉구·노원구·은평구·서대문구·마포구·양천구·강서구·구로구·금천구·영등포구·동작구·관악구·서초구·강남구·송파구·강동구)는 모두 서울이다. '동작구', '강남구'처럼 '서울'을 붙이지 않은 자치구 이름도 서울로 간주하고 Tool을 호출하라.
            - 지역 지원 여부를 미리 단정하지 마라. 먼저 Tool을 호출하고, Tool이 '지원하지 않는 지역'이라고 응답할 때에만 서울 외 지역이라고 정중히 안내하라.
            - 답변은 한국어로 간결하게, 금액은 '만원'/'억원' 단위로 표기한다.
            - 조회 결과가 없으면 없다고 명확히 답하고, 추측으로 숫자를 만들지 마라.
            - 매우 중요: 아파트명·동(법정동)명 등 고유명사는 도구 결과에 적힌 글자를 절대 바꾸지 말고
              그대로 복사해서 사용하라. 비슷한 다른 이름으로 바꾸거나 새 이름을 만들어내면 안 된다.
            """;

    private final ChatClient chatClient;
    private final HouseTools houseTools;
    private final AiChatRateLimiter rateLimiter;
    private final int maxMessageLength;

    public AiChatController(
            ChatClient chatClient,
            HouseTools houseTools,
            AiChatRateLimiter rateLimiter,
            @Value("${ai.chat.max-message-length:500}") int maxMessageLength
    ) {
        if (maxMessageLength <= 0) {
            throw new IllegalArgumentException("AI chat max message length must be positive.");
        }
        this.chatClient = chatClient;
        this.houseTools = houseTools;
        this.rateLimiter = rateLimiter;
        this.maxMessageLength = maxMessageLength;
    }

    public record ChatRequest(String message) {
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<String>> chat(
            @RequestBody(required = false) ChatRequest request,
            HttpServletRequest httpRequest
    ) {
        String message = request == null || request.message() == null ? null : request.message().trim();
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("message is required.", null));
        }
        if (message.codePointCount(0, message.length()) > maxMessageLength) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("message must be " + maxMessageLength + " characters or fewer.", null));
        }

        Long memberId = currentMemberId(httpRequest);
        if (memberId == null) {
            return failure(HttpStatus.UNAUTHORIZED, "login is required.");
        }

        AiChatRateLimiter.Decision rateLimit = rateLimiter.acquire(memberId);
        if (!rateLimit.allowed()) {
            if (rateLimit.reason() == AiChatRateLimiter.RejectionReason.CONCURRENT_REQUEST) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.fail(CONCURRENT_REQUEST_MESSAGE, null));
            }
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(rateLimit.retryAfterSeconds()))
                    .body(ApiResponse.fail(RATE_LIMIT_MESSAGE, null));
        }

        try {
            String answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(houseTools)
                    .options(ChatOptions.builder().temperature(0.0).build())
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                return failure(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
            }

            return ResponseEntity.ok(ApiResponse.ok(answer));
        } catch (RuntimeException exception) {
            return isTimeout(exception)
                    ? failure(HttpStatus.GATEWAY_TIMEOUT, TIMEOUT_MESSAGE)
                    : failure(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        } finally {
            rateLimiter.release(memberId);
        }
    }

    private static ResponseEntity<ApiResponse<String>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(message, null));
    }

    private static Long currentMemberId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE);
        return value instanceof Long memberId ? memberId : null;
    }

    private static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
