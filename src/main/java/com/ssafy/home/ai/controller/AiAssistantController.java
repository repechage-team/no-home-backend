package com.ssafy.home.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.ai.agent.AgentCommand;
import com.ssafy.home.ai.assistant.AssistantResponse;
import com.ssafy.home.ai.limit.AiChatRateLimiter;
import com.ssafy.home.ai.support.AiProviderErrors;
import com.ssafy.home.ai.support.AiRequests;
import com.ssafy.home.ai.tool.HouseTools;
import com.ssafy.home.ai.tool.PageActionTools;
import com.ssafy.home.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 통합 AI 어시스턴트 엔드포인트 — 단일 {@code POST /api/ai/assistant}에서 LLM이 tool calling으로 분기한다.
 * <p>
 * 질문이면 데이터 tool({@link HouseTools#searchSeoulAptDeals}, returnDirect=false)을 거쳐 텍스트 답변을,
 * 페이지 조작이면 액션 tool({@link PageActionTools}, returnDirect=true)이 반환한 {@link AgentCommand}를 돌려준다.
 * 모드 토글이 없고, "아무 tool도 안 부르고 일반 답변"이 1급 선택지라 모호·불만 발화가 억지 action으로 새지 않는다.
 * <p>
 * 피처 플래그 {@code ai.assistant.enabled=true}일 때만 빈으로 등록된다(기존 /chat·/agent와 병행).
 */
@RestController
@RequestMapping("/api/ai")
@ConditionalOnProperty(name = "ai.assistant.enabled", havingValue = "true")
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private static final String TIMEOUT_MESSAGE = "AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
    private static final String UNAVAILABLE_MESSAGE = "AI 서비스에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    private static final String AUTH_FAILURE_MESSAGE = "AI 서비스 인증에 문제가 있어 답변할 수 없습니다. 잠시 후 다시 시도해주세요.";
    private static final String DISABLED_MESSAGE = "AI 챗봇이 현재 비활성화되어 있습니다. 관리자에게 문의해주세요.";
    private static final String RATE_LIMIT_MESSAGE = "AI 질문 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    private static final String CONCURRENT_REQUEST_MESSAGE = "이미 답변을 생성하고 있습니다. 현재 답변이 끝난 뒤 다시 질문해주세요.";
    private static final String DEFAULT_CLARIFY = "무엇을 도와드릴까요? 예: '강남구 2024년 5월 검색해줘'.";

    // Phase 0 PoC 실측 확정: returnDirect 액션 tool 호출 시 finishReason이 정확히 이 값이고,
    // 그때 content는 액션 tool이 반환한 AgentCommand의 JSON 직렬화다.
    private static final String FINISH_RETURN_DIRECT = "returnDirect";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            너는 'no-home' 서울 아파트 실거래가 서비스의 AI 어시스턴트다. 한 대화에서 '질문 답변'과 '페이지 조작'을 모두 처리한다.
            - 특정 지역의 실거래가/시세를 '질문'하면 searchSeoulAptDeals tool로 조회한 뒤 한국어로 간결히 요약 답변하라.
              매매/전세/월세는 dealMode('sale'|'jeonse'|'monthly')로 구분한다.
            - 사용자가 조건을 말하며 매물을 '검색/조회'하려 하면(예: '강남구 검색해줘', '서초구 전세로 찾아줘') applyFiltersAndSearch를 호출하라(검색 실행이 기본).
              '검색하지 말고 조건만'처럼 명시적으로 검색을 미룰 때만 setFilters를 쓴다. 페이지 이동은 paginate,
              매물 상세는 selectItem, 지도 표시는 mapFocus, 검색 초기화는 reset tool을 호출하라.
            - 일반 대화·인사·모호·불만·평가성 발화는 어떤 tool도 호출하지 말고 한국어 텍스트로 답하라.
            - 액션 tool의 filters에 쓸 수 있는 키는 정확히 다음뿐이다: %s. 목록에 없는 키는 만들지 마라.
            - 값은 모두 문자열, 거래월은 'YYYY-MM'(예: '2024-05'), 자치구는 '강남구'처럼 '구'를 포함한다.
            - 서울특별시 25개 자치구만 지원한다. 금액은 '만원/억원' 단위로 표기한다.
            - 현재 화면 상태(참고용): filters=%s, page=%s, totalPages=%s. 존재하지 않는 페이지는 요청하지 마라.
            """;

    private final ChatClient chatClient;
    private final HouseTools houseTools;
    private final PageActionTools pageActionTools;
    private final AiChatRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int maxMessageLength;

    public AiAssistantController(
            // SSAFY_GMS_API_KEY 미설정 시 ChatClient 빈이 없어 null이 주입된다(앱은 정상 기동).
            @Nullable ChatClient chatClient,
            HouseTools houseTools,
            PageActionTools pageActionTools,
            AiChatRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${ai.chat.max-message-length:500}") int maxMessageLength
    ) {
        if (maxMessageLength <= 0) {
            throw new IllegalArgumentException("AI chat max message length must be positive.");
        }
        this.chatClient = chatClient;
        this.houseTools = houseTools;
        this.pageActionTools = pageActionTools;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.maxMessageLength = maxMessageLength;
    }

    /**
     * 어시스턴트 요청. 서버는 무상태이므로 프론트가 현재 UI 상태(필터/페이지)와 지원 필터 목록을 동봉한다.
     */
    public record AssistantRequest(
            String message,
            List<String> capabilities,
            Map<String, String> currentFilters,
            Integer currentPage,
            Integer totalPages,
            String conversationId
    ) {
    }

    @PostMapping("/assistant")
    public ResponseEntity<ApiResponse<AssistantResponse>> assistant(
            @RequestBody(required = false) AssistantRequest request,
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
            return failure(HttpStatus.SERVICE_UNAVAILABLE, DISABLED_MESSAGE);
        }

        Long memberId = AiRequests.currentMemberId(httpRequest);
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
            String conversationId = AiRequests.resolveConversationId(
                    request == null ? null : request.conversationId(), memberId);
            ChatResponse chatResponse = chatClient.prompt()
                    .system(buildSystemPrompt(request))
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .tools(houseTools, pageActionTools)
                    .options(ChatOptions.builder().temperature(0.0).build())
                    .call()
                    .chatResponse();

            return ResponseEntity.ok(ApiResponse.ok(toAssistantResponse(chatResponse)));
        } catch (RuntimeException exception) {
            if (AiProviderErrors.isTimeout(exception)) {
                return failure(HttpStatus.GATEWAY_TIMEOUT, TIMEOUT_MESSAGE);
            }
            if (AiProviderErrors.isAuthFailure(exception)) {
                log.warn("AI provider authentication failed — SSAFY_GMS_API_KEY may be invalid or expired.");
                return failure(HttpStatus.SERVICE_UNAVAILABLE, AUTH_FAILURE_MESSAGE);
            }
            log.debug("Assistant call failed.", exception);
            return failure(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        } finally {
            rateLimiter.release(memberId);
        }
    }

    /**
     * ChatResponse를 통합 응답으로 변환한다. finishReason이 returnDirect면 액션 tool 산출(content=AgentCommand JSON)이므로
     * 역직렬화해 type=command, 그 외엔 텍스트 답변(type=answer)으로 처리한다.
     */
    private AssistantResponse toAssistantResponse(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return AssistantResponse.answer(UNAVAILABLE_MESSAGE);
        }
        Generation generation = chatResponse.getResult();
        String content = generation.getOutput().getText();
        String finishReason = generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason();

        if (FINISH_RETURN_DIRECT.equalsIgnoreCase(finishReason)) {
            try {
                AgentCommand command = objectMapper.readValue(content, AgentCommand.class);
                if (command != null && command.action() != null && !command.action().isBlank()) {
                    return AssistantResponse.command(command);
                }
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
                log.debug("Failed to parse returnDirect content as AgentCommand; degrading to clarify.", e);
            }
            return AssistantResponse.command(AgentCommand.clarify(DEFAULT_CLARIFY));
        }

        if (content == null || content.isBlank()) {
            return AssistantResponse.answer(UNAVAILABLE_MESSAGE);
        }
        return AssistantResponse.answer(content);
    }

    private String buildSystemPrompt(AssistantRequest request) {
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

    private static ResponseEntity<ApiResponse<AssistantResponse>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(message, null));
    }
}
