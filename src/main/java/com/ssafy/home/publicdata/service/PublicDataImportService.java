package com.ssafy.home.publicdata.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.publicdata.client.PublicDataAptTradeClient;
import com.ssafy.home.publicdata.client.PublicDataAptTradeXmlParser;
import com.ssafy.home.publicdata.dto.AptTradeApiItem;
import com.ssafy.home.publicdata.dto.AptTradeApiResponse;
import com.ssafy.home.publicdata.dto.PublicDataImportResult;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import com.ssafy.home.publicdata.mapper.HouseUpsertCommand;
import com.ssafy.home.publicdata.mapper.PublicDataImportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.DEAL_TYPE;
import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.HOUSE_TYPE;
import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.SOURCE_API;

@Service
public class PublicDataImportService {

    private static final int PAGE_SIZE = 100;

    private final PublicDataAptTradeClient client;
    private final PublicDataAptTradeXmlParser parser;
    private final PublicDataImportMapper mapper;
    private final AptTradeImportCommandFactory commandFactory;
    private final SeoulLawdCodeResolver seoulLawdCodeResolver;

    public PublicDataImportService(
            PublicDataAptTradeClient client,
            PublicDataAptTradeXmlParser parser,
            PublicDataImportMapper mapper,
            AptTradeImportCommandFactory commandFactory,
            SeoulLawdCodeResolver seoulLawdCodeResolver
    ) {
        this.client = client;
        this.parser = parser;
        this.mapper = mapper;
        this.commandFactory = commandFactory;
        this.seoulLawdCodeResolver = seoulLawdCodeResolver;
    }

    @Transactional
    public PublicDataImportResult importAptTrades(String lawdCd, String dealYmd) {
        if (mapper.selectSuccessBatchId(SOURCE_API, lawdCd, dealYmd, HOUSE_TYPE, DEAL_TYPE).isPresent()) {
            return new PublicDataImportResult(SOURCE_API, lawdCd, dealYmd, "success", 0, 0, 0, true,
                    "success batch already exists; skipped normal import");
        }

        mapper.upsertRequestedBatch(SOURCE_API, lawdCd, dealYmd, HOUSE_TYPE, DEAL_TYPE);
        try {
            ImportResult importResult = importAllPages(lawdCd, dealYmd);
            mapper.updateBatchSuccess(
                    SOURCE_API,
                    lawdCd,
                    dealYmd,
                    HOUSE_TYPE,
                    DEAL_TYPE,
                    importResult.totalCount,
                    importResult.counters.importedCount,
                    importResult.counters.skippedCount
            );
            return new PublicDataImportResult(SOURCE_API, lawdCd, dealYmd, "success", importResult.totalCount,
                    importResult.counters.importedCount, importResult.counters.skippedCount, false, "import completed");
        } catch (RuntimeException exception) {
            mapper.updateBatchFailed(SOURCE_API, lawdCd, dealYmd, HOUSE_TYPE, DEAL_TYPE, exception.getMessage());
            throw exception;
        }
    }

    private ImportResult importAllPages(String lawdCd, String dealYmd) {
        ImportCounters counters = new ImportCounters();
        int totalCount = 0;
        int processedCount = 0;
        int pageNo = 1;

        while (true) {
            AptTradeApiResponse response = parser.parse(client.fetchXml(lawdCd, dealYmd, pageNo, PAGE_SIZE));
            if (pageNo == 1) {
                totalCount = response.totalCount();
            }
            if (response.items().isEmpty()) {
                break;
            }

            counters.add(importItems(lawdCd, dealYmd, response));
            processedCount += response.items().size();

            if (processedCount >= totalCount) {
                break;
            }
            pageNo++;
        }

        return new ImportResult(totalCount, counters);
    }

    private ImportCounters importItems(String lawdCd, String dealYmd, AptTradeApiResponse response) {
        ImportCounters counters = new ImportCounters();
        String sigungu = seoulLawdCodeResolver.sigunguName(lawdCd).orElse("");
        for (AptTradeApiItem item : response.items()) {
            mapper.upsertRegion(lawdCd, SeoulLawdCodeResolver.SEOUL_SIDO_NAME, sigungu, item.umdNm());
            Long regionId = mapper.selectRegionId(lawdCd, item.umdNm())
                    .orElseThrow(() -> new IllegalStateException("region upsert failed: " + item.umdNm()));
            HouseUpsertCommand houseCommand = commandFactory.toHouseCommand(lawdCd, regionId, item);
            mapper.upsertHouse(houseCommand);
            Long houseId = mapper.selectHouseId(houseCommand)
                    .orElseThrow(() -> new IllegalStateException("house upsert failed: " + item.aptNm()));
            HouseDealInsertCommand dealCommand = commandFactory.toDealCommand(lawdCd, dealYmd, houseId, item);
            int inserted = mapper.insertHouseDealIfAbsent(dealCommand);
            if (inserted == 1) {
                counters.importedCount++;
            } else {
                counters.skippedCount++;
            }
        }
        return counters;
    }

    private static class ImportCounters {
        private int importedCount;
        private int skippedCount;

        private void add(ImportCounters other) {
            importedCount += other.importedCount;
            skippedCount += other.skippedCount;
        }
    }

    private static class ImportResult {
        private final int totalCount;
        private final ImportCounters counters;

        private ImportResult(int totalCount, ImportCounters counters) {
            this.totalCount = totalCount;
            this.counters = counters;
        }
    }
}
