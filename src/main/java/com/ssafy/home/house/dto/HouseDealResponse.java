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
        LocalDate dealDate,
        String dealAmount,
        Integer dealAmountManwon,
        BigDecimal excluUseAr,
        Integer floor
) {
}
