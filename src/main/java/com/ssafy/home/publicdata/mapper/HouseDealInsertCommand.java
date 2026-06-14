package com.ssafy.home.publicdata.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HouseDealInsertCommand(
        Long houseId,
        String sourceApi,
        String lawdCd,
        String dealYmd,
        Integer dealYear,
        Integer dealMonth,
        Integer dealDay,
        LocalDate dealDate,
        String dealAmount,
        Integer dealAmountManwon,
        BigDecimal excluUseAr,
        Integer floor,
        String aptDong,
        String buyerGbn,
        String slerGbn,
        String dealingGbn,
        String estateAgentSggNm,
        String cdealType,
        String cdealDay,
        String rgstDate,
        String landLeaseholdGbn,
        String apiRowHash,
        String rawResponse
) {
}
