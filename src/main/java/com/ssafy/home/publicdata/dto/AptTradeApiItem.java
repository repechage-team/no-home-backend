package com.ssafy.home.publicdata.dto;

public record AptTradeApiItem(
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        String buildYear,
        String dealYear,
        String dealMonth,
        String dealDay,
        String dealAmount,
        String excluUseAr,
        String floor,
        String aptDong,
        String buyerGbn,
        String slerGbn,
        String dealingGbn,
        String estateAgentSggNm,
        String cdealType,
        String cdealDay,
        String rgstDate,
        String landLeaseholdGbn
) {
}
