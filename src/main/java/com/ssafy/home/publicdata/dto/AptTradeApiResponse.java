package com.ssafy.home.publicdata.dto;

import java.util.List;

public record AptTradeApiResponse(String resultCode, String resultMsg, int totalCount, List<AptTradeApiItem> items) {

    public boolean isSuccess() {
        return resultCode == null || resultCode.isBlank() || "00".equals(resultCode);
    }
}
