package com.ssafy.home.house.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.house.dto.HouseDealPriceRangeResponse;
import com.ssafy.home.house.dto.HouseDealResponse;
import com.ssafy.home.house.dto.HouseResponse;
import com.ssafy.home.house.dto.HouseSearchCondition;
import com.ssafy.home.house.dto.HouseSearchPageResponse;
import com.ssafy.home.house.dto.HouseSearchResultResponse;
import com.ssafy.home.house.dto.ImportBatchResponse;
import com.ssafy.home.house.dto.RegionResponse;
import com.ssafy.home.house.mapper.HouseMapper;
import com.ssafy.home.publicdata.dto.PublicDataImportResult;
import com.ssafy.home.publicdata.service.PublicDataImportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseServiceTest {

    @Test
    void findRegionsMergesCatalogAndRepairsMojibake() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.regions = List.of(new RegionResponse(1L, "11590", "1159010500",
                "\u00ec\u201a\u00c1\u00ec\u2014\u00b8\u00eb\u00ac\u00a1\u00eb\u00a1\u0153",
                "\u00eb\u008f\u2122\u00ec\u017e\u2018\u00ea\u00b5\u00ac",
                "\u00ed\u009d\u2018\u00ec\u201e\u009d\u00eb\u008f\u2122",
                null, null));
        HouseService service = new HouseService(mapper);

        List<RegionResponse> regions = service.findRegions("11590");

        assertThat(regions).hasSize(9);
        assertThat(regions).extracting(RegionResponse::umdNm)
                .contains("노량진동", "상도1동", "상도동", "흑석동");
        assertThat(regions).extracting(RegionResponse::umdNm)
                .noneMatch(umdNm -> umdNm.contains("\u00ed"));
        assertThat(mapper.lastLawdCd).isEqualTo("11590");
    }

    @Test
    void findRegionsReturnsCatalogWhenDatabaseHasNoRows() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.regions = List.of();
        HouseService service = new HouseService(mapper);

        List<RegionResponse> regions = service.findRegions("11650");

        assertThat(regions).extracting(RegionResponse::umdNm)
                .contains("내곡동", "반포동", "서초동", "잠원동")
                .doesNotContain("서초1동");
    }
    @Test
    void findHouseDealsDelegatesToMapperWithLawdCdAndDealYmd() {
        StubHouseMapper mapper = new StubHouseMapper();
        HouseService service = new HouseService(mapper);

        List<HouseDealResponse> deals = service.findHouseDeals("11590", "202405");

        assertThat(deals).hasSize(1);
        assertThat(deals.get(0).aptNm()).isEqualTo("Heukseok River Apt");
        assertThat(mapper.lastLawdCd).isEqualTo("11590");
        assertThat(mapper.lastDealYmd).isEqualTo("202405");
    }

    @Test
    void searchHouseDealsFailsWhenAllConditionsAreBlank() {
        StubHouseMapper mapper = new StubHouseMapper();
        HouseService service = new HouseService(mapper);

        assertThatThrownBy(() -> service.searchHouseDeals(" ", null, null, "", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one search condition");
    }

    @Test
    void searchHouseDealsNormalizesPageSizeAndCapsSize() {
        StubHouseMapper mapper = new StubHouseMapper();
        HouseService service = new HouseService(mapper);

        HouseSearchPageResponse response = service.searchHouseDeals(
                " 11590 ", null, null, null, null, "202605", 0, 200
        );

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(100);
        assertThat(mapper.lastCondition.lawdCd()).isEqualTo("11590");
        assertThat(mapper.lastCondition.dealYmd()).isEqualTo("202605");
        assertThat(mapper.lastCondition.sort()).isEqualTo("latest");
        assertThat(mapper.lastCondition.offset()).isZero();
    }

    @Test
    void searchHouseDealsPassesSortAndPriceConditionsAndReturnsPriceRange() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.priceRange = new HouseDealPriceRangeResponse(100000, 300000);
        HouseService service = new HouseService(mapper);

        HouseSearchPageResponse response = service.searchHouseDeals(
                "11590", null, null, null, null, "202605", null, null, 2, 10, false,
                "priceDesc", 120000, 250000
        );

        assertThat(mapper.lastCondition.sort()).isEqualTo("priceDesc");
        assertThat(mapper.lastCondition.minPrice()).isEqualTo(120000);
        assertThat(mapper.lastCondition.maxPrice()).isEqualTo(250000);
        assertThat(mapper.lastCondition.offset()).isEqualTo(10);
        assertThat(response.minDealAmountManwon()).isEqualTo(100000);
        assertThat(response.maxDealAmountManwon()).isEqualTo(300000);
    }

    @Test
    void searchHouseDealsRejectsInvalidSortAndInvertedPriceRange() {
        HouseService service = new HouseService(new StubHouseMapper());

        assertThatThrownBy(() -> service.searchHouseDeals(
                "11590", null, null, null, null, "202605", null, null, 1, 20, false,
                "unknown", null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sort");

        assertThatThrownBy(() -> service.searchHouseDeals(
                "11590", null, null, null, null, "202605", null, null, 1, 20, false,
                "latest", 300000, 100000
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    void findHouseDealPriceRangeUsesConditionsWithoutCurrentPriceFilter() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.priceRange = new HouseDealPriceRangeResponse(50000, 100000);
        HouseService service = new HouseService(mapper);

        HouseDealPriceRangeResponse response = service.findHouseDealPriceRange(
                "11590", null, null, "Sangdo-dong", null, "202605", null, null, false
        );

        assertThat(response.minDealAmountManwon()).isEqualTo(50000);
        assertThat(response.maxDealAmountManwon()).isEqualTo(100000);
        assertThat(mapper.lastCondition.lawdCd()).isEqualTo("11590");
        assertThat(mapper.lastCondition.umdNm()).isEqualTo("Sangdo-dong");
        assertThat(mapper.lastCondition.minPrice()).isNull();
        assertThat(mapper.lastCondition.maxPrice()).isNull();
    }

    @Test
    void searchHouseDealsPassesExactRegionAndPartialAptNameConditions() {
        StubHouseMapper mapper = new StubHouseMapper();
        HouseService service = new HouseService(mapper);

        service.searchHouseDeals(null, "Seoul", "Dongjak-gu", "Sangdo-dong", "River", null, 2, 10);

        assertThat(mapper.lastCondition.sido()).isEqualTo("Seoul");
        assertThat(mapper.lastCondition.sigungu()).isEqualTo("Dongjak-gu");
        assertThat(mapper.lastCondition.umdNm()).isEqualTo("Sangdo-dong");
        assertThat(mapper.lastCondition.aptName()).isEqualTo("River");
        assertThat(mapper.lastCondition.offset()).isEqualTo(10);
    }

    @Test
    void searchHouseDealsReturnsMapperOrderAndEmptyListWhenNoData() {
        StubHouseMapper mapper = new StubHouseMapper();
        HouseService service = new HouseService(mapper);

        HouseSearchPageResponse response = service.searchHouseDeals("11590", null, null, null, "none", null, 1, 20);

        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void searchHouseDealsSkipsImportWhenCoverageIsComplete() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.importBatches = Map.of("11590:202605", completedBatch("11590", "202605"));
        PublicDataImportService importService = mock(PublicDataImportService.class);
        HouseService service = new HouseService(mapper, importService, new SeoulLawdCodeResolver());

        HouseSearchPageResponse response = service.searchHouseDeals(
                "11590", null, null, null, null, "202605", 1, 20, true
        );

        verify(importService, never()).importAptTrades("11590", "202605");
        assertThat(response.autoImportAttempted()).isTrue();
        assertThat(response.skippedRanges()).hasSize(1);
        assertThat(response.importedRanges()).isEmpty();
    }

    @Test
    void searchHouseDealsImportsOnlyMissingCoverageThenSearchesDb() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.importBatches = Map.of("11590:202605", partialBatch("11590", "202605"));
        PublicDataImportService importService = mock(PublicDataImportService.class);
        when(importService.importAptTrades("11590", "202605"))
                .thenReturn(new PublicDataImportResult("RTMSDataSvcAptTrade", "11590", "202605",
                        "success", 1, 1, 0, false, "import completed"));
        HouseService service = new HouseService(mapper, importService, new SeoulLawdCodeResolver());

        HouseSearchPageResponse response = service.searchHouseDeals(
                "11590", null, null, null, null, "202605", 1, 20, true
        );

        verify(importService).importAptTrades("11590", "202605");
        assertThat(response.autoImportAttempted()).isTrue();
        assertThat(response.importedRanges()).hasSize(1);
        assertThat(mapper.countCalls).isEqualTo(1);
    }

    @Test
    void successBatchWithLowerImportedAndSkippedCountIsNotCompleteCoverage() {
        StubHouseMapper mapper = new StubHouseMapper();
        mapper.importBatches = Map.of("11590:202605", undercountSuccessBatch("11590", "202605"));
        PublicDataImportService importService = mock(PublicDataImportService.class);
        when(importService.importAptTrades("11590", "202605"))
                .thenReturn(new PublicDataImportResult("RTMSDataSvcAptTrade", "11590", "202605",
                        "success", 10, 10, 0, false, "import completed"));
        HouseService service = new HouseService(mapper, importService, new SeoulLawdCodeResolver());

        service.searchHouseDeals("11590", null, null, null, null, "202605", 1, 20, true);

        verify(importService).importAptTrades("11590", "202605");
    }

    @Test
    void searchHouseDealsDoesNotAutoImportWithoutDealYmd() {
        StubHouseMapper mapper = new StubHouseMapper();
        PublicDataImportService importService = mock(PublicDataImportService.class);
        HouseService service = new HouseService(mapper, importService, new SeoulLawdCodeResolver());

        HouseSearchPageResponse response = service.searchHouseDeals(
                "11590", null, null, null, null, null, 1, 20, true
        );

        verify(importService, never()).importAptTrades("11590", null);
        assertThat(response.autoImportAttempted()).isFalse();
    }

    @Test
    void searchHouseDealsDoesNotAutoImportWhenOnlyAptNameExists() {
        StubHouseMapper mapper = new StubHouseMapper();
        PublicDataImportService importService = mock(PublicDataImportService.class);
        HouseService service = new HouseService(mapper, importService, new SeoulLawdCodeResolver());

        HouseSearchPageResponse response = service.searchHouseDeals(
                null, null, null, null, "River", null, 1, 20, true
        );

        verify(importService, never()).importAptTrades(null, null);
        assertThat(response.autoImportAttempted()).isFalse();
    }

    @Test
    void searchHouseDealsRespectsAutoImportFalse() {
        StubHouseMapper mapper = new StubHouseMapper();
        PublicDataImportService importService = mock(PublicDataImportService.class);
        HouseService service = new HouseService(mapper, importService, new SeoulLawdCodeResolver());

        HouseSearchPageResponse response = service.searchHouseDeals(
                "11590", null, null, null, null, "202605", 1, 20, false
        );

        verify(importService, never()).importAptTrades("11590", "202605");
        assertThat(response.autoImportAttempted()).isFalse();
    }

    @Test
    void dongjakNameResolvesTo11590() {
        HouseService service = new HouseService(new StubHouseMapper());

        assertThat(service.resolveAutoImportLawdCds(null, "\uC11C\uC6B8\uD2B9\uBCC4\uC2DC", "\uB3D9\uC791\uAD6C"))
                .containsExactly("11590");
    }

    @Test
    void seoulSidoExpandsToAll25SigunguCodes() {
        HouseService service = new HouseService(new StubHouseMapper());

        List<String> lawdCds = service.resolveAutoImportLawdCds(null, "\uC11C\uC6B8\uD2B9\uBCC4\uC2DC", null);

        assertThat(lawdCds).hasSize(25);
        assertThat(lawdCds).contains("11110", "11590", "11740");
    }

    private static ImportBatchResponse completedBatch(String lawdCd, String dealYmd) {
        return new ImportBatchResponse(1L, "RTMSDataSvcAptTrade", lawdCd, dealYmd, "apartment", "sale",
                "success", 10, 8, 2, null, null, null);
    }

    private static ImportBatchResponse partialBatch(String lawdCd, String dealYmd) {
        return new ImportBatchResponse(1L, "RTMSDataSvcAptTrade", lawdCd, dealYmd, "apartment", "sale",
                "partial", 10, 8, 2, null, null, null);
    }

    private static ImportBatchResponse undercountSuccessBatch(String lawdCd, String dealYmd) {
        return new ImportBatchResponse(1L, "RTMSDataSvcAptTrade", lawdCd, dealYmd, "apartment", "sale",
                "success", 10, 7, 2, null, null, null);
    }

    private static class StubHouseMapper implements HouseMapper {
        private String lastLawdCd;
        private String lastDealYmd;
        private HouseSearchCondition lastCondition;
        private HouseDealPriceRangeResponse priceRange = new HouseDealPriceRangeResponse(150000, 205000);
        private Map<String, ImportBatchResponse> importBatches = Map.of();
        private List<RegionResponse> regions = List.of(new RegionResponse(1L, "11590", "1159010500",
                "서울특별시", "동작구", "흑석동", null, null));
        private int countCalls;

        @Override
        public List<RegionResponse> selectRegionsByLawdCd(String lawdCd) {
            this.lastLawdCd = lawdCd;
            return regions;
        }
        @Override
        public List<HouseResponse> selectHousesByAptName(String aptName) {
            return List.of();
        }

        @Override
        public List<HouseDealResponse> selectHouseDeals(String lawdCd, String dealYmd) {
            this.lastLawdCd = lawdCd;
            this.lastDealYmd = dealYmd;
            return List.of(new HouseDealResponse(
                    1L, 1L, "Heukseok River Apt", "흑석동", "10", lawdCd, dealYmd,
                    null, "150,000", 150000, null, 12
            ));
        }

        @Override
        public List<HouseSearchResultResponse> searchHouseDeals(HouseSearchCondition condition) {
            this.lastCondition = condition;
            return List.of(new HouseSearchResultResponse(
                    2L, 1L, "River Apt", "Seoul", "Dongjak-gu", "Sangdo-dong", "335", 2018,
                    "11590", "202605", LocalDate.of(2026, 5, 20), "205,000", 205000,
                    null, 18, null, null
            ));
        }

        @Override
        public long countHouseDeals(HouseSearchCondition condition) {
            this.lastCondition = condition;
            countCalls++;
            if ("none".equals(condition.aptName())) {
                return 0;
            }
            return 1;
        }

        @Override
        public HouseDealPriceRangeResponse selectHouseDealPriceRange(HouseSearchCondition condition) {
            this.lastCondition = condition;
            if ("none".equals(condition.aptName())) {
                return new HouseDealPriceRangeResponse(null, null);
            }
            return priceRange;
        }

        @Override
        public Optional<ImportBatchResponse> selectImportBatch(
                String sourceApi,
                String lawdCd,
                String dealYmd,
                String houseType,
                String dealType
        ) {
            return Optional.ofNullable(importBatches.get(lawdCd + ":" + dealYmd));
        }
    }
}
