package com.ssafy.home.house.controller;

import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.house.dto.HouseDealResponse;
import com.ssafy.home.house.dto.HouseResponse;
import com.ssafy.home.house.dto.HouseSearchPageResponse;
import com.ssafy.home.house.dto.RegionResponse;
import com.ssafy.home.house.service.AutoImportException;
import com.ssafy.home.house.service.HouseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class HouseController {

    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @GetMapping("/regions")
    public ApiResponse<List<RegionResponse>> regions(@RequestParam String lawdCd) {
        return ApiResponse.ok(houseService.findRegions(lawdCd));
    }

    @GetMapping("/houses")
    public ApiResponse<List<HouseResponse>> houses(@RequestParam String aptName) {
        return ApiResponse.ok(houseService.findHouses(aptName));
    }

    @GetMapping("/houses/search")
    public ResponseEntity<ApiResponse<HouseSearchPageResponse>> searchHouses(
            @RequestParam(required = false) String lawdCd,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String umdNm,
            @RequestParam(required = false) String aptName,
            @RequestParam(required = false) String dealYmd,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "true") Boolean autoImport
    ) {
        try {
            HouseSearchPageResponse result = houseService.searchHouseDeals(
                    lawdCd, sido, sigungu, umdNm, aptName, dealYmd, page, size, autoImport
            );
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage(), null));
        } catch (AutoImportException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.fail(e.getMessage(), null));
        }
    }

    @GetMapping("/house-deals")
    public ApiResponse<List<HouseDealResponse>> houseDeals(
            @RequestParam String lawdCd,
            @RequestParam String dealYmd
    ) {
        return ApiResponse.ok(houseService.findHouseDeals(lawdCd, dealYmd));
    }
}
