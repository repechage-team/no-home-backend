package com.ssafy.home.ai.tool;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.house.dto.HouseSearchPageResponse;
import com.ssafy.home.house.dto.HouseSearchResultResponse;
import com.ssafy.home.house.service.AutoImportException;
import com.ssafy.home.house.service.HouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 챗봇이 호출하는 부동산 질의 Tool.
 * <p>
 * 기존 {@link HouseService#searchHouseDeals}를 그대로 재사용해(자동 임포트 포함) 서울 아파트
 * 실거래가를 조회하고, LLM이 그대로 답변에 활용할 수 있도록 한국어 요약 문자열을 반환한다.
 */
@Component
public class HouseTools {

    private static final Logger log = LoggerFactory.getLogger(HouseTools.class);
    private static final String SEOUL_SIDO = SeoulLawdCodeResolver.SEOUL_SIDO_NAME;
    private static final int SAMPLE_SIZE = 100;
    private static final int LIST_LIMIT = 5;
    private static final int MIN_DEAL_YEAR = 2006; // 국토부 아파트 실거래가 공개 시작 연도
    private static final Pattern DEAL_YMD_PATTERN = Pattern.compile("\\d{6}");

    private final HouseService houseService;
    private final SeoulLawdCodeResolver lawdCodeResolver;

    public HouseTools(HouseService houseService, SeoulLawdCodeResolver lawdCodeResolver) {
        this.houseService = houseService;
        this.lawdCodeResolver = lawdCodeResolver;
    }

    @Tool(description = """
            서울 아파트 실거래가를 조회해 요약한다.
            - 데이터는 서울특별시만 지원한다. 구 이름은 '강남구'처럼 '구'까지 포함해 전달한다.
            - 거래 연월(dealYmd)은 가능하면 함께 전달한다. 형식은 'YYYYMM' 6자리, 2006년 이후이며 미래 월은 조회할 수 없다. 예: 2024년 5월 -> '202405'.
            - 결과는 거래 건수, 평균/최저/최고 거래가(만원), 대표 거래 목록을 포함한다.
            """)
    public String searchSeoulAptDeals(
            @ToolParam(description = "서울 자치구 이름. 예: '강남구', '마포구'") String sigungu,
            @ToolParam(required = false, description = "법정동 이름(선택). 예: '역삼동'") String umdNm,
            @ToolParam(required = false, description = "아파트명 일부(선택). 예: '래미안'") String aptName,
            @ToolParam(required = false, description = "거래 연월 6자리 'YYYYMM'(선택, 권장). 예: '202405'") String dealYmd
    ) {
        // 서울 자치구 이름 -> 법정동 코드(lawdCd) 변환. 기존 검색은 lawdCd 기준으로 동작하므로
        // sigungu를 그대로 넘기지 않고 lawdCd로 변환해 조회한다.
        List<String> lawdCds = lawdCodeResolver.resolveLawdCds(null, SEOUL_SIDO, sigungu);
        if (lawdCds.isEmpty()) {
            return "지원하지 않는 지역입니다. 서울 자치구 이름(예: '강남구', '동작구')으로 알려주세요. (입력: %s)"
                    .formatted(nv(sigungu));
        }
        String lawdCd = lawdCds.get(0);

        String dealYmdError = dealYmdError(dealYmd, YearMonth.now());
        if (dealYmdError != null) {
            return dealYmdError;
        }
        boolean monthSpecified = dealYmd != null && !dealYmd.isBlank();

        HouseSearchPageResponse result;
        try {
            result = houseService.searchHouseDeals(
                    lawdCd, null, null, umdNm, aptName, dealYmd, 1, SAMPLE_SIZE, true);
        } catch (AutoImportException e) {
            // 외부 공공데이터 임포트 지연/실패는 장애가 아니라 데이터 소스 일시 문제로 보고,
            // 503 대신 사용자 친화 폴백 문자열을 반환한다(예외 상세는 응답·로그에 노출하지 않음).
            log.debug("House tool auto-import fallback returned: reason={}", e.reason());
            return autoImportFallbackMessage(e.reason(), sigungu, dealYmd);
        } catch (RuntimeException e) {
            throw new HouseToolException("House deal lookup failed", e);
        }

        List<HouseSearchResultResponse> items = result.items();
        if (items == null || items.isEmpty()) {
            if (!monthSpecified) {
                // 연월 미지정 + 보유 데이터 없음: 임의의 달을 추정 임포트하지 않고 연월을 되묻는다(외부 호출 절약).
                return "거래연월을 함께 알려주세요. 예: 2024년 5월 -> '202405'. 연월을 지정하면 공공데이터에서 해당 월을 조회해 드릴 수 있어요. (구=%s, 동=%s, 아파트=%s)"
                        .formatted(nv(sigungu), nv(umdNm), nv(aptName));
            }
            return "조건에 맞는 실거래가가 없습니다. (구=%s, 동=%s, 아파트=%s, 거래연월=%s)"
                    .formatted(nv(sigungu), nv(umdNm), nv(aptName), nv(dealYmd));
        }

        List<HouseSearchResultResponse> priced = items.stream()
                .filter(it -> it.dealAmountManwon() != null)
                .toList();

        StringBuilder sb = new StringBuilder();
        if (!monthSpecified) {
            sb.append("※ 거래연월을 지정하지 않아 보유 중인 전체 기간 기준으로 요약했어요. 특정 달은 'YYYYMM'으로 알려주세요.")
                    .append(System.lineSeparator());
        }
        sb.append("[조회 조건] 구=%s, 동=%s, 아파트=%s, 거래연월=%s%n"
                .formatted(nv(sigungu), nv(umdNm), nv(aptName), nv(dealYmd)));
        sb.append("총 거래 건수: ").append(result.totalCount()).append("건");
        if (result.totalCount() > items.size()) {
            sb.append(" (이하 통계는 표본 ").append(items.size()).append("건 기준)");
        }
        sb.append(System.lineSeparator());

        if (!priced.isEmpty()) {
            long avg = Math.round(priced.stream().mapToInt(HouseSearchResultResponse::dealAmountManwon).average().orElse(0));
            int min = priced.stream().mapToInt(HouseSearchResultResponse::dealAmountManwon).min().orElse(0);
            int max = priced.stream().mapToInt(HouseSearchResultResponse::dealAmountManwon).max().orElse(0);
            sb.append("평균 거래가: ").append(formatManwon(avg)).append(System.lineSeparator());
            sb.append("최저 거래가: ").append(formatManwon(min)).append(System.lineSeparator());
            sb.append("최고 거래가: ").append(formatManwon(max)).append(System.lineSeparator());
        }

        sb.append("대표 거래:").append(System.lineSeparator());
        priced.stream()
                .sorted(Comparator.comparingInt(HouseSearchResultResponse::dealAmountManwon).reversed())
                .limit(LIST_LIMIT)
                .forEach(it -> sb.append("- ")
                        .append(nv(it.aptNm()))
                        .append(" (").append(nv(it.umdNm())).append(")")
                        .append(", ").append(formatManwon(it.dealAmountManwon()))
                        .append(it.excluUseAr() != null ? ", 전용 " + it.excluUseAr() + "㎡" : "")
                        .append(it.floor() != null ? ", " + it.floor() + "층" : "")
                        .append(it.dealDate() != null ? ", " + it.dealDate() : "")
                        .append(System.lineSeparator()));

        String summary = sb.toString().strip();
        log.debug("House tool completed: totalCount={}, sampleCount={}, pricedCount={}, truncated={}",
                result.totalCount(), items.size(), priced.size(), result.totalCount() > items.size());
        return summary;
    }

    private static String autoImportFallbackMessage(AutoImportException.Reason reason, String sigungu, String dealYmd) {
        String suffix = " (구=%s, 거래연월=%s)".formatted(nv(sigungu), nv(dealYmd));
        return switch (reason) {
            case KEY_MISSING, KEY_INVALID ->
                    "공공데이터 서비스키 설정 또는 유효기간을 확인해야 해서 실거래가 자동 갱신을 완료하지 못했습니다." + suffix;
            case QUOTA ->
                    "공공데이터 호출 한도를 초과해서 실거래가 자동 갱신을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요." + suffix;
            case TIMEOUT ->
                    "공공데이터 응답이 지연되어 실거래가 자동 갱신을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요." + suffix;
            case PROVIDER_ERROR, UNKNOWN ->
                    "공공데이터 제공처 응답 문제로 실거래가 자동 갱신을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요." + suffix;
        };
    }

    private static String formatManwon(long manwon) {
        if (manwon >= 10000) {
            long eok = manwon / 10000;
            long rest = manwon % 10000;
            return rest == 0
                    ? "%d억원 (%,d만원)".formatted(eok, manwon)
                    : "%d억 %,d만원 (%,d만원)".formatted(eok, rest, manwon);
        }
        return "%,d만원".formatted(manwon);
    }

    private static String nv(Object value) {
        return value == null ? "-" : value.toString();
    }

    // 거래연월(dealYmd) 형식·범위 검증. 유효하거나 비어 있으면 null, 잘못되면 사용자 안내 문자열을 반환.
    // 비어 있으면 자동 임포트 없이 DB만 조회하므로 형식 검증 대상이 아니다.
    static String dealYmdError(String dealYmd, YearMonth now) {
        if (dealYmd == null || dealYmd.isBlank()) {
            return null;
        }
        String trimmed = dealYmd.trim();
        if (!DEAL_YMD_PATTERN.matcher(trimmed).matches()) {
            return dealYmdGuide(trimmed);
        }
        int year = Integer.parseInt(trimmed.substring(0, 4));
        int month = Integer.parseInt(trimmed.substring(4, 6));
        if (month < 1 || month > 12) {
            return dealYmdGuide(trimmed);
        }
        YearMonth requested = YearMonth.of(year, month);
        if (year < MIN_DEAL_YEAR || requested.isAfter(now)) {
            return dealYmdGuide(trimmed);
        }
        return null;
    }

    private static String dealYmdGuide(String input) {
        return "거래연월은 'YYYYMM' 6자리로 알려주세요. 2006년 이후이며 미래 월은 조회할 수 없어요. 예: 2024년 5월 -> '202405'. (입력: %s)"
                .formatted(input);
    }
}
