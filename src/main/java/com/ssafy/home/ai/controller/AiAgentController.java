package com.ssafy.home.ai.controller;

import com.ssafy.home.ai.agent.AgentCommand;
import com.ssafy.home.ai.agent.AgentCommandGuards;
import com.ssafy.home.ai.limit.AiChatRateLimiter;
import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.common.response.ApiResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import com.ssafy.home.member.auth.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * AI 에이전트(실행) 모드 엔드포인트. 로그인 사용자만 접근 가능하다(/api/ai/** 인터셉터 보호).
 * <p>
 * 질문 모드({@link AiChatController})와 달리 텍스트 대신 구조화 명령({@link AgentCommand})을 반환한다.
 * 실제 페이지 조작은 프론트엔드가 명령을 받아 수행한다(capability-driven). 검색 Tool은 호출하지 않고
 * 자연어 의도를 명령으로 변환만 한다.
 */
@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    private static final Logger log = LoggerFactory.getLogger(AiAgentController.class);

    private static final String TIMEOUT_MESSAGE = "AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
    private static final String UNAVAILABLE_MESSAGE = "AI 서비스에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    private static final String AUTH_FAILURE_MESSAGE = "AI 서비스 인증에 문제가 있어 답변할 수 없습니다. 잠시 후 다시 시도해주세요.";
    private static final String DISABLED_MESSAGE = "AI 챗봇이 현재 비활성화되어 있습니다. 관리자에게 문의해주세요.";
    private static final String RATE_LIMIT_MESSAGE = "AI 질문 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String CONCURRENT_REQUEST_MESSAGE = "이미 답변을 생성하고 있습니다. 현재 답변이 끝난 뒤 다시 질문해주세요.";

    private static final String DEFAULT_CLARIFY = "무엇을 도와드릴까요? 예: '강남구 2024년 5월 검색해줘'.";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            너는 'no-home' 서울 아파트 실거래가 서비스의 '실행 모드' 에이전트다.
            사용자의 자연어 요청을 아래 스키마의 단일 명령(JSON)으로 변환한다. 설명 문장은 쓰지 말고 명령만 만든다.

            - 가능한 action: search | setFilters | reset | clarify | paginate | mapFocus | selectItem
              - search: filters를 적용하고 검색을 실행한다.
              - setFilters: filters만 채우고 검색은 실행하지 않는다.
              - reset: 검색 조건을 초기화한다.
              - clarify: 요청이 모호하거나 매핑할 수 없을 때. clarify 필드에 한국어로 되묻는 질문을 담는다.
              - paginate: 현재 검색 결과의 페이지를 이동한다. 절대 페이지는 page(1부터), 상대 이동은 direction('next' 또는 'prev')을 쓴다. '3페이지'는 page=3, '다음 페이지'는 direction='next'.
              - mapFocus: 현재 결과 목록의 특정 매물을 지도에 표시한다. itemIndex(1부터, 1=첫 번째)를 쓴다.
              - selectItem: 현재 결과 목록의 특정 매물을 선택해 상세를 본다. itemIndex(1부터, 1=첫 번째)를 쓴다.
            - filters 맵에 사용할 수 있는 키는 정확히 다음뿐이다: %s
              목록에 없는 키는 절대 만들지 마라.
            - 값은 모두 문자열로 적는다. 거래월은 'YYYY-MM' 형식(예: '2024-05'), 자치구는 '강남구'처럼 '구'를 포함한다.
              - sort: 'latest'(최신순) | 'oldest'(오래된순) | 'priceDesc'(가격 높은순) | 'priceAsc'(가격 낮은순) 중 하나(기본 'latest'). 자치구가 정해졌을 때만 적용된다.
              - minPrice / maxPrice: 만원 단위 정수 문자열(예: '50000' = 5억). 콤마·단위 없이 숫자만, minPrice <= maxPrice.
              - umdNm: 선택한 자치구 내 법정동 이름(예: '역삼동'). 자치구와 함께 또는 자치구가 정해진 뒤에만 채운다.
            - 서울특별시 25개 자치구만 지원한다. 서울 외 지역이면 action=clarify로 서울만 지원한다고 정중히 되묻는다.
            - 요청이 모호하거나(예: 조건이 없음) 매핑이 불가능하면 action=clarify로 답한다.
            - summary에는 수행 결과를 한국어로 간결하게 적는다. 예: '강남구·2024-05로 검색했어요'.
            - 현재 화면 상태(참고용): filters=%s, page=%s, totalPages=%s. paginate 시 존재하지 않는 페이지는 요청하지 마라.
            """;

    private final ChatClient chatClient;
    private final AiChatRateLimiter rateLimiter;
    private final SeoulLawdCodeResolver lawdCodeResolver;
    private final int maxMessageLength;

    public AiAgentController(
            // SSAFY_GMS_API_KEY 미설정 시 ChatClient 빈이 없으므로 null이 주입된다(앱은 정상 기동, 에이전트만 503).
            @Nullable ChatClient chatClient,
            AiChatRateLimiter rateLimiter,
            SeoulLawdCodeResolver lawdCodeResolver,
            @Value("${ai.chat.max-message-length:500}") int maxMessageLength
    ) {
        if (maxMessageLength <= 0) {
            throw new IllegalArgumentException("AI chat max message length must be positive.");
        }
        this.chatClient = chatClient;
        this.rateLimiter = rateLimiter;
        this.lawdCodeResolver = lawdCodeResolver;
        this.maxMessageLength = maxMessageLength;
    }

    /**
     * 에이전트 요청. 서버는 무상태이므로 프론트가 현재 UI 상태(필터/페이지)와 지원 필터 목록을 동봉한다.
     *
     * @param message        사용자 자연어 입력
     * @param capabilities   프론트가 인식·적용하는 필터 키 목록(단일 출처). 프롬프트 allow-list로 주입된다.
     * @param currentFilters 현재 적용된 필터 상태(참고용)
     * @param currentPage    현재 페이지(참고용)
     * @param totalPages     전체 페이지 수(참고용, 선택)
     */
    public record AgentRequest(
            String message,
            List<String> capabilities,
            Map<String, String> currentFilters,
            Integer currentPage,
            Integer totalPages,
            String conversationId
    ) {
    }

    @PostMapping("/agent")
    public ResponseEntity<ApiResponse<AgentCommand>> agent(
            @RequestBody(required = false) AgentRequest request,
            HttpServletRequest httpRequest
    ) {
        String message = request == null || request.message() == null ? null : request.message().trim();
        if (message == null || message.isBlank()) {
            return failure(HttpStatus.BAD_REQUEST, "message is required.");
        }
        if (message.codePointCount(0, message.length()) > maxMessageLength) {
            return failure(HttpStatus.BAD_REQUEST,
                    "message must be " + maxMessageLength + " characters or fewer.");
        }

        if (chatClient == null) {
            // SSAFY_GMS_API_KEY 미설정으로 AI 비활성화 상태(앱 자체는 정상 기동).
            return failure(HttpStatus.SERVICE_UNAVAILABLE, DISABLED_MESSAGE);
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
            String conversationId = resolveConversationId(request, memberId);
            AgentCommand command = chatClient.prompt()
                    .system(buildSystemPrompt(request))
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .options(ChatOptions.builder().temperature(0.0).build())
                    .call()
                    .entity(AgentCommand.class);

            // 구조화 출력 실패(null)도 500 대신 graceful clarify로 처리.
            AgentCommand validated = AgentCommandGuards.validate(command, lawdCodeResolver, YearMonth.now());
            return ResponseEntity.ok(ApiResponse.ok(validated));
        } catch (RuntimeException exception) {
            if (isTimeout(exception)) {
                return failure(HttpStatus.GATEWAY_TIMEOUT, TIMEOUT_MESSAGE);
            }
            if (isAuthFailure(exception)) {
                log.warn("AI provider authentication failed — SSAFY_GMS_API_KEY may be invalid or expired.");
                return failure(HttpStatus.SERVICE_UNAVAILABLE, AUTH_FAILURE_MESSAGE);
            }
            // 구조화 출력 파싱 실패 등은 앱이 죽지 않도록 clarify로 degrade(200).
            log.debug("Agent command generation failed; returning clarify fallback.", exception);
            return ResponseEntity.ok(ApiResponse.ok(AgentCommand.clarify(DEFAULT_CLARIFY)));
        } finally {
            rateLimiter.release(memberId);
        }
    }

    private String buildSystemPrompt(AgentRequest request) {
        List<String> capabilities = request == null || request.capabilities() == null
                ? List.of()
                : request.capabilities();
        Map<String, String> currentFilters = request == null || request.currentFilters() == null
                ? Map.of()
                : request.currentFilters();
        Integer currentPage = request == null ? null : request.currentPage();
        Integer totalPages = request == null ? null : request.totalPages();
        return SYSTEM_PROMPT_TEMPLATE.formatted(
                String.join(", ", capabilities),
                currentFilters,
                currentPage == null ? "-" : currentPage.toString(),
                totalPages == null ? "-" : totalPages.toString());
    }

    private static ResponseEntity<ApiResponse<AgentCommand>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(message, null));
    }

    private static Long currentMemberId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE);
        return value instanceof Long memberId ? memberId : null;
    }

    /**
     * 대화기억 키. memberId 네임스페이스(사용자 격리) + 프론트 세션 conversationId(세션 분리, 누락 시 'default').
     * 휘발성 저장소라 세션 종료 시 새 conversationId가 되어 초기화된다. 질문 모드와 동일 규칙.
     */
    private static String resolveConversationId(AgentRequest request, Long memberId) {
        String client = request == null ? null : request.conversationId();
        String base = (client == null || client.isBlank()) ? "default" : client.trim();
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        return memberId + ":" + base;
    }

    // NOTE: 질문 모드 컨트롤러(AiChatController)와 동일한 공급자 오류 분류 로직.
    // MVP에서는 복사해 두고, 후속으로 AiProviderErrors 유틸로 추출해 중복을 제거한다(계획 1.6).
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

    private static boolean isAuthFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpClientErrorException httpError) {
                int code = httpError.getStatusCode().value();
                if (code == 401 || code == 403) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("401") || lower.contains("403")
                        || lower.contains("unauthorized")
                        || lower.contains("invalid_api_key")
                        || lower.contains("incorrect api key")
                        || lower.contains("expired")
                        || lower.contains("authentication")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
