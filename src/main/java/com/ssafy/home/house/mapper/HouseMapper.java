package com.ssafy.home.house.mapper;

import com.ssafy.home.house.dto.HouseDealResponse;
import com.ssafy.home.house.dto.HouseResponse;
import com.ssafy.home.house.dto.HouseSearchCondition;
import com.ssafy.home.house.dto.HouseSearchResultResponse;
import com.ssafy.home.house.dto.ImportBatchResponse;
import com.ssafy.home.house.dto.RegionResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface HouseMapper {

    List<RegionResponse> selectRegionsByLawdCd(@Param("lawdCd") String lawdCd);

    List<HouseResponse> selectHousesByAptName(@Param("aptName") String aptName);

    List<HouseDealResponse> selectHouseDeals(@Param("lawdCd") String lawdCd, @Param("dealYmd") String dealYmd);

    List<HouseSearchResultResponse> searchHouseDeals(@Param("condition") HouseSearchCondition condition);

    long countHouseDeals(@Param("condition") HouseSearchCondition condition);

    Optional<ImportBatchResponse> selectImportBatch(
            @Param("sourceApi") String sourceApi,
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("houseType") String houseType,
            @Param("dealType") String dealType
    );
}
