package com.ssafy.home.publicdata.mapper;

public record HouseIdMapping(
        Long houseId,
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        Integer buildYear
) {
}
