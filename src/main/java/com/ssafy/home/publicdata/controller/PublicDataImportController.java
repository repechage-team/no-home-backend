package com.ssafy.home.publicdata.controller;

import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.publicdata.dto.PublicDataImportResult;
import com.ssafy.home.publicdata.service.PublicDataImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-data")
public class PublicDataImportController {

    private final PublicDataImportService importService;

    public PublicDataImportController(PublicDataImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/apt-trades/import")
    public ApiResponse<PublicDataImportResult> importAptTrades(
            @RequestParam String lawdCd,
            @RequestParam String dealYmd
    ) {
        try {
            return ApiResponse.ok(importService.importAptTrades(lawdCd, dealYmd));
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage(), null);
        }
    }
}
