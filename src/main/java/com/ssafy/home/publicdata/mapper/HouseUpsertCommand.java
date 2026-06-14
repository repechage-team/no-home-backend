package com.ssafy.home.publicdata.mapper;

public record HouseUpsertCommand(
        Long regionId,
        String sggCd,
        String umdNm,
        String jibun,
        String aptNm,
        Integer buildYear
) {
}
