package com.ssafy.home.ai.tool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.house.dto.HouseSearchPageResponse;
import com.ssafy.home.house.dto.HouseSearchResultResponse;
import com.ssafy.home.house.service.AutoImportException;
import com.ssafy.home.house.service.HouseService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HouseToolsTest {

    private final HouseService houseService = mock(HouseService.class);
    private final SeoulLawdCodeResolver lawdCodeResolver = mock(SeoulLawdCodeResolver.class);
    private final HouseTools houseTools = new HouseTools(houseService, lawdCodeResolver);

    @Test
    void rejectsUnsupportedRegionWithoutSearching() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "부산진구"))
                .thenReturn(List.of());

        String result = houseTools.searchSeoulAptDeals("부산진구", null, null, "202405");

        assertThat(result)
                .contains("지원하지 않는 지역입니다")
                .contains("부산진구");
        verifyNoInteractions(houseService);
    }

    @Test
    void summarizesSearchResultsAndUsesExistingHouseService() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "동작구"))
                .thenReturn(List.of("11590"));
        when(houseService.searchHouseDeals(
                "11590", null, null, "흑석동", null, "202405", 1, 100, true
        )).thenReturn(new HouseSearchPageResponse(List.of(
                deal("낮은가격아파트", 10_000, 3),
                deal("높은가격아파트", 30_000, 15)
        ), 1, 100, 120));

        String result = houseTools.searchSeoulAptDeals("동작구", "흑석동", null, "202405");

        assertThat(result)
                .contains("총 거래 건수: 120건")
                .contains("이하 통계는 표본 2건 기준")
                .contains("평균 거래가: 2억원 (20,000만원)")
                .contains("최저 거래가: 1억원 (10,000만원)")
                .contains("최고 거래가: 3억원 (30,000만원)");
        assertThat(result.indexOf("높은가격아파트"))
                .isLessThan(result.indexOf("낮은가격아파트"));
        verify(houseService).searchHouseDeals(
                "11590", null, null, "흑석동", null, "202405", 1, 100, true
        );
    }

    @Test
    void reportsWhenNoDealsMatch() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "강남구"))
                .thenReturn(List.of("11680"));
        when(houseService.searchHouseDeals(
                "11680", null, null, null, "없는아파트", "202405", 1, 100, true
        )).thenReturn(new HouseSearchPageResponse(List.of(), 1, 100, 0));

        String result = houseTools.searchSeoulAptDeals("강남구", null, "없는아파트", "202405");

        assertThat(result)
                .contains("조건에 맞는 실거래가가 없습니다")
                .contains("구=강남구")
                .contains("아파트=없는아파트")
                .contains("거래연월=202405");
    }

    @Test
    void debugLogContainsOnlyAggregateMetadata() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "동작구"))
                .thenReturn(List.of("11590"));
        when(houseService.searchHouseDeals(
                "11590", null, null, "흑석동", "비밀아파트", "202405", 1, 100, true
        )).thenReturn(new HouseSearchPageResponse(List.of(
                deal("비밀아파트", 30_000, 15)
        ), 1, 100, 120));

        Logger logger = (Logger) LoggerFactory.getLogger(HouseTools.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        try {
            houseTools.searchSeoulAptDeals("동작구", "흑석동", "비밀아파트", "202405");

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .singleElement()
                    .asString()
                    .contains("totalCount=120", "sampleCount=1", "pricedCount=1", "truncated=true")
                    .doesNotContain("동작구", "흑석동", "비밀아파트", "202405", "30,000");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    void propagatesSearchFailureWithoutExposingItAsToolText() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "마포구"))
                .thenReturn(List.of("11440"));
        when(houseService.searchHouseDeals(
                "11440", null, null, null, null, "202403", 1, 100, true
        )).thenThrow(new IllegalStateException("upstream timeout"));

        assertThatThrownBy(() -> houseTools.searchSeoulAptDeals("마포구", null, null, "202403"))
                .isInstanceOf(HouseToolException.class)
                .hasMessage("House deal lookup failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedDealYmdWithoutCallingService() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "강남구"))
                .thenReturn(List.of("11680"));

        String result = houseTools.searchSeoulAptDeals("강남구", null, null, "2024-05");

        assertThat(result)
                .contains("YYYYMM")
                .contains("2024-05");
        verifyNoInteractions(houseService);
    }

    @Test
    void rejectsFutureDealYmdWithoutCallingService() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "강남구"))
                .thenReturn(List.of("11680"));

        // 9999년 12월은 항상 미래이므로 실제 시계와 무관하게 결정적이다.
        String result = houseTools.searchSeoulAptDeals("강남구", null, null, "999912");

        assertThat(result).contains("미래 월은 조회할 수 없어요");
        verifyNoInteractions(houseService);
    }

    @Test
    void dealYmdErrorValidatesFormatAndRange() {
        YearMonth now = YearMonth.of(2026, 6);

        assertThat(HouseTools.dealYmdError("202405", now)).isNull();
        assertThat(HouseTools.dealYmdError("202606", now)).isNull(); // 현재 월 허용
        assertThat(HouseTools.dealYmdError(null, now)).isNull();
        assertThat(HouseTools.dealYmdError("   ", now)).isNull();

        assertThat(HouseTools.dealYmdError("2024", now)).isNotNull();   // 자리수 부족
        assertThat(HouseTools.dealYmdError("2024-05", now)).isNotNull(); // 형식 오류
        assertThat(HouseTools.dealYmdError("202413", now)).isNotNull(); // 월 범위 초과
        assertThat(HouseTools.dealYmdError("200512", now)).isNotNull(); // 2006년 이전
        assertThat(HouseTools.dealYmdError("202607", now)).isNotNull(); // 미래 월
    }

    @Test
    void returnsFriendlyFallbackWhenAutoImportFails() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "마포구"))
                .thenReturn(List.of("11440"));
        when(houseService.searchHouseDeals(
                "11440", null, null, null, null, "202403", 1, 100, true
        )).thenThrow(new AutoImportException(
                AutoImportException.Reason.QUOTA,
                "public data import failed",
                new RuntimeException("upstream 503")
        ));

        String result = houseTools.searchSeoulAptDeals("마포구", null, null, "202403");

        assertThat(result)
                .contains("공공데이터 호출 한도")
                .contains("다시 시도")
                .contains("구=마포구")
                .doesNotContain("public data import failed", "upstream 503");
    }

    @Test
    void asksForDealYmdWhenMissingAndNoData() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "강남구"))
                .thenReturn(List.of("11680"));
        when(houseService.searchHouseDeals(
                "11680", null, null, null, null, null, 1, 100, true
        )).thenReturn(new HouseSearchPageResponse(List.of(), 1, 100, 0));

        String result = houseTools.searchSeoulAptDeals("강남구", null, null, null);

        assertThat(result)
                .contains("거래연월을 함께 알려주세요")
                .contains("202405");
    }

    @Test
    void notesWholePeriodWhenDealYmdMissingButDataExists() {
        when(lawdCodeResolver.resolveLawdCds(null, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, "동작구"))
                .thenReturn(List.of("11590"));
        when(houseService.searchHouseDeals(
                "11590", null, null, null, null, null, 1, 100, true
        )).thenReturn(new HouseSearchPageResponse(List.of(
                deal("보유아파트", 20_000, 5)
        ), 1, 100, 1));

        String result = houseTools.searchSeoulAptDeals("동작구", null, null, null);

        assertThat(result)
                .contains("보유 중인 전체 기간 기준")
                .contains("총 거래 건수: 1건")
                .contains("보유아파트");
    }

    private static HouseSearchResultResponse deal(String aptName, int amountManwon, int floor) {
        return new HouseSearchResultResponse(
                1L, 2L, aptName, "서울특별시", "동작구", "흑석동", "1", 2020,
                "11590", "202405", LocalDate.of(2024, 5, 20), String.valueOf(amountManwon),
                amountManwon, BigDecimal.valueOf(84.9), floor, null, null
        );
    }
}
