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
        String excluUseAr,
        String floor
) {
}
