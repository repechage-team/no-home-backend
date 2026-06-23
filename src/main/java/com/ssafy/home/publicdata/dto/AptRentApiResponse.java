package com.ssafy.home.publicdata.dto;

import java.util.List;

public record AptRentApiResponse(
        String resultCode,
        String resultMsg,
        int totalCount,
        List<AptRentApiItem> items
) {
    public boolean isSuccess() {
        return resultCode == null || "00".equals(resultCode) || "000".equals(resultCode) || "03".equals(resultCode);
    }
}
