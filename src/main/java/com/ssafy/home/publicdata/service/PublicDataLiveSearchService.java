package com.ssafy.home.publicdata.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.house.dto.AutoImportRangeResponse;
import com.ssafy.home.house.dto.HouseDealPriceRangeResponse;
import com.ssafy.home.house.dto.HouseSearchCondition;
import com.ssafy.home.house.dto.HouseSearchPageResponse;
import com.ssafy.home.house.dto.HouseSearchResultResponse;
import com.ssafy.home.publicdata.client.PublicDataAptRentClient;
import com.ssafy.home.publicdata.client.PublicDataAptRentXmlParser;
import com.ssafy.home.publicdata.client.PublicDataAptTradeClient;
import com.ssafy.home.publicdata.client.PublicDataAptTradeXmlParser;
import com.ssafy.home.publicdata.dto.AptRentApiItem;
import com.ssafy.home.publicdata.dto.AptRentApiResponse;
import com.ssafy.home.publicdata.dto.AptTradeApiItem;
import com.ssafy.home.publicdata.dto.AptTradeApiResponse;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import com.ssafy.home.publicdata.mapper.HouseUpsertCommand;
import com.ssafy.home.publicdata.service.PublicDataBatchPersistService.PersistRequest;
import com.ssafy.home.publicdata.service.PublicDataBatchPersistService.PersistRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.ssafy.home.publicdata.service.AptRentImportCommandFactory.RENT_TYPE_JEONSE;
import static com.ssafy.home.publicdata.service.AptRentImportCommandFactory.RENT_TYPE_MONTHLY;

@Service
public class PublicDataLiveSearchService {

    private static final int PREFERRED_PAGE_SIZE = 1000;
    private final PublicDataAptTradeClient tradeClient;
    private final PublicDataAptTradeXmlParser tradeParser;
    private final PublicDataAptRentClient rentClient;
    private final PublicDataAptRentXmlParser rentParser;
    private final AptTradeImportCommandFactory tradeCommandFactory;
    private final AptRentImportCommandFactory rentCommandFactory;
    private final SeoulLawdCodeResolver seoulLawdCodeResolver;
    private final PublicDataBatchPersistService persistService;

    public PublicDataLiveSearchService(
            PublicDataAptTradeClient tradeClient,
            PublicDataAptTradeXmlParser tradeParser,
            PublicDataAptRentClient rentClient,
            PublicDataAptRentXmlParser rentParser,
            AptTradeImportCommandFactory tradeCommandFactory,
            AptRentImportCommandFactory rentCommandFactory,
            SeoulLawdCodeResolver seoulLawdCodeResolver,
            PublicDataBatchPersistService persistService
    ) {
        this.tradeClient = tradeClient;
        this.tradeParser = tradeParser;
        this.rentClient = rentClient;
        this.rentParser = rentParser;
        this.tradeCommandFactory = tradeCommandFactory;
        this.rentCommandFactory = rentCommandFactory;
        this.seoulLawdCodeResolver = seoulLawdCodeResolver;
        this.persistService = persistService;
    }

    public HouseSearchPageResponse search(List<String> lawdCds, List<String> dealYmds, HouseSearchCondition condition) {
        List<LiveRow> rows = fetchRows(lawdCds, dealYmds, condition.dealMode());
        List<HouseSearchResultResponse> baseFiltered = rows.stream()
                .map(LiveRow::response)
                .filter(item -> matchesBaseFilters(item, condition))
                .toList();
        HouseDealPriceRangeResponse priceRange = priceRange(baseFiltered);
        List<HouseSearchResultResponse> filtered = baseFiltered.stream()
                .filter(item -> matchesPriceFilters(item, condition))
                .sorted(comparator(condition.sort()))
                .toList();
        int from = Math.min(condition.offset(), filtered.size());
        int to = Math.min(from + condition.size(), filtered.size());
        return new HouseSearchPageResponse(filtered.subList(from, to), condition.page(), condition.size(), filtered.size(),
                priceRange.minDealAmountManwon(), priceRange.maxDealAmountManwon(),
                priceRange.minDepositManwon(), priceRange.maxDepositManwon(),
                priceRange.minMonthlyRentManwon(), priceRange.maxMonthlyRentManwon(),
                true,
                rows.stream().map(LiveRow::range).distinct().toList(),
                List.of());
    }

    public HouseDealPriceRangeResponse priceRange(List<String> lawdCds, List<String> dealYmds, HouseSearchCondition condition) {
        List<HouseSearchResultResponse> items = fetchRows(lawdCds, dealYmds, condition.dealMode()).stream()
                .map(LiveRow::response)
                .filter(item -> matchesBaseFilters(item, condition))
                .toList();
        return priceRange(items);
    }

    private List<LiveRow> fetchRows(List<String> lawdCds, List<String> dealYmds, String dealMode) {
        List<LiveRow> rows = new ArrayList<>();
        for (String lawdCd : lawdCds) {
            String sigungu = seoulLawdCodeResolver.sigunguName(lawdCd).orElse("");
            for (String dealYmd : dealYmds) {
                if (shouldFetchSale(dealMode)) {
                    rows.addAll(fetchSale(lawdCd, sigungu, dealYmd));
                }
                if (shouldFetchRent(dealMode)) {
                    rows.addAll(fetchRent(lawdCd, sigungu, dealYmd));
                }
            }
        }
        return rows;
    }

    private List<LiveRow> fetchSale(String lawdCd, String sigungu, String dealYmd) {
        AptTradeApiResponse response = fetchTradeAll(lawdCd, dealYmd);
        List<PersistRow> persistRows = new ArrayList<>();
        List<LiveRow> liveRows = new ArrayList<>();
        for (AptTradeApiItem item : response.items()) {
            HouseUpsertCommand houseCommand = tradeCommandFactory.toHouseCommand(lawdCd, null, item);
            HouseDealInsertCommand dealCommand = tradeCommandFactory.toDealCommand(lawdCd, dealYmd, null, item);
            HouseSearchResultResponse result = toResponse(lawdCd, dealYmd, sigungu, houseCommand, dealCommand);
            persistRows.add(new PersistRow(lawdCd, sigungu, item.umdNm(), houseCommand, dealCommand));
            liveRows.add(new LiveRow(result, new AutoImportRangeResponse(lawdCd, dealYmd, "live",
                    AptTradeImportCommandFactory.SOURCE_API + " live response")));
        }
        persistService.persistAsync(new PersistRequest(AptTradeImportCommandFactory.SOURCE_API, lawdCd, dealYmd,
                AptTradeImportCommandFactory.HOUSE_TYPE, AptTradeImportCommandFactory.DEAL_TYPE,
                response.totalCount(), persistRows));
        return liveRows;
    }

    private List<LiveRow> fetchRent(String lawdCd, String sigungu, String dealYmd) {
        AptRentApiResponse response = fetchRentAll(lawdCd, dealYmd);
        List<PersistRow> persistRows = new ArrayList<>();
        List<LiveRow> liveRows = new ArrayList<>();
        for (AptRentApiItem item : response.items()) {
            HouseUpsertCommand houseCommand = rentCommandFactory.toHouseCommand(lawdCd, null, item);
            HouseDealInsertCommand dealCommand = rentCommandFactory.toDealCommand(lawdCd, dealYmd, null, item);
            HouseSearchResultResponse result = toResponse(lawdCd, dealYmd, sigungu, houseCommand, dealCommand);
            persistRows.add(new PersistRow(lawdCd, sigungu, item.umdNm(), houseCommand, dealCommand));
            liveRows.add(new LiveRow(result, new AutoImportRangeResponse(lawdCd, dealYmd, "live",
                    AptRentImportCommandFactory.SOURCE_API + " live response")));
        }
        persistService.persistAsync(new PersistRequest(AptRentImportCommandFactory.SOURCE_API, lawdCd, dealYmd,
                AptRentImportCommandFactory.HOUSE_TYPE, AptRentImportCommandFactory.DEAL_TYPE,
                response.totalCount(), persistRows));
        return liveRows;
    }

    private AptTradeApiResponse fetchTradeAll(String lawdCd, String dealYmd) {
        AptTradeApiResponse first = tradeParser.parse(tradeClient.fetchXml(lawdCd, dealYmd, 1, PREFERRED_PAGE_SIZE));
        validateTrade(first);
        List<AptTradeApiItem> items = new ArrayList<>(first.items());
        for (int pageNo = 2; items.size() < first.totalCount(); pageNo++) {
            AptTradeApiResponse response = tradeParser.parse(tradeClient.fetchXml(lawdCd, dealYmd, pageNo, PREFERRED_PAGE_SIZE));
            validateTrade(response);
            items.addAll(response.items());
            if (response.items().isEmpty()) {
                break;
            }
        }
        return new AptTradeApiResponse(first.resultCode(), first.resultMsg(), first.totalCount(), items);
    }

    private AptRentApiResponse fetchRentAll(String lawdCd, String dealYmd) {
        AptRentApiResponse first = rentParser.parse(rentClient.fetchXml(lawdCd, dealYmd, 1, PREFERRED_PAGE_SIZE));
        validateRent(first);
        List<AptRentApiItem> items = new ArrayList<>(first.items());
        for (int pageNo = 2; items.size() < first.totalCount(); pageNo++) {
            AptRentApiResponse response = rentParser.parse(rentClient.fetchXml(lawdCd, dealYmd, pageNo, PREFERRED_PAGE_SIZE));
            validateRent(response);
            items.addAll(response.items());
            if (response.items().isEmpty()) {
                break;
            }
        }
        return new AptRentApiResponse(first.resultCode(), first.resultMsg(), first.totalCount(), items);
    }

    private HouseSearchResultResponse toResponse(
            String lawdCd,
            String dealYmd,
            String sigungu,
            HouseUpsertCommand houseCommand,
            HouseDealInsertCommand dealCommand
    ) {
        return new HouseSearchResultResponse(
                null,
                null,
                houseCommand.aptNm(),
                SeoulLawdCodeResolver.SEOUL_SIDO_NAME,
                sigungu,
                houseCommand.umdNm(),
                houseCommand.jibun(),
                houseCommand.buildYear(),
                lawdCd,
                dealYmd,
                dealCommand.dealType(),
                dealCommand.rentType(),
                dealCommand.dealDate(),
                dealCommand.dealAmount(),
                dealCommand.dealAmountManwon(),
                dealCommand.deposit(),
                dealCommand.depositManwon(),
                dealCommand.monthlyRent(),
                dealCommand.monthlyRentManwon(),
                dealCommand.excluUseAr(),
                dealCommand.floor(),
                dealCommand.contractTerm(),
                dealCommand.contractType(),
                dealCommand.useRRRight(),
                dealCommand.preDeposit(),
                dealCommand.preDepositManwon(),
                dealCommand.preMonthlyRent(),
                dealCommand.preMonthlyRentManwon(),
                dealCommand.roadnm(),
                dealCommand.aptSeq(),
                null,
                null,
                dealCommand.apiRowHash(),
                dealCommand.apiRowHash()
        );
    }

    private static boolean matchesBaseFilters(HouseSearchResultResponse item, HouseSearchCondition condition) {
        if (condition.umdNm() != null && !condition.umdNm().equals(item.umdNm())) {
            return false;
        }
        if (condition.aptName() != null && !item.aptNm().toLowerCase(Locale.KOREAN)
                .contains(condition.aptName().toLowerCase(Locale.KOREAN))) {
            return false;
        }
        return switch (condition.dealMode()) {
            case "sale" -> "sale".equals(item.dealType());
            case "jeonse" -> RENT_TYPE_JEONSE.equals(item.dealType());
            case "monthly" -> RENT_TYPE_MONTHLY.equals(item.dealType());
            case "rent" -> RENT_TYPE_JEONSE.equals(item.dealType()) || RENT_TYPE_MONTHLY.equals(item.dealType());
            case "all" -> true;
            default -> false;
        };
    }

    private static boolean matchesPriceFilters(HouseSearchResultResponse item, HouseSearchCondition condition) {
        return atLeast(item.dealAmountManwon(), condition.minPrice())
                && atMost(item.dealAmountManwon(), condition.maxPrice())
                && atLeast(item.depositManwon(), condition.minDeposit())
                && atMost(item.depositManwon(), condition.maxDeposit())
                && atLeast(item.monthlyRentManwon(), condition.minMonthlyRent())
                && atMost(item.monthlyRentManwon(), condition.maxMonthlyRent());
    }

    private static boolean atLeast(Integer value, Integer min) {
        return min == null || (value != null && value >= min);
    }

    private static boolean atMost(Integer value, Integer max) {
        return max == null || (value != null && value <= max);
    }

    private static Comparator<HouseSearchResultResponse> comparator(String sort) {
        Comparator<HouseSearchResultResponse> latest = Comparator
                .comparing(HouseSearchResultResponse::dealDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(item -> item.resultKey() == null ? "" : item.resultKey(), Comparator.reverseOrder());
        return switch (sort) {
            case "oldest" -> Comparator
                    .comparing(HouseSearchResultResponse::dealDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(item -> item.resultKey() == null ? "" : item.resultKey());
            case "priceDesc" -> byInteger(HouseSearchResultResponse::dealAmountManwon, false).thenComparing(latest);
            case "priceAsc" -> byInteger(HouseSearchResultResponse::dealAmountManwon, true).thenComparing(latest);
            case "depositDesc" -> byInteger(HouseSearchResultResponse::depositManwon, false).thenComparing(latest);
            case "depositAsc" -> byInteger(HouseSearchResultResponse::depositManwon, true).thenComparing(latest);
            case "monthlyRentDesc" -> byInteger(HouseSearchResultResponse::monthlyRentManwon, false).thenComparing(latest);
            case "monthlyRentAsc" -> byInteger(HouseSearchResultResponse::monthlyRentManwon, true).thenComparing(latest);
            case "areaDesc" -> byBigDecimal(HouseSearchResultResponse::excluUseAr, false).thenComparing(latest);
            case "areaAsc" -> byBigDecimal(HouseSearchResultResponse::excluUseAr, true).thenComparing(latest);
            default -> latest;
        };
    }

    private static Comparator<HouseSearchResultResponse> byInteger(
            java.util.function.Function<HouseSearchResultResponse, Integer> getter,
            boolean asc
    ) {
        Comparator<Integer> valueComparator = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(getter, Comparator.nullsLast(valueComparator));
    }

    private static Comparator<HouseSearchResultResponse> byBigDecimal(
            java.util.function.Function<HouseSearchResultResponse, BigDecimal> getter,
            boolean asc
    ) {
        Comparator<BigDecimal> valueComparator = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(getter, Comparator.nullsLast(valueComparator));
    }

    private static HouseDealPriceRangeResponse priceRange(List<HouseSearchResultResponse> items) {
        return new HouseDealPriceRangeResponse(
                min(items.stream().map(HouseSearchResultResponse::dealAmountManwon).toList()),
                max(items.stream().map(HouseSearchResultResponse::dealAmountManwon).toList()),
                min(items.stream().map(HouseSearchResultResponse::depositManwon).toList()),
                max(items.stream().map(HouseSearchResultResponse::depositManwon).toList()),
                min(items.stream().map(HouseSearchResultResponse::monthlyRentManwon).toList()),
                max(items.stream().map(HouseSearchResultResponse::monthlyRentManwon).toList())
        );
    }

    private static Integer min(List<Integer> values) {
        return values.stream().filter(value -> value != null).min(Integer::compareTo).orElse(null);
    }

    private static Integer max(List<Integer> values) {
        return values.stream().filter(value -> value != null).max(Integer::compareTo).orElse(null);
    }

    private static void validateTrade(AptTradeApiResponse response) {
        if (!response.isSuccess()) {
            throw new PublicDataApiException(PublicDataImportService.classifyApiFailure(response.resultCode(),
                    response.resultMsg()), response.resultCode(), response.resultMsg());
        }
    }

    private static void validateRent(AptRentApiResponse response) {
        if (!response.isSuccess()) {
            throw new PublicDataApiException(PublicDataImportService.classifyApiFailure(response.resultCode(),
                    response.resultMsg()), response.resultCode(), response.resultMsg());
        }
    }

    private static boolean shouldFetchSale(String dealMode) {
        return "sale".equals(dealMode) || "all".equals(dealMode);
    }

    private static boolean shouldFetchRent(String dealMode) {
        return "jeonse".equals(dealMode) || "monthly".equals(dealMode)
                || "rent".equals(dealMode) || "all".equals(dealMode);
    }

    private record LiveRow(HouseSearchResultResponse response, AutoImportRangeResponse range) {
    }
}
