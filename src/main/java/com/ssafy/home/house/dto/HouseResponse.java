package com.ssafy.home.house.dto;

import java.math.BigDecimal;

public record HouseResponse(
        Long houseId,
        Long regionId,
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        Integer buildYear,
        BigDecimal lat,
        BigDecimal lng
) {
}
