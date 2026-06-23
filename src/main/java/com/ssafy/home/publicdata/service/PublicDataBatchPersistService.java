package com.ssafy.home.publicdata.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.publicdata.dto.PublicDataImportResult;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import com.ssafy.home.publicdata.mapper.HouseIdMapping;
import com.ssafy.home.publicdata.mapper.HouseUpsertCommand;
import com.ssafy.home.publicdata.mapper.PublicDataImportMapper;
import com.ssafy.home.publicdata.mapper.RegionIdMapping;
import com.ssafy.home.publicdata.mapper.RegionIdentity;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PublicDataBatchPersistService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataBatchPersistService.class);
    private static final int INSERT_CHUNK_SIZE = 500;

    private final PublicDataImportMapper mapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public PublicDataBatchPersistService(PublicDataImportMapper mapper) {
        this.mapper = mapper;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public void persistAsync(PersistRequest request) {
        executor.execute(() -> {
            try {
                persist(request);
            } catch (RuntimeException exception) {
                log.warn("Async public data persist failed: sourceApi={}, lawdCd={}, dealYmd={}, cause={}",
                        request.sourceApi(), request.lawdCd(), request.dealYmd(), exception.getClass().getSimpleName());
            }
        });
    }

    @Transactional
    public PublicDataImportResult persist(PersistRequest request) {
        if (mapper.selectSuccessBatchId(request.sourceApi(), request.lawdCd(), request.dealYmd(),
                request.houseType(), request.batchDealType()).isPresent()) {
            return new PublicDataImportResult(request.sourceApi(), request.lawdCd(), request.dealYmd(), "success",
                    0, 0, 0, true, "success batch already exists; skipped normal import");
        }

        mapper.upsertRequestedBatch(request.sourceApi(), request.lawdCd(), request.dealYmd(),
                request.houseType(), request.batchDealType());
        try {
            PersistCounts counts = persistRows(request.rows());
            mapper.updateBatchSuccess(request.sourceApi(), request.lawdCd(), request.dealYmd(),
                    request.houseType(), request.batchDealType(), request.totalCount(),
                    counts.importedCount(), counts.skippedCount());
            return new PublicDataImportResult(request.sourceApi(), request.lawdCd(), request.dealYmd(), "success",
                    request.totalCount(), counts.importedCount(), counts.skippedCount(), false, "import completed");
        } catch (RuntimeException exception) {
            mapper.updateBatchFailed(request.sourceApi(), request.lawdCd(), request.dealYmd(),
                    request.houseType(), request.batchDealType(), exception.getMessage());
            throw exception;
        }
    }

    private PersistCounts persistRows(List<PersistRow> rows) {
        if (rows.isEmpty()) {
            return new PersistCounts(0, 0);
        }

        List<RegionIdentity> regions = dedupeRegions(rows);
        if (!regions.isEmpty()) {
            mapper.upsertRegions(regions);
        }

        Map<RegionKey, Long> regionIds = new LinkedHashMap<>();
        for (RegionIdMapping mapping : mapper.selectRegionIds(regions)) {
            regionIds.put(new RegionKey(mapping.lawdCd(), mapping.umdNm()), mapping.regionId());
        }

        List<HouseUpsertCommand> houses = dedupeHouses(rows, regionIds);
        if (!houses.isEmpty()) {
            for (List<HouseUpsertCommand> chunk : chunks(houses)) {
                mapper.upsertHouses(chunk);
            }
        }

        Map<HouseKey, Long> houseIds = new LinkedHashMap<>();
        for (List<HouseUpsertCommand> chunk : chunks(houses)) {
            for (HouseIdMapping mapping : mapper.selectHouseIds(chunk)) {
                houseIds.put(new HouseKey(mapping.sggCd(), mapping.umdNm(), mapping.jibun(), mapping.aptNm(),
                        mapping.buildYear()), mapping.houseId());
            }
        }

        List<HouseDealInsertCommand> deals = new ArrayList<>();
        for (PersistRow row : rows) {
            HouseUpsertCommand house = row.houseCommand();
            Long houseId = houseIds.get(new HouseKey(house.sggCd(), house.umdNm(), house.jibun(), house.aptNm(),
                    house.buildYear()));
            if (houseId == null) {
                throw new IllegalStateException("house batch upsert failed: " + house.aptNm());
            }
            deals.add(row.dealCommandWithHouseId(houseId));
        }

        int imported = 0;
        for (List<HouseDealInsertCommand> chunk : chunks(deals)) {
            imported += mapper.insertHouseDealsIfAbsent(chunk);
        }
        return new PersistCounts(imported, Math.max(0, rows.size() - imported));
    }

    private static List<RegionIdentity> dedupeRegions(List<PersistRow> rows) {
        Map<RegionKey, RegionIdentity> regions = new LinkedHashMap<>();
        for (PersistRow row : rows) {
            regions.putIfAbsent(new RegionKey(row.lawdCd(), row.umdNm()),
                    new RegionIdentity(row.lawdCd(), SeoulLawdCodeResolver.SEOUL_SIDO_NAME,
                            row.sigungu(), row.umdNm()));
        }
        return new ArrayList<>(regions.values());
    }

    private static List<HouseUpsertCommand> dedupeHouses(List<PersistRow> rows, Map<RegionKey, Long> regionIds) {
        Map<HouseKey, HouseUpsertCommand> houses = new LinkedHashMap<>();
        for (PersistRow row : rows) {
            Long regionId = regionIds.get(new RegionKey(row.lawdCd(), row.umdNm()));
            if (regionId == null) {
                throw new IllegalStateException("region batch upsert failed: " + row.umdNm());
            }
            HouseUpsertCommand source = row.houseCommand();
            HouseUpsertCommand command = new HouseUpsertCommand(regionId, source.sggCd(), source.umdNm(),
                    source.jibun(), source.aptNm(), source.buildYear());
            houses.putIfAbsent(new HouseKey(command.sggCd(), command.umdNm(), command.jibun(), command.aptNm(),
                    command.buildYear()), command);
        }
        return new ArrayList<>(houses.values());
    }

    private static <T> List<List<T>> chunks(List<T> values) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += INSERT_CHUNK_SIZE) {
            chunks.add(values.subList(start, Math.min(start + INSERT_CHUNK_SIZE, values.size())));
        }
        return chunks;
    }

    public record PersistRequest(
            String sourceApi,
            String lawdCd,
            String dealYmd,
            String houseType,
            String batchDealType,
            int totalCount,
            List<PersistRow> rows
    ) {
    }

    public record PersistRow(
            String lawdCd,
            String sigungu,
            String umdNm,
            HouseUpsertCommand houseCommand,
            HouseDealInsertCommand dealCommand
    ) {
        private HouseDealInsertCommand dealCommandWithHouseId(Long houseId) {
            return new HouseDealInsertCommand(
                    houseId,
                    dealCommand.sourceApi(),
                    dealCommand.lawdCd(),
                    dealCommand.dealYmd(),
                    dealCommand.dealType(),
                    dealCommand.dealYear(),
                    dealCommand.dealMonth(),
                    dealCommand.dealDay(),
                    dealCommand.dealDate(),
                    dealCommand.dealAmount(),
                    dealCommand.dealAmountManwon(),
                    dealCommand.rentType(),
                    dealCommand.deposit(),
                    dealCommand.depositManwon(),
                    dealCommand.monthlyRent(),
                    dealCommand.monthlyRentManwon(),
                    dealCommand.excluUseAr(),
                    dealCommand.floor(),
                    dealCommand.aptDong(),
                    dealCommand.buyerGbn(),
                    dealCommand.slerGbn(),
                    dealCommand.dealingGbn(),
                    dealCommand.estateAgentSggNm(),
                    dealCommand.cdealType(),
                    dealCommand.cdealDay(),
                    dealCommand.rgstDate(),
                    dealCommand.landLeaseholdGbn(),
                    dealCommand.contractTerm(),
                    dealCommand.contractType(),
                    dealCommand.useRRRight(),
                    dealCommand.preDeposit(),
                    dealCommand.preDepositManwon(),
                    dealCommand.preMonthlyRent(),
                    dealCommand.preMonthlyRentManwon(),
                    dealCommand.roadnm(),
                    dealCommand.aptSeq(),
                    dealCommand.apiRowHash(),
                    dealCommand.rawResponse()
            );
        }
    }

    private record PersistCounts(int importedCount, int skippedCount) {
    }

    private record RegionKey(String lawdCd, String umdNm) {
    }

    private record HouseKey(String sggCd, String umdNm, String jibun, String aptNm, Integer buildYear) {
        private HouseKey {
            Objects.requireNonNull(sggCd);
            Objects.requireNonNull(umdNm);
            Objects.requireNonNull(jibun);
            Objects.requireNonNull(aptNm);
        }
    }
}
