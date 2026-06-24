package com.ssafy.home.interest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.interest.dto.InterestRegionRequest;
import com.ssafy.home.interest.dto.InterestRegionResponse;
import com.ssafy.home.interest.service.InterestRegionErrorCode;
import com.ssafy.home.interest.service.InterestRegionException;
import com.ssafy.home.interest.service.InterestRegionService;
import com.ssafy.home.member.auth.AuthCookieService;
import com.ssafy.home.member.auth.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InterestRegionControllerTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void listUsesAccessCookieMember() throws Exception {
        InterestRegionService service = mock(InterestRegionService.class);
        when(service.findMyRegions(1L)).thenReturn(List.of(response(1L, "흑석동")));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(get("/api/interest-regions")
                        .cookie(accessCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].umdNm").value("흑석동"));
    }

    @Test
    void addRequiresLogin() throws Exception {
        InterestRegionService service = mock(InterestRegionService.class);
        when(service.addMyRegion(eq(null), any(InterestRegionRequest.class)))
                .thenThrow(new InterestRegionException(InterestRegionErrorCode.UNAUTHENTICATED, "login required."));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(post("/api/interest-regions")
                        .contentType("application/json")
                        .content("""
                                {"lawdCd":"11590","sido":"서울특별시","sigungu":"동작구","umdNm":"흑석동"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addAndDeleteReturnCommonResponses() throws Exception {
        InterestRegionService service = mock(InterestRegionService.class);
        when(service.addMyRegion(eq(1L), any(InterestRegionRequest.class))).thenReturn(response(3L, "흑석동"));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(post("/api/interest-regions")
                        .cookie(accessCookie(1L))
                        .contentType("application/json")
                        .content("""
                                {"lawdCd":"11590","sido":"서울특별시","sigungu":"동작구","umdNm":"흑석동"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data.umdNm").value("흑석동"));

        mockMvc.perform(delete("/api/interest-regions/3")
                        .cookie(accessCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    private static InterestRegionController controller(InterestRegionService service) {
        return new InterestRegionController(service, cookieService(), tokenService());
    }

    private static AuthCookieService cookieService() {
        return new AuthCookieService(false, 900, 604800);
    }

    private static JwtTokenService tokenService() {
        return new JwtTokenService(SECRET, 900, 604800, new ObjectMapper());
    }

    private static Cookie accessCookie(Long memberId) {
        return new Cookie(AuthCookieService.ACCESS_COOKIE, tokenService().issue(memberId).accessToken());
    }

    private static InterestRegionResponse response(Long interestRegionId, String umdNm) {
        return new InterestRegionResponse(
                interestRegionId,
                7L,
                "11590",
                "1159010500",
                "서울특별시",
                "동작구",
                umdNm,
                LocalDateTime.of(2026, 6, 23, 10, 0)
        );
    }
}
