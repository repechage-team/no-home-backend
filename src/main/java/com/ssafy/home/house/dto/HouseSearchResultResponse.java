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
        LocalDate dealDate,
        String dealAmount,
        Integer dealAmountManwon,
        BigDecimal excluUseAr,
        Integer floor,
        BigDecimal lat,
        BigDecimal lng
) {
}
