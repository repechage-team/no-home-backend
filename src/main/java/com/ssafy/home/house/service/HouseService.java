package com.ssafy.home.house.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.common.region.SeoulLegalDongCatalog;
import com.ssafy.home.common.text.MojibakeRepairer;
import com.ssafy.home.house.dto.AutoImportRangeResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.DEAL_TYPE;
import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.HOUSE_TYPE;
import static com.ssafy.home.publicdata.service.AptTradeImportCommandFactory.SOURCE_API;

@Service
public class HouseService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "latest";
    private static final Collator KOREAN_COLLATOR = Collator.getInstance(Locale.KOREAN);

    private final HouseMapper houseMapper;
    private final PublicDataImportService publicDataImportService;
    private final SeoulLawdCodeResolver seoulLawdCodeResolver;

    @Autowired
    public HouseService(
            HouseMapper houseMapper,
            PublicDataImportService publicDataImportService,
            SeoulLawdCodeResolver seoulLawdCodeResolver
    ) {
        this.houseMapper = houseMapper;
        this.publicDataImportService = publicDataImportService;
        this.seoulLawdCodeResolver = seoulLawdCodeResolver;
    }

    HouseService(HouseMapper houseMapper) {
        this(houseMapper, null, new SeoulLawdCodeResolver());
    }

    public List<RegionResponse> findRegions(String lawdCd) {
        Map<String, RegionResponse> regionsByDong = new LinkedHashMap<>();

        for (RegionResponse region : houseMapper.selectRegionsByLawdCd(lawdCd)) {
            RegionResponse repaired = repairRegion(region);
            if (hasText(repaired.umdNm())) {
                regionsByDong.putIfAbsent(repaired.umdNm(), repaired);
            }
        }

        for (RegionResponse region : SeoulLegalDongCatalog.regions(lawdCd, seoulLawdCodeResolver)) {
            regionsByDong.putIfAbsent(region.umdNm(), region);
        }

        return regionsByDong.values().stream()
                .sorted(Comparator.comparing(RegionResponse::umdNm, KOREAN_COLLATOR))
                .toList();
    }

    public List<HouseResponse> findHouses(String aptName) {
        return houseMapper.selectHousesByAptName(aptName);
    }

    public List<HouseDealResponse> findHouseDeals(String lawdCd, String dealYmd) {
        return houseMapper.selectHouseDeals(lawdCd, dealYmd);
    }

    public HouseSearchPageResponse searchHouseDeals(
            String lawdCd,
            String sido,
            String sigungu,
            String umdNm,
            String aptName,
            String dealYmd,
            Integer page,
            Integer size
    ) {
        return searchHouseDeals(lawdCd, sido, sigungu, umdNm, aptName, dealYmd, page, size, true);
    }

    public HouseSearchPageResponse searchHouseDeals(
            String lawdCd,
            String sido,
            String sigungu,
            String umdNm,
            String aptName,
            String dealYmd,
            Integer page,
            Integer size,
            Boolean autoImport
    ) {
        return searchHouseDeals(lawdCd, sido, sigungu, umdNm, aptName, dealYmd, null, null, page, size, autoImport,
                DEFAULT_SORT, null, null);
    }

    public HouseSearchPageResponse searchHouseDeals(
            String lawdCd,
            String sido,
            String sigungu,
            String umdNm,
            String aptName,
            String dealYmd,
            String startDealYmd,
            String endDealYmd,
            Integer page,
            Integer size,
            Boolean autoImport,
            String sort,
            Integer minPrice,
            Integer maxPrice
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedSort = normalizeSort(sort);
        Integer normalizedMinPrice = normalizePrice(minPrice, "minPrice");
        Integer normalizedMaxPrice = normalizePrice(maxPrice, "maxPrice");
        if (normalizedMinPrice != null && normalizedMaxPrice != null && normalizedMinPrice > normalizedMaxPrice) {
            throw new IllegalArgumentException("minPrice must be less than or equal to maxPrice.");
        }
        String normalizedDealYmd = trimToNull(dealYmd);
        String normalizedStartDealYmd = trimToNull(startDealYmd);
        String normalizedEndDealYmd = trimToNull(endDealYmd);
        if (normalizedDealYmd != null) {
            normalizedStartDealYmd = null;
            normalizedEndDealYmd = null;
        } else if (normalizedStartDealYmd != null && normalizedEndDealYmd != null
                && normalizedStartDealYmd.compareTo(normalizedEndDealYmd) > 0) {
            throw new IllegalArgumentException("startDealYmd must be less than or equal to endDealYmd.");
        }

        HouseSearchCondition condition = new HouseSearchCondition(
                trimToNull(lawdCd),
                trimToNull(sido),
                trimToNull(sigungu),
                trimToNull(umdNm),
                trimToNull(aptName),
                normalizedDealYmd,
                normalizedStartDealYmd,
                normalizedEndDealYmd,
                normalizedSort,
                normalizedMinPrice,
                normalizedMaxPrice,
                normalizedPage,
                normalizedSize,
                (normalizedPage - 1) * normalizedSize
        );

        if (!condition.hasSearchCondition()) {
            throw new IllegalArgumentException("At least one search condition is required.");
        }

        AutoImportMetadata autoImportMetadata = ensureCoverage(condition, autoImport);
        HouseSearchPageResponse pageResponse = searchDb(condition);
        return new HouseSearchPageResponse(
                pageResponse.items(),
                pageResponse.page(),
                pageResponse.size(),
                pageResponse.totalCount(),
                pageResponse.minDealAmountManwon(),
                pageResponse.maxDealAmountManwon(),
                autoImportMetadata.attempted,
                autoImportMetadata.importedRanges,
                autoImportMetadata.skippedRanges
        );
    }

    public HouseDealPriceRangeResponse findHouseDealPriceRange(
            String lawdCd,
            String sido,
            String sigungu,
            String umdNm,
            String aptName,
            String dealYmd,
            String startDealYmd,
            String endDealYmd,
            Boolean autoImport
    ) {
        String normalizedDealYmd = trimToNull(dealYmd);
        String normalizedStartDealYmd = trimToNull(startDealYmd);
        String normalizedEndDealYmd = trimToNull(endDealYmd);
        if (normalizedDealYmd != null) {
            normalizedStartDealYmd = null;
            normalizedEndDealYmd = null;
        } else if (normalizedStartDealYmd != null && normalizedEndDealYmd != null
                && normalizedStartDealYmd.compareTo(normalizedEndDealYmd) > 0) {
            throw new IllegalArgumentException("startDealYmd must be less than or equal to endDealYmd.");
        }

        HouseSearchCondition condition = new HouseSearchCondition(
                trimToNull(lawdCd),
                trimToNull(sido),
                trimToNull(sigungu),
                trimToNull(umdNm),
                trimToNull(aptName),
                normalizedDealYmd,
                normalizedStartDealYmd,
                normalizedEndDealYmd,
                DEFAULT_SORT,
                null,
                null,
                DEFAULT_PAGE,
                DEFAULT_SIZE,
                0
        );

        if (!condition.hasSearchCondition()) {
            throw new IllegalArgumentException("At least one search condition is required.");
        }

        ensureCoverage(condition, autoImport);
        HouseDealPriceRangeResponse priceRange = houseMapper.selectHouseDealPriceRange(condition);
        return priceRange == null ? new HouseDealPriceRangeResponse(null, null) : priceRange;
    }

    public List<String> resolveAutoImportLawdCds(String lawdCd, String sido, String sigungu) {
        return seoulLawdCodeResolver.resolveLawdCds(lawdCd, sido, sigungu);
    }

    public boolean isCompleteCoverage(ImportBatchResponse batch) {
        int totalCount = batch.totalCount() == null ? 0 : batch.totalCount();
        int importedCount = batch.importedCount() == null ? 0 : batch.importedCount();
        int skippedCount = batch.skippedCount() == null ? 0 : batch.skippedCount();
        return "success".equals(batch.status()) && totalCount <= importedCount + skippedCount;
    }

    private HouseSearchPageResponse searchDb(HouseSearchCondition condition) {
        long totalCount = houseMapper.countHouseDeals(condition);
        List<HouseSearchResultResponse> items = totalCount == 0
                ? List.of()
                : houseMapper.searchHouseDeals(condition);
        HouseDealPriceRangeResponse priceRange = houseMapper.selectHouseDealPriceRange(condition);
        Integer minPrice = priceRange == null ? null : priceRange.minDealAmountManwon();
        Integer maxPrice = priceRange == null ? null : priceRange.maxDealAmountManwon();
        return new HouseSearchPageResponse(items, condition.page(), condition.size(), totalCount, minPrice, maxPrice,
                false, List.of(), List.of());
    }

    private AutoImportMetadata ensureCoverage(HouseSearchCondition condition, Boolean autoImport) {
        if (!Boolean.TRUE.equals(autoImport) || publicDataImportService == null || !canAutoImport(condition)) {
            return AutoImportMetadata.notAttempted();
        }

        List<String> lawdCds = seoulLawdCodeResolver.resolveLawdCds(
                condition.lawdCd(), condition.sido(), condition.sigungu()
        );
        if (lawdCds.isEmpty()) {
            return AutoImportMetadata.notAttempted();
        }

        List<AutoImportRangeResponse> importedRanges = new ArrayList<>();
        List<AutoImportRangeResponse> skippedRanges = new ArrayList<>();
        for (String lawdCd : lawdCds) {
            Optional<ImportBatchResponse> batch = findImportBatch(SOURCE_API, lawdCd, condition.dealYmd(), HOUSE_TYPE, DEAL_TYPE);
            if (batch.isPresent() && isCompleteCoverage(batch.get())) {
                skippedRanges.add(new AutoImportRangeResponse(lawdCd, condition.dealYmd(), "success", "complete coverage exists"));
                continue;
            }

            try {
                PublicDataImportResult result = publicDataImportService.importAptTrades(lawdCd, condition.dealYmd());
                importedRanges.add(new AutoImportRangeResponse(lawdCd, condition.dealYmd(), result.status(), result.message()));
            } catch (RuntimeException exception) {
                throw new AutoImportException("Auto import failed for lawdCd=" + lawdCd
                        + ", dealYmd=" + condition.dealYmd(), exception);
            }
        }
        return new AutoImportMetadata(!importedRanges.isEmpty() || !skippedRanges.isEmpty(), importedRanges, skippedRanges);
    }

    private static boolean canAutoImport(HouseSearchCondition condition) {
        if (!hasText(condition.dealYmd())) {
            return false;
        }
        if (hasText(condition.aptName())
                && !hasText(condition.lawdCd())
                && !hasText(condition.sido())
                && !hasText(condition.sigungu())
                && !hasText(condition.umdNm())) {
            return false;
        }
        return hasText(condition.lawdCd()) || hasText(condition.sido());
    }

    public Optional<ImportBatchResponse> findImportBatch(
            String sourceApi,
            String lawdCd,
            String dealYmd,
            String houseType,
            String dealType
    ) {
        return houseMapper.selectImportBatch(sourceApi, lawdCd, dealYmd, houseType, dealType);
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static String normalizeSort(String sort) {
        String normalized = trimToNull(sort);
        if (normalized == null) {
            return DEFAULT_SORT;
        }
        return switch (normalized) {
            case "latest", "oldest", "priceDesc", "priceAsc" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported sort option: " + sort);
        };
    }

    private static Integer normalizePrice(Integer price, String fieldName) {
        if (price == null) {
            return null;
        }
        if (price < 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 0.");
        }
        return price;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static RegionResponse repairRegion(RegionResponse region) {
        return new RegionResponse(
                region.regionId(),
                region.lawdCd(),
                region.legalDongCode(),
                MojibakeRepairer.repair(region.sido()),
                MojibakeRepairer.repair(region.sigungu()),
                MojibakeRepairer.repair(region.umdNm()),
                region.lat(),
                region.lng()
        );
    }

    private record AutoImportMetadata(
            boolean attempted,
            List<AutoImportRangeResponse> importedRanges,
            List<AutoImportRangeResponse> skippedRanges
    ) {
        private static AutoImportMetadata notAttempted() {
            return new AutoImportMetadata(false, List.of(), List.of());
        }
    }
}
