package com.ssafy.home.interest.mapper;

import com.ssafy.home.interest.dto.InterestRegion;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface InterestRegionMapper {

    List<InterestRegion> selectByMemberId(@Param("memberId") Long memberId);

    Optional<Long> selectRegionId(@Param("lawdCd") String lawdCd, @Param("umdNm") String umdNm);

    int insertRegion(
            @Param("lawdCd") String lawdCd,
            @Param("legalDongCode") String legalDongCode,
            @Param("sido") String sido,
            @Param("sigungu") String sigungu,
            @Param("umdNm") String umdNm
    );

    int insertInterestRegion(@Param("memberId") Long memberId, @Param("regionId") Long regionId);

    int deleteInterestRegion(@Param("memberId") Long memberId, @Param("interestRegionId") Long interestRegionId);
}
