package com.ssafy.home.house.dto;

import java.util.List;

public record HouseSearchPageResponse(
        List<HouseSearchResultResponse> items,
        int page,
        int size,
        long totalCount,
        Integer minDealAmountManwon,
        Integer maxDealAmountManwon,
        boolean autoImportAttempted,
        List<AutoImportRangeResponse> importedRanges,
        List<AutoImportRangeResponse> skippedRanges
) {
    public HouseSearchPageResponse(
            List<HouseSearchResultResponse> items,
            int page,
            int size,
            long totalCount
    ) {
        this(items, page, size, totalCount, null, null, false, List.of(), List.of());
    }
}
