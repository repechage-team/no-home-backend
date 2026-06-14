package com.ssafy.home.house.dto;

import java.math.BigDecimal;

public record RegionResponse(
        Long regionId,
        String lawdCd,
        String legalDongCode,
        String sido,
        String sigungu,
        String umdNm,
        BigDecimal lat,
        BigDecimal lng
) {
}
