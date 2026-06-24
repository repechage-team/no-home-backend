package com.ssafy.home.interest.dto;

import java.time.LocalDateTime;

public record InterestRegionResponse(
        Long interestRegionId,
        Long regionId,
        String lawdCd,
        String legalDongCode,
        String sido,
        String sigungu,
        String umdNm,
        LocalDateTime createdAt
) {
    public static InterestRegionResponse from(InterestRegion interestRegion) {
        return new InterestRegionResponse(
                interestRegion.interestRegionId(),
                interestRegion.regionId(),
                interestRegion.lawdCd(),
                interestRegion.legalDongCode(),
                interestRegion.sido(),
                interestRegion.sigungu(),
                interestRegion.umdNm(),
                interestRegion.createdAt()
        );
    }
}
