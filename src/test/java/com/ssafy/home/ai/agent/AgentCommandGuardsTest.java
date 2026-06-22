package com.ssafy.home.ai.agent;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCommandGuardsTest {

    // SeoulLawdCodeResolver는 상태 없는 순수 컴포넌트라 실제 인스턴스를 그대로 쓴다(mock 불필요).
    private final SeoulLawdCodeResolver resolver = new SeoulLawdCodeResolver();
    private final YearMonth now = YearMonth.of(2026, 6);

    @Test
    void passesValidSearchCommand() {
        AgentCommand command = search(Map.of("sigungu", "강남구", "startDealMonth", "2024-05"));

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result).isSameAs(command);
        assertThat(result.action()).isEqualTo("search");
    }

    @Test
    void clarifiesForNonSeoulRegion() {
        AgentCommand command = search(Map.of("sigungu", "해운대구"));

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result.action()).isEqualTo("clarify");
        assertThat(result.clarify()).contains("서울");
    }

    @Test
    void clarifiesForInvalidDealMonth() {
        // '1999-13' -> '199913': 월 범위 초과 + 2006년 이전 -> dealYmdError가 안내 문구 반환.
        AgentCommand command = search(Map.of("sigungu", "강남구", "startDealMonth", "1999-13"));

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result.action()).isEqualTo("clarify");
        assertThat(result.clarify()).contains("YYYYMM");
    }

    @Test
    void clarifiesForFutureDealMonth() {
        AgentCommand command = search(Map.of("endDealMonth", "2999-01"));

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result.action()).isEqualTo("clarify");
    }

    @Test
    void clarifiesForUnsupportedAction() {
        AgentCommand command = new AgentCommand("frobnicate", Map.of(), null, null, null, null, null);

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result.action()).isEqualTo("clarify");
    }

    @Test
    void clarifiesForNullOrBlankAction() {
        assertThat(AgentCommandGuards.validate(null, resolver, now).action()).isEqualTo("clarify");
        AgentCommand blank = new AgentCommand("  ", Map.of(), null, null, null, null, null);
        assertThat(AgentCommandGuards.validate(blank, resolver, now).action()).isEqualTo("clarify");
    }

    @Test
    void passesClarifyCommandThrough() {
        AgentCommand command = AgentCommand.clarify("서울 자치구를 알려주세요.");

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result).isSameAs(command);
        assertThat(result.clarify()).isEqualTo("서울 자치구를 알려주세요.");
    }

    @Test
    void passesResetCommandWithoutFilters() {
        AgentCommand command = new AgentCommand("reset", null, null, null, null, null, null);

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result.action()).isEqualTo("reset");
    }

    @Test
    void ignoresUnknownFilterKeysAtServer() {
        // 미인식 키는 프론트가 무시·보고하므로 서버 가드는 통과시킨다(메인 필터 변경에 견고).
        AgentCommand command = search(Map.of("sigungu", "강남구", "bedrooms", "3"));

        AgentCommand result = AgentCommandGuards.validate(command, resolver, now);

        assertThat(result.action()).isEqualTo("search");
        assertThat(result.filters()).containsEntry("bedrooms", "3");
    }

    private static AgentCommand search(Map<String, String> filters) {
        return new AgentCommand("search", filters, null, null, null, null, null);
    }
}
