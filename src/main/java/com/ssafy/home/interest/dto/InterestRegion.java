package com.ssafy.home.interest.dto;

import java.time.LocalDateTime;

public record InterestRegion(
        Long interestRegionId,
        Long memberId,
        Long regionId,
        String lawdCd,
        String legalDongCode,
        String sido,
        String sigungu,
        String umdNm,
        LocalDateTime createdAt
) {
}
