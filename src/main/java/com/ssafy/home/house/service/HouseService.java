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
import com.ssafy.home.publicdata.service.PublicDataApiException;
import com.ssafy.home.publicdata.service.AptRentImportCommandFactory;
import com.ssafy.home.publicdata.service.PublicDataLiveSearchService;
import com.ssafy.home.publicdata.service.PublicDataAptRentImportService;
import com.ssafy.home.publicdata.service.PublicDataImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.text.Collator;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    private static final Logger log = LoggerFactory.getLogger(HouseService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "latest";
    private static final String DEFAULT_DEAL_MODE = "sale";
    private static final DateTimeFormatter DEAL_YMD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Collator KOREAN_COLLATOR = Collator.getInstance(Locale.KOREAN);

    private final HouseMapper houseMapper;
    private final PublicDataImportService publicDataImportService;
    private final PublicDataAptRentImportService publicDataAptRentImportService;
    private final PublicDataLiveSearchService publicDataLiveSearchService;
    private final SeoulLawdCodeResolver seoulLawdCodeResolver;

    @Autowired
    public HouseService(
            HouseMapper houseMapper,
            PublicDataImportService publicDataImportService,
            PublicDataAptRentImportService publicDataAptRentImportService,
            PublicDataLiveSearchService publicDataLiveSearchService,
            SeoulLawdCodeResolver seoulLawdCodeResolver
    ) {
        this.houseMapper = houseMapper;
        this.publicDataImportService = publicDataImportService;
        this.publicDataAptRentImportService = publicDataAptRentImportService;
        this.publicDataLiveSearchService = publicDataLiveSearchService;
        this.seoulLawdCodeResolver = seoulLawdCodeResolver;
    }

    HouseService(HouseMapper houseMapper) {
        this(houseMapper, null, null, null, new SeoulLawdCodeResolver());
    }

    HouseService(
            HouseMapper houseMapper,
            PublicDataImportService publicDataImportService,
            SeoulLawdCodeResolver seoulLawdCodeResolver
    ) {
        this(houseMapper, publicDataImportService, null, null, seoulLawdCodeResolver);
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
                DEFAULT_SORT, null, null, null, null, null, null, DEFAULT_DEAL_MODE);
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
        return searchHouseDeals(lawdCd, sido, sigungu, umdNm, aptName, dealYmd, startDealYmd, endDealYmd, page, size,
                autoImport, sort, minPrice, maxPrice, null, null, null, null, DEFAULT_DEAL_MODE);
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
            Integer maxPrice,
            Integer minDeposit,
            Integer maxDeposit,
            Integer minMonthlyRent,
            Integer maxMonthlyRent,
            String dealMode
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedDealMode = normalizeDealMode(dealMode);
        String normalizedSort = normalizeSort(sort, normalizedDealMode);
        Integer normalizedMinPrice = normalizePrice(minPrice, "minPrice");
        Integer normalizedMaxPrice = normalizePrice(maxPrice, "maxPrice");
        Integer normalizedMinDeposit = normalizePrice(minDeposit, "minDeposit");
        Integer normalizedMaxDeposit = normalizePrice(maxDeposit, "maxDeposit");
        Integer normalizedMinMonthlyRent = normalizePrice(minMonthlyRent, "minMonthlyRent");
        Integer normalizedMaxMonthlyRent = normalizePrice(maxMonthlyRent, "maxMonthlyRent");
        if (normalizedMinPrice != null && normalizedMaxPrice != null && normalizedMinPrice > normalizedMaxPrice) {
            throw new IllegalArgumentException("minPrice must be less than or equal to maxPrice.");
        }
        if (normalizedMinDeposit != null && normalizedMaxDeposit != null && normalizedMinDeposit > normalizedMaxDeposit) {
            throw new IllegalArgumentException("minDeposit must be less than or equal to maxDeposit.");
        }
        if (normalizedMinMonthlyRent != null && normalizedMaxMonthlyRent != null
                && normalizedMinMonthlyRent > normalizedMaxMonthlyRent) {
            throw new IllegalArgumentException("minMonthlyRent must be less than or equal to maxMonthlyRent.");
        }
        validatePriceFilters(normalizedDealMode, normalizedMinPrice, normalizedMaxPrice, normalizedMinDeposit,
                normalizedMaxDeposit, normalizedMinMonthlyRent, normalizedMaxMonthlyRent);
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
                normalizedDealMode,
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
                normalizedMinDeposit,
                normalizedMaxDeposit,
                normalizedMinMonthlyRent,
                normalizedMaxMonthlyRent,
                normalizedPage,
                normalizedSize,
                (normalizedPage - 1) * normalizedSize
        );

        if (!condition.hasSearchCondition()) {
            throw new IllegalArgumentException("At least one search condition is required.");
        }

        Optional<LiveCoverageRequest> liveCoverage = liveCoverageRequest(condition, autoImport);
        if (liveCoverage.isPresent()) {
            LiveCoverageRequest request = liveCoverage.get();
            return publicDataLiveSearchService.search(request.lawdCds(), request.dealYmds(), condition);
        }

        AutoImportMetadata autoImportMetadata = publicDataLiveSearchService == null
                ? ensureCoverage(condition, autoImport)
                : AutoImportMetadata.notAttempted();
        HouseSearchPageResponse pageResponse = searchDb(condition);
        return new HouseSearchPageResponse(
                pageResponse.items(),
                pageResponse.page(),
                pageResponse.size(),
                pageResponse.totalCount(),
                pageResponse.minDealAmountManwon(),
                pageResponse.maxDealAmountManwon(),
                pageResponse.minDepositManwon(),
                pageResponse.maxDepositManwon(),
                pageResponse.minMonthlyRentManwon(),
                pageResponse.maxMonthlyRentManwon(),
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
            Boolean autoImport,
            String dealMode
    ) {
        String normalizedDealMode = normalizeDealMode(dealMode);
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
                normalizedDealMode,
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
                null,
                null,
                null,
                null,
                DEFAULT_PAGE,
                DEFAULT_SIZE,
                0
        );

        if (!condition.hasSearchCondition()) {
            throw new IllegalArgumentException("At least one search condition is required.");
        }

        Optional<LiveCoverageRequest> liveCoverage = liveCoverageRequest(condition, autoImport);
        if (liveCoverage.isPresent()) {
            LiveCoverageRequest request = liveCoverage.get();
            return publicDataLiveSearchService.priceRange(request.lawdCds(), request.dealYmds(), condition);
        }

        if (publicDataLiveSearchService == null) {
            ensureCoverage(condition, autoImport);
        }

        HouseDealPriceRangeResponse priceRange = houseMapper.selectHouseDealPriceRange(condition);
        return priceRange == null ? new HouseDealPriceRangeResponse(null, null, null, null, null, null) : priceRange;
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
        return findHouseDealPriceRange(lawdCd, sido, sigungu, umdNm, aptName, dealYmd, startDealYmd, endDealYmd,
                autoImport, DEFAULT_DEAL_MODE);
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
        Integer minDeposit = priceRange == null ? null : priceRange.minDepositManwon();
        Integer maxDeposit = priceRange == null ? null : priceRange.maxDepositManwon();
        Integer minMonthlyRent = priceRange == null ? null : priceRange.minMonthlyRentManwon();
        Integer maxMonthlyRent = priceRange == null ? null : priceRange.maxMonthlyRentManwon();
        return new HouseSearchPageResponse(items, condition.page(), condition.size(), totalCount, minPrice, maxPrice,
                minDeposit, maxDeposit, minMonthlyRent, maxMonthlyRent, false, List.of(), List.of());
    }

    private AutoImportMetadata ensureCoverage(HouseSearchCondition condition, Boolean autoImport) {
        if (!Boolean.TRUE.equals(autoImport) || !canAutoImport(condition)) {
            return AutoImportMetadata.notAttempted();
        }

        List<String> lawdCds = seoulLawdCodeResolver.resolveLawdCds(
                condition.lawdCd(), condition.sido(), condition.sigungu()
        );
        if (lawdCds.isEmpty()) {
            return AutoImportMetadata.notAttempted();
        }

        List<String> dealYmds = autoImportDealYmds(condition);
        if (dealYmds.isEmpty()) {
            return AutoImportMetadata.notAttempted();
        }

        List<AutoImportRangeResponse> importedRanges = new ArrayList<>();
        List<AutoImportRangeResponse> skippedRanges = new ArrayList<>();
        for (String lawdCd : lawdCds) {
            for (String dealYmd : dealYmds) {
                if (shouldImportSale(condition.dealMode())) {
                    importCoverage(lawdCd, dealYmd, SOURCE_API, HOUSE_TYPE, DEAL_TYPE,
                            () -> publicDataImportService.importAptTrades(lawdCd, dealYmd),
                            importedRanges, skippedRanges);
                }
                if (shouldImportRent(condition.dealMode())) {
                    importCoverage(lawdCd, dealYmd,
                            AptRentImportCommandFactory.SOURCE_API,
                            AptRentImportCommandFactory.HOUSE_TYPE,
                            AptRentImportCommandFactory.DEAL_TYPE,
                            () -> publicDataAptRentImportService.importAptRents(lawdCd, dealYmd),
                            importedRanges, skippedRanges);
                }
            }
        }
        return new AutoImportMetadata(!importedRanges.isEmpty() || !skippedRanges.isEmpty(), importedRanges, skippedRanges);
    }

    private Optional<LiveCoverageRequest> liveCoverageRequest(HouseSearchCondition condition, Boolean autoImport) {
        if (!Boolean.TRUE.equals(autoImport) || publicDataLiveSearchService == null || !canAutoImport(condition)) {
            return Optional.empty();
        }

        List<String> lawdCds = seoulLawdCodeResolver.resolveLawdCds(
                condition.lawdCd(), condition.sido(), condition.sigungu()
        );
        List<String> dealYmds = autoImportDealYmds(condition);
        if (lawdCds.isEmpty() || dealYmds.isEmpty()) {
            return Optional.empty();
        }

        for (String lawdCd : lawdCds) {
            for (String dealYmd : dealYmds) {
                if (shouldImportSale(condition.dealMode())
                        && !hasCompleteCoverage(lawdCd, dealYmd, SOURCE_API, HOUSE_TYPE, DEAL_TYPE)) {
                    return Optional.of(new LiveCoverageRequest(lawdCds, dealYmds));
                }
                if (shouldImportRent(condition.dealMode())
                        && !hasCompleteCoverage(lawdCd, dealYmd,
                        AptRentImportCommandFactory.SOURCE_API,
                        AptRentImportCommandFactory.HOUSE_TYPE,
                        AptRentImportCommandFactory.DEAL_TYPE)) {
                    return Optional.of(new LiveCoverageRequest(lawdCds, dealYmds));
                }
            }
        }

        return Optional.empty();
    }

    private boolean hasCompleteCoverage(
            String lawdCd,
            String dealYmd,
            String sourceApi,
            String houseType,
            String dealType
    ) {
        return findImportBatch(sourceApi, lawdCd, dealYmd, houseType, dealType)
                .map(this::isCompleteCoverage)
                .orElse(false);
    }

    private static List<String> autoImportDealYmds(HouseSearchCondition condition) {
        if (hasText(condition.dealYmd())) {
            return List.of(condition.dealYmd());
        }
        if (!hasText(condition.startDealYmd()) || !hasText(condition.endDealYmd())) {
            return List.of();
        }

        YearMonth start = YearMonth.parse(condition.startDealYmd(), DEAL_YMD_FORMATTER);
        YearMonth end = YearMonth.parse(condition.endDealYmd(), DEAL_YMD_FORMATTER);
        List<String> dealYmds = new ArrayList<>();
        for (YearMonth current = start; !current.isAfter(end); current = current.plusMonths(1)) {
            dealYmds.add(current.format(DEAL_YMD_FORMATTER));
        }
        return dealYmds;
    }

    private void importCoverage(
            String lawdCd,
            String dealYmd,
            String sourceApi,
            String houseType,
            String dealType,
            ImportAction importAction,
            List<AutoImportRangeResponse> importedRanges,
            List<AutoImportRangeResponse> skippedRanges
    ) {
        Optional<ImportBatchResponse> batch = findImportBatch(sourceApi, lawdCd, dealYmd, houseType, dealType);
        if (batch.isPresent() && isCompleteCoverage(batch.get())) {
            skippedRanges.add(new AutoImportRangeResponse(lawdCd, dealYmd, "success",
                    sourceApi + " complete coverage exists"));
            return;
        }

        try {
            PublicDataImportResult result = importAction.importData();
            importedRanges.add(new AutoImportRangeResponse(lawdCd, dealYmd, result.status(), result.message()));
        } catch (RuntimeException exception) {
            AutoImportException.Reason reason = classifyAutoImportFailure(exception);
            log.warn("Auto import failed: reason={}, sourceApi={}, lawdCd={}, dealYmd={}{}",
                    reason, sourceApi, lawdCd, dealYmd, autoImportFailureDetail(exception));
            throw new AutoImportException(reason,
                    "Auto import failed for lawdCd=" + lawdCd + ", dealYmd=" + dealYmd,
                    exception);
        }
    }

    private boolean canAutoImport(HouseSearchCondition condition) {
        if (!hasText(condition.dealYmd())
                && (!hasText(condition.startDealYmd()) || !hasText(condition.endDealYmd()))) {
            return false;
        }
        if (shouldImportSale(condition.dealMode()) && publicDataImportService == null) {
            return false;
        }
        if (shouldImportRent(condition.dealMode()) && publicDataAptRentImportService == null) {
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

    private static boolean shouldImportSale(String dealMode) {
        return "sale".equals(dealMode) || "all".equals(dealMode);
    }

    private static boolean shouldImportRent(String dealMode) {
        return "jeonse".equals(dealMode) || "monthly".equals(dealMode)
                || "rent".equals(dealMode) || "all".equals(dealMode);
    }

    // Exposes safe diagnostics only; exception messages may include request URLs with service keys.
    private static String autoImportFailureDetail(RuntimeException exception) {
        if (exception instanceof PublicDataApiException publicDataApiException) {
            return ", resultCode=" + publicDataApiException.resultCode()
                    + ", resultMsg=" + publicDataApiException.resultMsg();
        }
        return ", cause=" + exception.getClass().getSimpleName();
    }

    private static AutoImportException.Reason classifyAutoImportFailure(RuntimeException exception) {
        if (exception instanceof PublicDataApiException publicDataApiException) {
            return switch (publicDataApiException.reason()) {
                case KEY_INVALID -> AutoImportException.Reason.KEY_INVALID;
                case QUOTA -> AutoImportException.Reason.QUOTA;
                case PROVIDER_ERROR -> AutoImportException.Reason.PROVIDER_ERROR;
            };
        }
        if (exception instanceof IllegalStateException) {
            return AutoImportException.Reason.KEY_MISSING;
        }
        if (exception instanceof ResourceAccessException || hasCause(exception, SocketTimeoutException.class)) {
            return AutoImportException.Reason.TIMEOUT;
        }
        return AutoImportException.Reason.PROVIDER_ERROR;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    private static String normalizeDealMode(String dealMode) {
        String normalized = trimToNull(dealMode);
        if (normalized == null) {
            return DEFAULT_DEAL_MODE;
        }
        return switch (normalized) {
            case "sale", "jeonse", "monthly", "rent", "all" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported dealMode option: " + dealMode);
        };
    }

    private static String normalizeSort(String sort, String dealMode) {
        String normalized = trimToNull(sort);
        if (normalized == null) {
            return DEFAULT_SORT;
        }
        boolean supported = switch (dealMode) {
            case "sale" -> switch (normalized) {
                case "latest", "oldest", "priceDesc", "priceAsc", "areaDesc", "areaAsc" -> true;
                default -> false;
            };
            case "jeonse" -> switch (normalized) {
                case "latest", "oldest", "depositDesc", "depositAsc", "areaDesc", "areaAsc" -> true;
                default -> false;
            };
            case "monthly" -> switch (normalized) {
                case "latest", "oldest", "depositDesc", "depositAsc", "monthlyRentDesc", "monthlyRentAsc", "areaDesc", "areaAsc" -> true;
                default -> false;
            };
            case "rent", "all" -> switch (normalized) {
                case "latest", "oldest", "areaDesc", "areaAsc" -> true;
                default -> false;
            };
            default -> false;
        };
        if (!supported) {
            throw new IllegalArgumentException("Unsupported sort option for dealMode=" + dealMode + ": " + sort);
        }
        return normalized;
    }

    private static void validatePriceFilters(
            String dealMode,
            Integer minPrice,
            Integer maxPrice,
            Integer minDeposit,
            Integer maxDeposit,
            Integer minMonthlyRent,
            Integer maxMonthlyRent
    ) {
        boolean hasSalePrice = minPrice != null || maxPrice != null;
        boolean hasDeposit = minDeposit != null || maxDeposit != null;
        boolean hasMonthlyRent = minMonthlyRent != null || maxMonthlyRent != null;
        switch (dealMode) {
            case "sale" -> {
                if (hasDeposit || hasMonthlyRent) {
                    throw new IllegalArgumentException("Rent price filters are not supported for dealMode=sale.");
                }
            }
            case "jeonse" -> {
                if (hasSalePrice || hasMonthlyRent) {
                    throw new IllegalArgumentException("Only deposit filters are supported for dealMode=jeonse.");
                }
            }
            case "monthly" -> {
                if (hasSalePrice) {
                    throw new IllegalArgumentException("Sale price filters are not supported for dealMode=monthly.");
                }
            }
            case "rent", "all" -> {
                if (hasSalePrice || hasDeposit || hasMonthlyRent) {
                    throw new IllegalArgumentException("Price filters are not supported for dealMode=" + dealMode + ".");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported dealMode option: " + dealMode);
        }
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

    private record LiveCoverageRequest(List<String> lawdCds, List<String> dealYmds) {
    }

    @FunctionalInterface
    private interface ImportAction {
        PublicDataImportResult importData();
    }
}
