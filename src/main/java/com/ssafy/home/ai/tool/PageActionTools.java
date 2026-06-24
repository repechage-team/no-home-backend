package com.ssafy.home.ai.tool;

import com.ssafy.home.ai.agent.AgentCommand;
import com.ssafy.home.ai.agent.AgentCommandGuards;
import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 프론트 페이지 조작 액션 tool 모음 — 모두 {@code @Tool(returnDirect=true)}.
 * <p>
 * 본문은 <b>부작용 없이</b> 인자를 {@link AgentCommand}로 조립하고 {@link AgentCommandGuards}로 검증(서울 지역·
 * 거래연월·action·인덱스 가드)해 반환만 한다. 실제 페이지 조작(필터 적용·검색·페이지 이동·선택·지도)은 프론트가
 * 명령을 받아 수행한다(capability-driven). returnDirect라 반환한 {@code AgentCommand}가 그대로 호출자에게 전달된다.
 * <p>
 * filters 키는 프론트 {@code filterSchema}와 동일한 단일 출처를 유지한다(프론트가 모르는 키는 무시·보고).
 */
@Component
public class PageActionTools {

    private final SeoulLawdCodeResolver lawdCodeResolver;

    public PageActionTools(SeoulLawdCodeResolver lawdCodeResolver) {
        this.lawdCodeResolver = lawdCodeResolver;
    }

    @Tool(returnDirect = true, description = """
            검색 조건(지역·거래월·정렬·가격/보증금/월세·거래유형)을 적용하고 '검색을 실행'한다.
            '강남구 검색해줘', '서초구 전세로 찾아줘', '마포구 2024년 5월 보여줘'처럼 결과 목록을 보려는 요청은
            거의 모두 이 tool이다(검색 실행이 기본). 특정 지역 시세를 단순히 '질문'할 때만 제외한다.
            """)
    public AgentCommand applyFiltersAndSearch(
            @ToolParam(required = false, description = "서울 자치구. 예: '강남구'") String sigungu,
            @ToolParam(required = false, description = "법정동. 예: '역삼동'") String umdNm,
            @ToolParam(required = false, description = "아파트명 일부. 예: '래미안'") String aptName,
            @ToolParam(required = false, description = "거래월 'YYYY-MM'. 예: '2024-05'") String dealMonth,
            @ToolParam(required = false, description = "정렬. 매매: latest|oldest|priceDesc|priceAsc / 전월세: depositDesc|depositAsc|monthlyRentDesc|monthlyRentAsc") String sort,
            @ToolParam(required = false, description = "거래유형. sale(매매)|jeonse(전세)|monthly(월세)") String dealMode,
            @ToolParam(required = false, description = "매매가 하한(만원, 숫자만). 예: '50000'") String minPrice,
            @ToolParam(required = false, description = "매매가 상한(만원)") String maxPrice,
            @ToolParam(required = false, description = "보증금 하한(만원, 전월세)") String minDeposit,
            @ToolParam(required = false, description = "보증금 상한(만원, 전월세)") String maxDeposit,
            @ToolParam(required = false, description = "월세 하한(만원, 월세)") String minMonthlyRent,
            @ToolParam(required = false, description = "월세 상한(만원, 월세)") String maxMonthlyRent
    ) {
        Map<String, String> filters = buildFilters(sigungu, umdNm, aptName, dealMonth, sort, dealMode,
                minPrice, maxPrice, minDeposit, maxDeposit, minMonthlyRent, maxMonthlyRent);
        return guard(new AgentCommand("search", filters, null, null, null, "검색 조건을 적용해 목록을 조회합니다.", null));
    }

    @Tool(returnDirect = true, description = """
            검색은 '실행하지 않고' 조건(필터)만 채운다. 사용자가 '검색하지 말고 조건만', '필터만 설정해줘'처럼
            검색 실행을 명시적으로 미룰 때만 사용한다. 그냥 '검색해줘/찾아줘/보여줘'는 applyFiltersAndSearch를 써라.
            인자는 applyFiltersAndSearch와 동일하다.
            """)
    public AgentCommand setFilters(
            @ToolParam(required = false, description = "서울 자치구. 예: '강남구'") String sigungu,
            @ToolParam(required = false, description = "법정동. 예: '역삼동'") String umdNm,
            @ToolParam(required = false, description = "아파트명 일부. 예: '래미안'") String aptName,
            @ToolParam(required = false, description = "거래월 'YYYY-MM'") String dealMonth,
            @ToolParam(required = false, description = "정렬") String sort,
            @ToolParam(required = false, description = "거래유형. sale|jeonse|monthly") String dealMode,
            @ToolParam(required = false, description = "매매가 하한(만원)") String minPrice,
            @ToolParam(required = false, description = "매매가 상한(만원)") String maxPrice,
            @ToolParam(required = false, description = "보증금 하한(만원)") String minDeposit,
            @ToolParam(required = false, description = "보증금 상한(만원)") String maxDeposit,
            @ToolParam(required = false, description = "월세 하한(만원)") String minMonthlyRent,
            @ToolParam(required = false, description = "월세 상한(만원)") String maxMonthlyRent
    ) {
        Map<String, String> filters = buildFilters(sigungu, umdNm, aptName, dealMonth, sort, dealMode,
                minPrice, maxPrice, minDeposit, maxDeposit, minMonthlyRent, maxMonthlyRent);
        return guard(new AgentCommand("setFilters", filters, null, null, null, "검색 조건을 설정했습니다.", null));
    }

    @Tool(returnDirect = true, description = """
            현재 검색 결과에서 '다음/이전 페이지' 또는 특정 페이지 번호로 이동하려는 의도면 호출한다.
            '다음 페이지'는 direction='next', '이전'은 'prev', '3페이지'는 page=3.
            """)
    public AgentCommand paginate(
            @ToolParam(required = false, description = "절대 페이지 번호(1부터). 예: 3") Integer page,
            @ToolParam(required = false, description = "상대 이동. 'next'(다음) 또는 'prev'(이전)") String direction
    ) {
        return guard(new AgentCommand("paginate", Map.of(), page, trimToNull(direction), null,
                "페이지를 이동합니다.", null));
    }

    @Tool(returnDirect = true, description = """
            현재 결과 목록에서 특정 매물의 '상세를 보려는' 의도면 호출한다. 예: '첫 번째 매물 보여줘'.
            """)
    public AgentCommand selectItem(
            @ToolParam(description = "대상 순번(1부터, 1=첫 번째)") Integer itemIndex
    ) {
        return guard(new AgentCommand("selectItem", Map.of(), null, null, itemIndex, "매물을 선택합니다.", null));
    }

    @Tool(returnDirect = true, description = """
            현재 결과 목록의 특정 매물을 '지도에 표시'하려는 의도면 호출한다. 예: '두 번째 매물 지도에서 보여줘'.
            """)
    public AgentCommand mapFocus(
            @ToolParam(description = "대상 순번(1부터, 1=첫 번째)") Integer itemIndex
    ) {
        return guard(new AgentCommand("mapFocus", Map.of(), null, null, itemIndex, "지도에 표시합니다.", null));
    }

    @Tool(returnDirect = true, description = """
            검색 조건을 '초기화'하려는 의도면 호출한다. 예: '검색 초기화', '필터 다 지워줘'.
            """)
    public AgentCommand reset() {
        return guard(new AgentCommand("reset", Map.of(), null, null, null, "검색 조건을 초기화했습니다.", null));
    }

    private AgentCommand guard(AgentCommand command) {
        return AgentCommandGuards.validate(command, lawdCodeResolver, YearMonth.now());
    }

    private static Map<String, String> buildFilters(
            String sigungu, String umdNm, String aptName, String dealMonth, String sort, String dealMode,
            String minPrice, String maxPrice, String minDeposit, String maxDeposit,
            String minMonthlyRent, String maxMonthlyRent
    ) {
        Map<String, String> filters = new LinkedHashMap<>();
        put(filters, "sigungu", sigungu);
        put(filters, "umdNm", umdNm);
        put(filters, "aptName", aptName);
        String month = trimToNull(dealMonth);
        if (month != null) {
            // 단일 월 지정 → 시작=종료. 범위는 모델이 start/end를 따로 호출하는 대신 단일 파라미터로 단순화.
            filters.put("startDealMonth", month);
            filters.put("endDealMonth", month);
        }
        put(filters, "sort", sort);
        put(filters, "dealMode", dealMode);
        put(filters, "minPrice", minPrice);
        put(filters, "maxPrice", maxPrice);
        put(filters, "minDeposit", minDeposit);
        put(filters, "maxDeposit", maxDeposit);
        put(filters, "minMonthlyRent", minMonthlyRent);
        put(filters, "maxMonthlyRent", maxMonthlyRent);
        return filters;
    }

    private static void put(Map<String, String> filters, String key, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            filters.put(key, trimmed);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
