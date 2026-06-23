package com.ssafy.home.house.dto;

public record HouseDealPriceRangeResponse(
        Integer minDealAmountManwon,
        Integer maxDealAmountManwon,
        Integer minDepositManwon,
        Integer maxDepositManwon,
        Integer minMonthlyRentManwon,
        Integer maxMonthlyRentManwon
) {
    public HouseDealPriceRangeResponse(Integer minDealAmountManwon, Integer maxDealAmountManwon) {
        this(minDealAmountManwon, maxDealAmountManwon, null, null, null, null);
    }
}
