package com.ssafy.home.house.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HouseSearchResultResponse(
        Long dealId,
        Long houseId,
        String aptNm,
        String sido,
        String sigungu,
        String umdNm,
        String jibun,
        Integer buildYear,
        String lawdCd,
        String dealYmd,
        String dealType,
        String rentType,
        LocalDate dealDate,
        String dealAmount,
        Integer dealAmountManwon,
        String deposit,
        Integer depositManwon,
        String monthlyRent,
        Integer monthlyRentManwon,
        BigDecimal excluUseAr,
        Integer floor,
        String contractTerm,
        String contractType,
        String useRRRight,
        String preDeposit,
        Integer preDepositManwon,
        String preMonthlyRent,
        Integer preMonthlyRentManwon,
        String roadnm,
        String aptSeq,
        BigDecimal lat,
        BigDecimal lng,
        String resultKey,
        String apiRowHash
) {
    public HouseSearchResultResponse(
            Long dealId,
            Long houseId,
            String aptNm,
            String sido,
            String sigungu,
            String umdNm,
            String jibun,
            Integer buildYear,
            String lawdCd,
            String dealYmd,
            LocalDate dealDate,
            String dealAmount,
            Integer dealAmountManwon,
            BigDecimal excluUseAr,
            Integer floor,
            BigDecimal lat,
            BigDecimal lng
    ) {
        this(dealId, houseId, aptNm, sido, sigungu, umdNm, jibun, buildYear, lawdCd, dealYmd, "sale", null,
                dealDate, dealAmount, dealAmountManwon, null, null, null, null, excluUseAr, floor,
                null, null, null, null, null, null, null, null, null, lat, lng, null, null);
    }
}
