package com.ssafy.home.publicdata.service;

import com.ssafy.home.house.util.ApiRowHashGenerator;
import com.ssafy.home.house.util.ApiRowHashInput;
import com.ssafy.home.publicdata.dto.AptRentApiItem;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import com.ssafy.home.publicdata.mapper.HouseUpsertCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AptRentImportCommandFactory {

    public static final String SOURCE_API = "RTMSDataSvcAptRent";
    public static final String HOUSE_TYPE = "apartment";
    public static final String DEAL_TYPE = "rent";
    public static final String RENT_TYPE_JEONSE = "jeonse";
    public static final String RENT_TYPE_MONTHLY = "monthly";

    public HouseUpsertCommand toHouseCommand(String lawdCd, Long regionId, AptRentApiItem item) {
        return new HouseUpsertCommand(
                regionId,
                defaultIfBlank(trim(item.sggCd()), lawdCd),
                trim(item.umdNm()),
                trim(item.jibun()),
                trim(item.aptNm()),
                parseInteger(item.buildYear())
        );
    }

    public HouseDealInsertCommand toDealCommand(String lawdCd, String dealYmd, Long houseId, AptRentApiItem item) {
        Integer dealYear = parseInteger(item.dealYear());
        Integer dealMonth = parseInteger(item.dealMonth());
        Integer dealDay = parseInteger(item.dealDay());
        String deposit = trim(item.deposit());
        String monthlyRent = trim(item.monthlyRent());
        Integer monthlyRentManwon = parseAmountManwon(monthlyRent);
        String rentType = monthlyRentManwon != null && monthlyRentManwon > 0
                ? RENT_TYPE_MONTHLY
                : RENT_TYPE_JEONSE;
        String apiRowHash = ApiRowHashGenerator.generate(new ApiRowHashInput(
                SOURCE_API,
                lawdCd,
                dealYmd,
                item.umdNm(),
                item.jibun(),
                item.aptNm(),
                item.dealYear(),
                item.dealMonth(),
                item.dealDay(),
                item.deposit(),
                item.monthlyRent(),
                item.excluUseAr(),
                item.floor()
        ));
        return new HouseDealInsertCommand(
                houseId,
                SOURCE_API,
                lawdCd,
                dealYmd,
                rentType,
                dealYear,
                dealMonth,
                dealDay,
                LocalDate.of(dealYear, dealMonth, dealDay),
                deposit,
                parseAmountManwon(deposit),
                rentType,
                deposit,
                parseAmountManwon(deposit),
                monthlyRent,
                monthlyRentManwon,
                parseBigDecimal(item.excluUseAr()),
                parseInteger(item.floor()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                trim(item.contractTerm()),
                trim(item.contractType()),
                trim(item.useRRRight()),
                trim(item.preDeposit()),
                parseAmountManwon(item.preDeposit()),
                trim(item.preMonthlyRent()),
                parseAmountManwon(item.preMonthlyRent()),
                trim(item.roadnm()),
                trim(item.aptSeq()),
                apiRowHash,
                toRawJson(item)
        );
    }

    private static String toRawJson(AptRentApiItem item) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("sggCd", item.sggCd());
        values.put("umdNm", item.umdNm());
        values.put("jibun", item.jibun());
        values.put("aptNm", item.aptNm());
        values.put("aptSeq", item.aptSeq());
        values.put("buildYear", item.buildYear());
        values.put("dealYear", item.dealYear());
        values.put("dealMonth", item.dealMonth());
        values.put("dealDay", item.dealDay());
        values.put("deposit", item.deposit());
        values.put("monthlyRent", item.monthlyRent());
        values.put("excluUseAr", item.excluUseAr());
        values.put("floor", item.floor());
        values.put("contractTerm", item.contractTerm());
        values.put("contractType", item.contractType());
        values.put("useRRRight", item.useRRRight());
        values.put("preDeposit", item.preDeposit());
        values.put("preMonthlyRent", item.preMonthlyRent());
        values.put("roadnm", item.roadnm());
        values.put("aptSeq", item.aptSeq());
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(entry.getKey()).append("\":");
            if (entry.getValue() == null) {
                builder.append("null");
            } else {
                builder.append('"').append(escapeJson(entry.getValue())).append('"');
            }
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Integer parseAmountManwon(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        return Integer.parseInt(normalized.replace(",", ""));
    }

    private static Integer parseInteger(String value) {
        String normalized = trim(value);
        return normalized == null ? null : Integer.parseInt(normalized);
    }

    private static BigDecimal parseBigDecimal(String value) {
        String normalized = trim(value);
        return normalized == null ? null : new BigDecimal(normalized);
    }

    private static String trim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
