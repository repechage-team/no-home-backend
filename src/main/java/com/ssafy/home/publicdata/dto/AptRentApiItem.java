package com.ssafy.home.publicdata.dto;

public record AptRentApiItem(
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        String aptSeq,
        String buildYear,
        String dealYear,
        String dealMonth,
        String dealDay,
        String deposit,
        String monthlyRent,
        String excluUseAr,
        String floor,
        String contractTerm,
        String contractType,
        String useRRRight,
        String preDeposit,
        String preMonthlyRent,
        String roadnm,
        String roadnmsggcd,
        String roadnmcd,
        String roadnmseq,
        String roadnmbcd,
        String roadnmbonbun,
        String roadnmbubun
) {
}
