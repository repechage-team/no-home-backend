package com.ssafy.home.ai.controller;

import com.ssafy.home.ai.tool.HouseTools;
import com.ssafy.home.ai.tool.PocPageActionTools;
import com.ssafy.home.common.response.ApiResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 0 PoC 전용 컨트롤러 — 단일 호출에서 데이터 tool(returnDirect=false)과
 * 액션 tool(returnDirect=true)을 함께 등록했을 때 Spring AI 1.1.2가
 * <b>명령을 어떤 형태로 회수</b>시키는지 실측한다.
 * <p>
 * 응답은 디버그 목적의 raw 구조(content/finishReason/toolCalls)다. 민감정보(키·프롬프트 원문)는 담지 않는다.
 * 게이트 통과 후 정식 {@code AiAssistantController}로 대체하며 이 클래스는 제거한다.
 * 피처 플래그 {@code ai.assistant.enabled=true}일 때만 빈으로 등록된다.
 */
@RestController
@RequestMapping("/api/ai/assistant")
@ConditionalOnProperty(name = "ai.assistant.enabled", havingValue = "true")
public class AiAssistantPocController {

    private static final String POC_SYSTEM = """
            너는 'no-home' 서울 아파트 실거래가 서비스의 AI 어시스턴트다. 다음 규칙으로 행동하라.
            - 사용자가 검색 조건(지역·거래월)을 지정해 매물 목록을 조회/갱신하려 하면 applyFiltersAndSearch tool을 호출하라.
            - '다음/이전 페이지'로 이동하려는 의도면 paginate tool을 호출하라.
            - 특정 지역의 실거래가/시세를 '질문'하면 searchSeoulAptDeals tool로 조회한 뒤 한국어로 간결히 요약 답변하라.
            - 그 외 일반 대화·모호·불만·평가성 발화는 어떤 tool도 호출하지 말고 한국어 텍스트로 답하라.
            - 서울 25개 자치구는 모두 서울이다. 금액은 '만원/억원' 단위로 표기한다.
            """;

    private final ChatClient chatClient;
    private final HouseTools houseTools;
    private final PocPageActionTools pocActionTools;

    public AiAssistantPocController(
            // 키 미설정 시 ChatClient 빈이 없어 null이 주입될 수 있다(앱은 정상 기동).
            @Nullable ChatClient chatClient,
            HouseTools houseTools,
            PocPageActionTools pocActionTools
    ) {
        this.chatClient = chatClient;
        this.houseTools = houseTools;
        this.pocActionTools = pocActionTools;
    }

    public record PocRequest(String message) {
    }

    @PostMapping("/poc")
    public ApiResponse<Map<String, Object>> poc(@RequestBody PocRequest request) {
        Map<String, Object> debug = new LinkedHashMap<>();
        if (chatClient == null) {
            return ApiResponse.fail("AI unavailable (no ChatClient bean).", debug);
        }
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ApiResponse.fail("message is required.", debug);
        }

        ChatResponse cr = chatClient.prompt()
                .system(POC_SYSTEM)
                .user(request.message().trim())
                .tools(houseTools, pocActionTools)
                .options(ChatOptions.builder().temperature(0.0).build())
                .call()
                .chatResponse();

        if (cr == null) {
            debug.put("note", "null ChatResponse");
            return ApiResponse.ok(debug);
        }

        debug.put("hasToolCalls", cr.hasToolCalls());
        debug.put("resultCount", cr.getResults() == null ? 0 : cr.getResults().size());

        Generation gen = cr.getResult();
        if (gen == null || gen.getOutput() == null) {
            debug.put("note", "null Generation/output");
            return ApiResponse.ok(debug);
        }
        var output = gen.getOutput();
        // returnDirect 액션 tool이 호출되면 content가 AgentCommand 직렬화(JSON)인지, 텍스트 답변인지가 핵심 측정점.
        debug.put("content", output.getText());
        debug.put("toolCalls", output.getToolCalls());
        debug.put("finishReason", gen.getMetadata() == null ? null : gen.getMetadata().getFinishReason());

        return ApiResponse.ok(debug);
    }
}
