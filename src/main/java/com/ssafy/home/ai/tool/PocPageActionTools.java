package com.ssafy.home.ai.tool;

import com.ssafy.home.ai.agent.AgentCommand;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 0 PoC 전용 — {@code @Tool(returnDirect=true)} 액션 tool의 Spring AI 1.1.2 실거동을 측정한다.
 * <p>
 * 본문은 <b>부작용 없이</b> 인자를 {@link AgentCommand}로 조립해 반환만 한다(실제 페이지 조작은 프론트).
 * returnDirect 회수 방식이 확정되면 가드를 내장한 정식 {@code PageActionTools}로 대체하고 이 클래스는 제거한다.
 */
@Component
public class PocPageActionTools {

    @Tool(returnDirect = true, description = """
            사용자가 검색 조건(지역·거래월 등)을 지정해 매물 '목록을 조회/갱신'하려는 의도면 호출한다.
            페이지 필터를 적용하고 검색을 실행하는 프론트 명령을 만든다. (단순 시세 '질문'에는 쓰지 말 것)
            """)
    public AgentCommand applyFiltersAndSearch(
            @ToolParam(description = "서울 자치구 이름. 예: '강남구', '마포구'") String sigungu,
            @ToolParam(required = false, description = "거래 연월 'YYYY-MM'(선택). 예: '2024-05'") String dealMonth
    ) {
        Map<String, String> filters = new LinkedHashMap<>();
        if (sigungu != null && !sigungu.isBlank()) {
            filters.put("sigungu", sigungu.trim());
        }
        if (dealMonth != null && !dealMonth.isBlank()) {
            filters.put("startDealMonth", dealMonth.trim());
            filters.put("endDealMonth", dealMonth.trim());
        }
        return new AgentCommand("search", filters, null, null, null,
                "검색 조건을 적용해 목록을 조회합니다.", null);
    }

    @Tool(returnDirect = true, description = """
            현재 검색 결과에서 '다음/이전 페이지로 이동'하려는 의도면 호출한다.
            """)
    public AgentCommand paginate(
            @ToolParam(description = "이동 방향. 'next'(다음) 또는 'prev'(이전)") String direction
    ) {
        String dir = (direction == null || direction.isBlank()) ? "next" : direction.trim();
        return new AgentCommand("paginate", Map.of(), null, dir, null,
                "페이지를 이동합니다.", null);
    }
}
