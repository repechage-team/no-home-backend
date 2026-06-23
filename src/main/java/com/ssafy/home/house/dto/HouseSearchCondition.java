package com.ssafy.home.house.dto;

public record HouseSearchCondition(
        String dealMode,
        String lawdCd,
        String sido,
        String sigungu,
        String umdNm,
        String aptName,
        String dealYmd,
        String startDealYmd,
        String endDealYmd,
        String sort,
        Integer minPrice,
        Integer maxPrice,
        Integer minDeposit,
        Integer maxDeposit,
        Integer minMonthlyRent,
        Integer maxMonthlyRent,
        int page,
        int size,
        int offset
) {

    public boolean hasSearchCondition() {
        return hasText(lawdCd)
                || hasText(sido)
                || hasText(sigungu)
                || hasText(umdNm)
                || hasText(aptName)
                || hasText(dealYmd)
                || hasText(startDealYmd)
                || hasText(endDealYmd);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
