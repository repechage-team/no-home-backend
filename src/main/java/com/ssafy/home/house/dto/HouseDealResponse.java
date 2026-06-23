package com.ssafy.home.house.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HouseDealResponse(
        Long dealId,
        Long houseId,
        String aptNm,
        String umdNm,
        String jibun,
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
        Integer floor
) {
    public HouseDealResponse(
            Long dealId,
            Long houseId,
            String aptNm,
            String umdNm,
            String jibun,
            String lawdCd,
            String dealYmd,
            LocalDate dealDate,
            String dealAmount,
            Integer dealAmountManwon,
            BigDecimal excluUseAr,
            Integer floor
    ) {
        this(dealId, houseId, aptNm, umdNm, jibun, lawdCd, dealYmd, "sale", null, dealDate, dealAmount,
                dealAmountManwon, null, null, null, null, excluUseAr, floor);
    }
}
