package com.ssafy.home.ai.tool;

import com.ssafy.home.ai.agent.AgentCommand;
import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 액션 tool이 올바른 {@link AgentCommand}를 조립하고, 서버 가드(서울 지역·거래월·페이지/인덱스)가
 * 위반을 clarify로 강등하는지 검증한다. returnDirect 회수(LLM 경유)는 컨트롤러/실측에서 검증한다.
 */
class PageActionToolsTest {

    private final SeoulLawdCodeResolver resolver = mock(SeoulLawdCodeResolver.class);
    private final PageActionTools tools = new PageActionTools(resolver);

    private void allowSeoul(String sigungu, String lawdCd) {
        when(resolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, sigungu))
                .thenReturn(List.of(lawdCd));
    }

    @Test
    void applyFiltersAndSearchBuildsSearchCommand() {
        allowSeoul("강남구", "11680");
        AgentCommand cmd = tools.applyFiltersAndSearch(
                "강남구", null, null, "2024-05", null, "sale", null, null, null, null, null, null);
        assertThat(cmd.action()).isEqualTo("search");
        assertThat(cmd.filters())
                .containsEntry("sigungu", "강남구")
                .containsEntry("startDealMonth", "2024-05")
                .containsEntry("endDealMonth", "2024-05")
                .containsEntry("dealMode", "sale");
    }

    @Test
    void applyFiltersAndSearchIncludesRentDepositKeys() {
        allowSeoul("마포구", "11440");
        AgentCommand cmd = tools.applyFiltersAndSearch(
                "마포구", null, null, null, "depositDesc", "jeonse", null, null, "10000", "50000", null, null);
        assertThat(cmd.action()).isEqualTo("search");
        assertThat(cmd.filters())
                .containsEntry("dealMode", "jeonse")
                .containsEntry("sort", "depositDesc")
                .containsEntry("minDeposit", "10000")
                .containsEntry("maxDeposit", "50000");
    }

    @Test
    void nonSeoulRegionDegradesToClarify() {
        when(resolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "부산진구"))
                .thenReturn(List.of());
        AgentCommand cmd = tools.applyFiltersAndSearch(
                "부산진구", null, null, null, null, null, null, null, null, null, null, null);
        assertThat(cmd.action()).isEqualTo("clarify");
    }

    @Test
    void futureDealMonthDegradesToClarify() {
        allowSeoul("강남구", "11680");
        AgentCommand cmd = tools.applyFiltersAndSearch(
                "강남구", null, null, "9999-12", null, null, null, null, null, null, null, null);
        assertThat(cmd.action()).isEqualTo("clarify");
    }

    @Test
    void setFiltersBuildsSetFiltersCommand() {
        allowSeoul("강남구", "11680");
        AgentCommand cmd = tools.setFilters(
                "강남구", "역삼동", null, null, null, null, null, null, null, null, null, null);
        assertThat(cmd.action()).isEqualTo("setFilters");
        assertThat(cmd.filters())
                .containsEntry("sigungu", "강남구")
                .containsEntry("umdNm", "역삼동");
    }

    @Test
    void paginateWithAbsolutePage() {
        AgentCommand cmd = tools.paginate(3, null);
        assertThat(cmd.action()).isEqualTo("paginate");
        assertThat(cmd.page()).isEqualTo(3);
    }

    @Test
    void paginateWithDirection() {
        AgentCommand cmd = tools.paginate(null, "next");
        assertThat(cmd.action()).isEqualTo("paginate");
        assertThat(cmd.direction()).isEqualTo("next");
    }

    @Test
    void paginateWithoutTargetDegradesToClarify() {
        AgentCommand cmd = tools.paginate(null, null);
        assertThat(cmd.action()).isEqualTo("clarify");
    }

    @Test
    void selectItemWithIndex() {
        AgentCommand cmd = tools.selectItem(2);
        assertThat(cmd.action()).isEqualTo("selectItem");
        assertThat(cmd.itemIndex()).isEqualTo(2);
    }

    @Test
    void selectItemWithoutIndexDegradesToClarify() {
        AgentCommand cmd = tools.selectItem(null);
        assertThat(cmd.action()).isEqualTo("clarify");
    }

    @Test
    void mapFocusWithIndex() {
        AgentCommand cmd = tools.mapFocus(1);
        assertThat(cmd.action()).isEqualTo("mapFocus");
        assertThat(cmd.itemIndex()).isEqualTo(1);
    }

    @Test
    void resetBuildsResetCommand() {
        AgentCommand cmd = tools.reset();
        assertThat(cmd.action()).isEqualTo("reset");
    }
}
