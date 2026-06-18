package com.ssafy.home.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.house.service.HouseService;
import com.ssafy.home.house.controller.HouseController;
import com.ssafy.home.member.auth.AuthCookieService;
import com.ssafy.home.member.auth.AuthenticatedMember;
import com.ssafy.home.member.auth.JwtAuthenticationInterceptor;
import com.ssafy.home.member.auth.JwtTokenPair;
import com.ssafy.home.member.auth.JwtTokenService;
import com.ssafy.home.member.auth.MemberAuthService;
import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.dto.MemberSignupRequest;
import com.ssafy.home.member.dto.MemberUpdateRequest;
import com.ssafy.home.member.service.MemberErrorCode;
import com.ssafy.home.member.service.MemberException;
import com.ssafy.home.member.service.MemberService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MemberControllerTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void signupSuccessAndDuplicateFailureUseApiResponse() throws Exception {
        MemberService service = mock(MemberService.class);
        when(service.signup(any(MemberSignupRequest.class))).thenReturn(response(1L, "user@example.com", "User"));
        MockMvc mockMvc = standaloneSetup(controller(service, mock(MemberAuthService.class))).build();

        mockMvc.perform(post("/api/members")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"password","name":"User","phone":"010"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        when(service.signup(any(MemberSignupRequest.class)))
                .thenThrow(new MemberException(MemberErrorCode.DUPLICATE_EMAIL, "email already exists."));

        mockMvc.perform(post("/api/members")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"password","name":"User"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginIssuesHttpOnlyAccessAndRefreshCookies() throws Exception {
        MemberService service = mock(MemberService.class);
        MemberAuthService authService = mock(MemberAuthService.class);
        JwtTokenPair tokens = tokens();
        when(authService.login("user@example.com", "password"))
                .thenReturn(new MemberAuthService.LoginResult(response(1L, "user@example.com", "User"), tokens));
        MockMvc mockMvc = standaloneSetup(controller(service, authService)).build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(value -> value.contains("access_token=access-token") && value.contains("HttpOnly")))
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(value -> value.contains("refresh_token=refresh-token") && value.contains("HttpOnly")))
                .andExpect(jsonPath("$.data.memberId").value(1));
    }

    @Test
    void refreshRotatesCookiesAndInvalidRefreshReturnsUnauthorized() throws Exception {
        MemberService service = mock(MemberService.class);
        MemberAuthService authService = mock(MemberAuthService.class);
        when(authService.refresh("refresh-token")).thenReturn(tokens());
        MockMvc mockMvc = standaloneSetup(controller(service, authService)).build();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE, "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(true))
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(value -> value.contains("access_token=access-token")));

        when(authService.refresh("invalid"))
                .thenThrow(new MemberException(MemberErrorCode.UNAUTHENTICATED, "refresh token is no longer valid."));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_COOKIE, "invalid")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void protectedMemberRoutesUseAuthenticatedRequestMemberOnly() throws Exception {
        MemberService service = mock(MemberService.class);
        MemberAuthService authService = mock(MemberAuthService.class);
        when(service.findCurrentMember(1L)).thenReturn(response(1L, "user@example.com", "User"));
        when(service.updateCurrentMember(eq(1L), any(MemberUpdateRequest.class)))
                .thenReturn(response(1L, "user@example.com", "Changed"));
        MockMvc mockMvc = standaloneSetup(controller(service, authService)).build();

        mockMvc.perform(get("/api/members/me")
                        .requestAttr(AuthenticatedMember.REQUEST_ATTRIBUTE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1));

        mockMvc.perform(put("/api/members/me")
                        .requestAttr(AuthenticatedMember.REQUEST_ATTRIBUTE, 1L)
                        .contentType("application/json")
                        .content("""
                                {"memberId":2,"name":"Changed","phone":"010"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Changed"));

        mockMvc.perform(delete("/api/members/me")
                        .requestAttr(AuthenticatedMember.REQUEST_ATTRIBUTE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(authService).revokeMember(1L);
        verify(service).deleteCurrentMember(1L);
    }

    @Test
    void interceptorProtectsOnlyMemberMeRoutes() throws Exception {
        MemberService memberService = mock(MemberService.class);
        HouseService houseService = mock(HouseService.class);
        MemberAuthService authService = mock(MemberAuthService.class);
        AuthCookieService cookieService = cookieService();
        JwtTokenService tokenService = new JwtTokenService(SECRET, 900, 604800, new ObjectMapper());
        JwtAuthenticationInterceptor interceptor = new JwtAuthenticationInterceptor(
                tokenService, cookieService, new ObjectMapper()
        );
        when(memberService.findCurrentMember(1L)).thenReturn(response(1L, "user@example.com", "User"));
        MockMvc mockMvc = standaloneSetup(controller(memberService, authService), new HouseController(houseService))
                .addMappedInterceptors(new String[]{"/api/members/me"}, interceptor)
                .build();

        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        String accessToken = tokenService.issue(1L).accessToken();
        mockMvc.perform(get("/api/members/me")
                        .cookie(new Cookie(AuthCookieService.ACCESS_COOKIE, accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1));

        mockMvc.perform(get("/api/houses").param("aptName", "test"))
                .andExpect(status().isOk());
    }

    private static MemberController controller(MemberService service, MemberAuthService authService) {
        return new MemberController(service, authService, cookieService());
    }

    private static AuthCookieService cookieService() {
        return new AuthCookieService(false, 900, 604800);
    }

    private static JwtTokenPair tokens() {
        Instant now = Instant.parse("2026-06-15T00:00:00Z");
        return new JwtTokenPair("access-token", now.plusSeconds(900), "refresh-token", now.plusSeconds(604800));
    }

    private static MemberResponse response(Long memberId, String email, String name) {
        return new MemberResponse(memberId, email, name, "010",
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 0));
    }
}
