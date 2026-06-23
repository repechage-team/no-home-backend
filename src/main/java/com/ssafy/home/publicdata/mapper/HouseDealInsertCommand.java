package com.ssafy.home.publicdata.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HouseDealInsertCommand(
        Long houseId,
        String sourceApi,
        String lawdCd,
        String dealYmd,
        String dealType,
        Integer dealYear,
        Integer dealMonth,
        Integer dealDay,
        LocalDate dealDate,
        String dealAmount,
        Integer dealAmountManwon,
        String rentType,
        String deposit,
        Integer depositManwon,
        String monthlyRent,
        Integer monthlyRentManwon,
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
        String contractTerm,
        String contractType,
        String useRRRight,
        String preDeposit,
        Integer preDepositManwon,
        String preMonthlyRent,
        Integer preMonthlyRentManwon,
        String roadnm,
        String aptSeq,
        String apiRowHash,
        String rawResponse
) {
}
