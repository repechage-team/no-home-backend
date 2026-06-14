package com.ssafy.home.house.dto;

public record AutoImportRangeResponse(
        String lawdCd,
        String dealYmd,
        String status,
        String message
) {
}
