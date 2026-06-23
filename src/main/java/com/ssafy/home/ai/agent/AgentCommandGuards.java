package com.ssafy.home.ai.agent;

import com.ssafy.home.ai.tool.HouseTools;
import com.ssafy.home.common.region.SeoulLawdCodeResolver;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 에이전트 명령({@link AgentCommand})의 서버측 가드 검증.
 * <p>
 * 기존 질문 모드/Tool과 <b>동일한 제약</b>(지원 action, 서울 자치구, 거래연월 범위)을 적용해
 * 분기 없이 일관성을 유지한다. 검증을 통과하면 원본 명령을, 실패하면 {@code clarify} 명령을 돌려준다.
 * <p>
 * Spring 컨텍스트 없이 단위테스트할 수 있도록 순수 정적 메서드로 분리했다
 * ({@link SeoulLawdCodeResolver}는 상태 없는 순수 컴포넌트라 그대로 사용 가능).
 */
public final class AgentCommandGuards {

    /** 지원하는 action. Phase 2에서 paginate/mapFocus/selectItem 추가. */
    static final Set<String> ALLOWED_ACTIONS =
            Set.of("search", "setFilters", "reset", "clarify", "paginate", "mapFocus", "selectItem");

    /** 페이지 이동 방향. 상대 이동 명령에 사용(절대 page와 택일). */
    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("next", "prev");

    /** 거래월 성격의 필터 키(YYYY-MM). 메인 페이지의 시작/종료 월 + 단일 월 표기 호환. */
    private static final List<String> MONTH_KEYS = List.of("startDealMonth", "endDealMonth", "dealMonth");

    private static final String UNSUPPORTED_ACTION = "아직 지원하지 않는 동작이에요. 검색·조건설정·초기화만 도와드릴 수 있어요.";
    private static final String REGION_CLARIFY = "서울 자치구만 지원해요. 예: '강남구', '마포구'처럼 알려주세요.";
    private static final String DEFAULT_CLARIFY = "무엇을 도와드릴까요? 예: '강남구 2024년 5월 검색해줘'.";
    private static final String PAGINATE_CLARIFY = "몇 페이지로 갈까요? '다음 페이지'나 '3페이지'처럼 알려주세요.";
    private static final String ITEM_INDEX_CLARIFY = "몇 번째 매물을 볼까요? 예: '첫 번째 매물'.";

    private AgentCommandGuards() {
    }

    public static AgentCommand validate(AgentCommand command, SeoulLawdCodeResolver resolver, YearMonth now) {
        if (command == null || command.action() == null || command.action().isBlank()) {
            return AgentCommand.clarify(DEFAULT_CLARIFY);
        }
        String action = command.action().trim();
        if (!ALLOWED_ACTIONS.contains(action)) {
            return AgentCommand.clarify(UNSUPPORTED_ACTION);
        }
        // clarify는 그대로 통과(되묻기 문구 유지).
        if ("clarify".equals(action)) {
            return command;
        }

        // 페이지 이동 가드: 절대 page(>=1) 또는 direction(next|prev) 중 하나는 있어야 한다.
        // 상한(totalPages)은 서버가 stateless하고 stale할 수 있어 검사하지 않는다(프론트가 라이브 clamp).
        if ("paginate".equals(action)) {
            boolean hasValidPage = command.page() != null && command.page() >= 1;
            String direction = trimToNull(command.direction());
            boolean hasValidDirection = direction != null
                    && ALLOWED_DIRECTIONS.contains(direction.toLowerCase());
            if (!hasValidPage && !hasValidDirection) {
                return AgentCommand.clarify(PAGINATE_CLARIFY);
            }
            return command;
        }

        // 항목 지정 가드: itemIndex는 1부터(1=첫 번째). 상한(결과 건수)은 서버가 모르므로 프론트가 검사.
        if ("mapFocus".equals(action) || "selectItem".equals(action)) {
            if (command.itemIndex() == null || command.itemIndex() < 1) {
                return AgentCommand.clarify(ITEM_INDEX_CLARIFY);
            }
            return command;
        }

        Map<String, String> filters = command.filters() == null ? Map.of() : command.filters();

        // 지역 가드: 자치구가 있으면 서울 25개 자치구로 해석 가능해야 한다(HouseTools와 동일 규칙).
        String sigungu = trimToNull(filters.get("sigungu"));
        if (sigungu != null
                && resolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, sigungu).isEmpty()) {
            return AgentCommand.clarify(REGION_CLARIFY);
        }

        // 거래연월 가드: YYYY-MM -> YYYYMM 변환 후 기존 dealYmdError 재사용(2006~현재, 형식).
        for (String key : MONTH_KEYS) {
            String value = trimToNull(filters.get(key));
            if (value == null) {
                continue;
            }
            String error = HouseTools.dealYmdError(toYearMonthDigits(value), now);
            if (error != null) {
                return AgentCommand.clarify(error);
            }
        }

        return command;
    }

    /** 'YYYY-MM' -> 'YYYYMM'. 구분자만 제거하며, 형식이 어긋나면 dealYmdError가 안내 문구를 반환한다. */
    private static String toYearMonthDigits(String value) {
        return value.replace("-", "").trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
