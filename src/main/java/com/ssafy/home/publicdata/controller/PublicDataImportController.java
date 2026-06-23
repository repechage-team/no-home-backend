package com.ssafy.home.publicdata.controller;

import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.publicdata.dto.PublicDataImportResult;
import com.ssafy.home.publicdata.service.PublicDataAptRentImportService;
import com.ssafy.home.publicdata.service.PublicDataImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-data")
public class PublicDataImportController {

    private final PublicDataImportService importService;
    private final PublicDataAptRentImportService aptRentImportService;

    public PublicDataImportController(
            PublicDataImportService importService,
            PublicDataAptRentImportService aptRentImportService
    ) {
        this.importService = importService;
        this.aptRentImportService = aptRentImportService;
    }

    @PostMapping("/apt-trades/import")
    public ApiResponse<PublicDataImportResult> importAptTrades(
            @RequestParam String lawdCd,
            @RequestParam String dealYmd,
            @RequestParam(required = false, defaultValue = "sale") String dealMode
    ) {
        try {
            return ApiResponse.ok(switch (dealMode) {
                case "sale" -> importService.importAptTrades(lawdCd, dealYmd);
                case "jeonse", "monthly", "rent" -> aptRentImportService.importAptRents(lawdCd, dealYmd);
                case "all" -> {
                    importService.importAptTrades(lawdCd, dealYmd);
                    yield aptRentImportService.importAptRents(lawdCd, dealYmd);
                }
                default -> throw new IllegalArgumentException("Unsupported dealMode option: " + dealMode);
            });
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage(), null);
        }
    }
}
