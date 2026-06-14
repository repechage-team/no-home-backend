package com.ssafy.home.house.dto;

import java.time.LocalDateTime;

public record ImportBatchResponse(
        Long importBatchId,
        String sourceApi,
        String lawdCd,
        String dealYmd,
        String houseType,
        String dealType,
        String status,
        Integer totalCount,
        Integer importedCount,
        Integer skippedCount,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
}
