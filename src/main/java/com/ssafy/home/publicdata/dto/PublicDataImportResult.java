package com.ssafy.home.publicdata.dto;

public record PublicDataImportResult(
        String sourceApi,
        String lawdCd,
        String dealYmd,
        String status,
        int totalCount,
        int importedCount,
        int skippedCount,
        boolean alreadyImported,
        String message
) {
}
