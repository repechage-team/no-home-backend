package com.ssafy.home.house.controller;

import com.ssafy.home.house.dto.HouseSearchPageResponse;
import com.ssafy.home.house.dto.HouseSearchResultResponse;
import com.ssafy.home.house.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HouseControllerTest {

    @Test
    void searchHousesReturnsBadRequestWhenNoSearchConditionExists() throws Exception {
        HouseService houseService = mock(HouseService.class);
        when(houseService.searchHouseDeals(null, null, null, null, null, null, null, null, true))
                .thenThrow(new IllegalArgumentException("At least one search condition is required."));
        MockMvc mockMvc = standaloneSetup(new HouseController(houseService)).build();

        mockMvc.perform(get("/api/houses/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("At least one search condition is required."));
    }

    @Test
    void searchHousesReturnsCommonJsonResponse() throws Exception {
        HouseService houseService = mock(HouseService.class);
        when(houseService.searchHouseDeals(
                eq("11590"), eq(null), eq(null), eq(null), eq("River"), eq("202605"), eq(1), eq(20), eq(true)
        )).thenReturn(new HouseSearchPageResponse(List.of(new HouseSearchResultResponse(
                1L, 2L, "River Apt", "Seoul", "Dongjak-gu", "Sangdo-dong", "335", 2018,
                "11590", "202605", LocalDate.of(2026, 5, 20), "205,000", 205000,
                null, 18, null, null
        )), 1, 20, 1));
        MockMvc mockMvc = standaloneSetup(new HouseController(houseService)).build();

        mockMvc.perform(get("/api/houses/search")
                        .param("lawdCd", "11590")
                        .param("aptName", "River")
                        .param("dealYmd", "202605")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.autoImportAttempted").value(false))
                .andExpect(jsonPath("$.data.items[0].aptNm").value("River Apt"))
                .andExpect(jsonPath("$.data.items[0].lat").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].lng").value(nullValue()));
    }
}
