package com.ssafy.home.house.util;

public record ApiRowHashInput(
        String sourceApi,
        String lawdCd,
        String dealYmd,
        String umdNm,
        String jibun,
        String aptNm,
        String dealYear,
        String dealMonth,
        String dealDay,
        String dealAmount,
        String monthlyRent,
        String excluUseAr,
        String floor
) {
    public ApiRowHashInput(
            String sourceApi,
            String lawdCd,
            String dealYmd,
            String umdNm,
            String jibun,
            String aptNm,
            String dealYear,
            String dealMonth,
            String dealDay,
            String dealAmount,
            String excluUseAr,
            String floor
    ) {
        this(sourceApi, lawdCd, dealYmd, umdNm, jibun, aptNm, dealYear, dealMonth, dealDay, dealAmount, null,
                excluUseAr, floor);
    }
}
