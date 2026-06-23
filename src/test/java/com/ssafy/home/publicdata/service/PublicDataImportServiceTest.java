package com.ssafy.home.publicdata.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.publicdata.client.PublicDataAptTradeClient;
import com.ssafy.home.publicdata.client.PublicDataAptTradeXmlParser;
import com.ssafy.home.publicdata.client.PublicDataApiKeyProvider;
import com.ssafy.home.publicdata.dto.PublicDataImportResult;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import com.ssafy.home.publicdata.mapper.HouseUpsertCommand;
import com.ssafy.home.publicdata.mapper.PublicDataImportMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PublicDataImportServiceTest {

    @Test
    void importSkipsWhenSuccessBatchAlreadyExists() {
        StubMapper mapper = new StubMapper();
        mapper.successBatchExists = true;
        PublicDataImportService service = newService(mapper, sampleXml());

        PublicDataImportResult result = service.importAptTrades("11590", "202405");

        assertThat(result.alreadyImported()).isTrue();
        assertThat(mapper.requestedBatchCount).isZero();
        assertThat(mapper.insertDealAttempts).isZero();
    }

    @Test
    void importInsertsFirstApiRowAndSkipsDuplicateHashOnRepeatedRows() {
        StubMapper mapper = new StubMapper();
        PublicDataImportService service = newService(mapper, sampleXmlWithDuplicateRows());

        PublicDataImportResult result = service.importAptTrades("11590", "202405");

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(mapper.successBatchTotalCount).isEqualTo(2);
        assertThat(mapper.insertDealAttempts).isEqualTo(2);
    }

    @Test
    void importReadsAdditionalPagesUntilProcessedCountReachesTotalCount() {
        StubMapper mapper = new StubMapper();
        PublicDataImportService service = newService(mapper, Map.of(
                1, """
                        <response><body><totalCount>2</totalCount><items>
                          <item><sggCd>11590</sggCd><umdNm>상도동</umdNm><jibun>10</jibun><aptNm>상도아파트</aptNm><buildYear>2011</buildYear><dealYear>2024</dealYear><dealMonth>5</dealMonth><dealDay>12</dealDay><dealAmount>150,000</dealAmount><excluUseAr>84.970</excluUseAr><floor>12</floor></item>
                        </items></body></response>
                        """,
                2, """
                        <response><body><totalCount>2</totalCount><items>
                          <item><sggCd>11590</sggCd><umdNm>흑석동</umdNm><jibun>20</jibun><aptNm>흑석아파트</aptNm><buildYear>2015</buildYear><dealYear>2024</dealYear><dealMonth>5</dealMonth><dealDay>13</dealDay><dealAmount>170,000</dealAmount><excluUseAr>84.970</excluUseAr><floor>7</floor></item>
                        </items></body></response>
                        """
        ));

        PublicDataImportResult result = service.importAptTrades("11590", "202405");

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(mapper.insertDealAttempts).isEqualTo(2);
    }

    @Test
    void importKeepsSuccessfulZeroRowsAsNoData() {
        StubMapper mapper = new StubMapper();
        // RTMS 실측 정상 코드는 "000"(NORMAL SERVICE). "00"은 공통표준 정상.
        PublicDataImportService service = newService(mapper, """
                <response>
                  <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                  <body><totalCount>0</totalCount><items></items></body>
                </response>
                """);

        PublicDataImportResult result = service.importAptTrades("11590", "202405");

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.totalCount()).isZero();
        assertThat(result.importedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(mapper.successBatchTotalCount).isZero();
        assertThat(mapper.failedBatchCount).isZero();
    }

    @Test
    void importSucceedsWhenResultCodeIs000WithItems() {
        // 회귀 방지: RTMS 정상 응답 resultCode="000" + item이 정상 적재되어야 한다(이전엔 "00"만 인정해 실패 분류됨).
        StubMapper mapper = new StubMapper();
        PublicDataImportService service = newService(mapper, """
                <response>
                  <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                  <body><totalCount>1</totalCount><items>
                    <item><sggCd>11680</sggCd><umdNm>역삼동</umdNm><jibun>10</jibun><aptNm>역삼래미안</aptNm><buildYear>2015</buildYear><dealYear>2024</dealYear><dealMonth>5</dealMonth><dealDay>12</dealDay><dealAmount>250,000</dealAmount><excluUseAr>84.970</excluUseAr><floor>12</floor></item>
                  </items></body>
                </response>
                """);

        PublicDataImportResult result = service.importAptTrades("11680", "202405");

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(mapper.failedBatchCount).isZero();
    }

    @Test
    void importThrowsCategorizedExceptionWhenApiReturnsServiceKeyError() {
        StubMapper mapper = new StubMapper();
        PublicDataImportService service = newService(mapper, """
                <response>
                  <header>
                    <resultCode>30</resultCode>
                    <resultMsg>SERVICE KEY IS NOT REGISTERED ERROR.</resultMsg>
                  </header>
                  <body><totalCount>0</totalCount><items></items></body>
                </response>
                """);

        Throwable thrown = catchThrowable(() -> service.importAptTrades("11590", "202405"));

        assertThat(thrown).isInstanceOf(PublicDataApiException.class);
        assertThat(((PublicDataApiException) thrown).reason()).isEqualTo(PublicDataApiException.Reason.KEY_INVALID);
        assertThat(mapper.failedBatchCount).isEqualTo(1);
        assertThat(mapper.lastFailureMessage).contains("resultCode=30");
    }

    private static PublicDataImportService newService(StubMapper mapper, String xml) {
        return newService(mapper, Map.of(1, xml));
    }

    private static PublicDataImportService newService(StubMapper mapper, Map<Integer, String> xmlByPageNo) {
        PublicDataAptTradeClient client = new PublicDataAptTradeClient(new PublicDataApiKeyProvider("test-key")) {
            @Override
            public String fetchXml(String lawdCd, String dealYmd, int pageNo, int numOfRows) {
                return xmlByPageNo.getOrDefault(pageNo, "<response><body><totalCount>0</totalCount><items></items></body></response>");
            }
        };
        return new PublicDataImportService(
                client,
                new PublicDataAptTradeXmlParser(),
                mapper,
                new AptTradeImportCommandFactory(),
                new SeoulLawdCodeResolver()
        );
    }

    private static String sampleXmlWithDuplicateRows() {
        return """
                <response><body><totalCount>2</totalCount><items>
                  <item><sggCd>11590</sggCd><umdNm>상도동</umdNm><jibun>10</jibun><aptNm>상도아파트</aptNm><buildYear>2011</buildYear><dealYear>2024</dealYear><dealMonth>5</dealMonth><dealDay>12</dealDay><dealAmount>150,000</dealAmount><excluUseAr>84.970</excluUseAr><floor>12</floor></item>
                  <item><sggCd>11590</sggCd><umdNm>상도동</umdNm><jibun>10</jibun><aptNm>상도아파트</aptNm><buildYear>2011</buildYear><dealYear>2024</dealYear><dealMonth>5</dealMonth><dealDay>12</dealDay><dealAmount>150,000</dealAmount><excluUseAr>84.970</excluUseAr><floor>12</floor></item>
                </items></body></response>
                """;
    }

    private static String sampleXml() {
        return """
                <response><body><totalCount>1</totalCount><items>
                  <item><sggCd>11590</sggCd><umdNm>상도동</umdNm><jibun>10</jibun><aptNm>상도아파트</aptNm><buildYear>2011</buildYear><dealYear>2024</dealYear><dealMonth>5</dealMonth><dealDay>12</dealDay><dealAmount>150,000</dealAmount><excluUseAr>84.970</excluUseAr><floor>12</floor></item>
                </items></body></response>
                """;
    }

    private static class StubMapper implements PublicDataImportMapper {
        private boolean successBatchExists;
        private int requestedBatchCount;
        private int insertDealAttempts;
        private int successBatchTotalCount;
        private int failedBatchCount;
        private String lastFailureMessage;
        private final Set<String> hashes = new HashSet<>();

        @Override
        public Optional<Long> selectSuccessBatchId(String sourceApi, String lawdCd, String dealYmd, String houseType, String dealType) {
            return successBatchExists ? Optional.of(1L) : Optional.empty();
        }

        @Override
        public void upsertRequestedBatch(String sourceApi, String lawdCd, String dealYmd, String houseType, String dealType) {
            requestedBatchCount++;
        }

        @Override
        public void updateBatchSuccess(String sourceApi, String lawdCd, String dealYmd, String houseType, String dealType, int totalCount, int importedCount, int skippedCount) {
            successBatchTotalCount = totalCount;
        }

        @Override
        public void updateBatchFailed(String sourceApi, String lawdCd, String dealYmd, String houseType, String dealType, String errorMessage) {
            failedBatchCount++;
            lastFailureMessage = errorMessage;
        }

        @Override
        public void upsertRegion(String lawdCd, String sido, String sigungu, String umdNm) {
        }

        @Override
        public Optional<Long> selectRegionId(String lawdCd, String umdNm) {
            return Optional.of(7L);
        }

        @Override
        public void upsertHouse(HouseUpsertCommand command) {
        }

        @Override
        public Optional<Long> selectHouseId(HouseUpsertCommand command) {
            return Optional.of(11L);
        }

        @Override
        public int insertHouseDealIfAbsent(HouseDealInsertCommand command) {
            insertDealAttempts++;
            return hashes.add(command.apiRowHash()) ? 1 : 0;
        }
    }
}
