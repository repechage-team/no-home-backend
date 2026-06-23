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
import com.ssafy.home.publicdata.service.PublicDataBatchPersistService.PersistRequest;
import com.ssafy.home.publicdata.service.PublicDataBatchPersistService.PersistRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.DEAL_TYPE;
import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.HOUSE_TYPE;
import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.SOURCE_API;

@Service
public class PublicDataImportService {

    private static final int PAGE_SIZE = 1000;

    private final PublicDataAptTradeClient client;
    private final PublicDataAptTradeXmlParser parser;
    private final PublicDataImportMapper mapper;
    private final AptTradeImportCommandFactory commandFactory;
    private final SeoulLawdCodeResolver seoulLawdCodeResolver;
    private final PublicDataBatchPersistService batchPersistService;

    @Autowired
    public PublicDataImportService(
            PublicDataAptTradeClient client,
            PublicDataAptTradeXmlParser parser,
            PublicDataImportMapper mapper,
            AptTradeImportCommandFactory commandFactory,
            SeoulLawdCodeResolver seoulLawdCodeResolver,
            PublicDataBatchPersistService batchPersistService
    ) {
        this.client = client;
        this.parser = parser;
        this.mapper = mapper;
        this.commandFactory = commandFactory;
        this.seoulLawdCodeResolver = seoulLawdCodeResolver;
        this.batchPersistService = batchPersistService;
    }

    PublicDataImportService(
            PublicDataAptTradeClient client,
            PublicDataAptTradeXmlParser parser,
            PublicDataImportMapper mapper,
            AptTradeImportCommandFactory commandFactory,
            SeoulLawdCodeResolver seoulLawdCodeResolver
    ) {
        this(client, parser, mapper, commandFactory, seoulLawdCodeResolver, new PublicDataBatchPersistService(mapper));
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
            return batchPersistService.persist(new PersistRequest(SOURCE_API, lawdCd, dealYmd, HOUSE_TYPE, DEAL_TYPE,
                    importResult.totalCount, importResult.rows));
        } catch (RuntimeException exception) {
            mapper.updateBatchFailed(SOURCE_API, lawdCd, dealYmd, HOUSE_TYPE, DEAL_TYPE, exception.getMessage());
            throw exception;
        }
    }

    private ImportResult importAllPages(String lawdCd, String dealYmd) {
        int totalCount = 0;
        int processedCount = 0;
        int pageNo = 1;
        List<PersistRow> rows = new java.util.ArrayList<>();

        while (true) {
            AptTradeApiResponse response = parser.parse(client.fetchXml(lawdCd, dealYmd, pageNo, PAGE_SIZE));
            validateSuccess(response);
            if (pageNo == 1) {
                totalCount = response.totalCount();
            }
            if (response.items().isEmpty()) {
                break;
            }

            rows.addAll(importItems(lawdCd, dealYmd, response));
            processedCount += response.items().size();

            if (processedCount >= totalCount) {
                break;
            }
            pageNo++;
        }

        return new ImportResult(totalCount, rows);
    }

    private static void validateSuccess(AptTradeApiResponse response) {
        if (response.isSuccess()) {
            return;
        }

        throw new PublicDataApiException(
                classifyApiFailure(response.resultCode(), response.resultMsg()),
                response.resultCode(),
                response.resultMsg()
        );
    }

    static PublicDataApiException.Reason classifyApiFailure(String resultCode, String resultMsg) {
        String code = resultCode == null ? "" : resultCode.trim();
        String message = resultMsg == null ? "" : resultMsg.toLowerCase();

        if ("22".equals(code) || message.contains("traffic") || message.contains("quota")) {
            return PublicDataApiException.Reason.QUOTA;
        }
        if ("30".equals(code) || "31".equals(code)
                || message.contains("service key")
                || message.contains("servic key")
                || message.contains("auth")
                || message.contains("expired")
                || message.contains("unregistered")) {
            return PublicDataApiException.Reason.KEY_INVALID;
        }
        return PublicDataApiException.Reason.PROVIDER_ERROR;
    }

    private List<PersistRow> importItems(String lawdCd, String dealYmd, AptTradeApiResponse response) {
        List<PersistRow> rows = new java.util.ArrayList<>();
        String sigungu = seoulLawdCodeResolver.sigunguName(lawdCd).orElse("");
        for (AptTradeApiItem item : response.items()) {
            HouseUpsertCommand houseCommand = commandFactory.toHouseCommand(lawdCd, null, item);
            HouseDealInsertCommand dealCommand = commandFactory.toDealCommand(lawdCd, dealYmd, null, item);
            rows.add(new PersistRow(lawdCd, sigungu, item.umdNm(), houseCommand, dealCommand));
        }
        return rows;
    }

    private static class ImportResult {
        private final int totalCount;
        private final List<PersistRow> rows;

        private ImportResult(int totalCount, List<PersistRow> rows) {
            this.totalCount = totalCount;
            this.rows = rows;
        }
    }
}
