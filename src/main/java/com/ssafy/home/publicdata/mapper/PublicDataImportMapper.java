package com.ssafy.home.publicdata.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Optional;

public interface PublicDataImportMapper {

    Optional<Long> selectSuccessBatchId(
            @Param("sourceApi") String sourceApi,
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("houseType") String houseType,
            @Param("dealType") String dealType
    );

    void upsertRequestedBatch(
            @Param("sourceApi") String sourceApi,
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("houseType") String houseType,
            @Param("dealType") String dealType
    );

    void updateBatchSuccess(
            @Param("sourceApi") String sourceApi,
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("houseType") String houseType,
            @Param("dealType") String dealType,
            @Param("totalCount") int totalCount,
            @Param("importedCount") int importedCount,
            @Param("skippedCount") int skippedCount
    );

    void updateBatchFailed(
            @Param("sourceApi") String sourceApi,
            @Param("lawdCd") String lawdCd,
            @Param("dealYmd") String dealYmd,
            @Param("houseType") String houseType,
            @Param("dealType") String dealType,
            @Param("errorMessage") String errorMessage
    );

    void upsertRegion(
            @Param("lawdCd") String lawdCd,
            @Param("sido") String sido,
            @Param("sigungu") String sigungu,
            @Param("umdNm") String umdNm
    );

    Optional<Long> selectRegionId(
            @Param("lawdCd") String lawdCd,
            @Param("umdNm") String umdNm
    );

    void upsertHouse(@Param("command") HouseUpsertCommand command);

    Optional<Long> selectHouseId(@Param("command") HouseUpsertCommand command);

    int insertHouseDealIfAbsent(@Param("command") HouseDealInsertCommand command);
}
