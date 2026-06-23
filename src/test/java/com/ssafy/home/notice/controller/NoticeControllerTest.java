package com.ssafy.home.notice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.member.auth.AuthCookieService;
import com.ssafy.home.member.auth.JwtTokenService;
import com.ssafy.home.notice.dto.NoticeRequest;
import com.ssafy.home.notice.dto.NoticeResponse;
import com.ssafy.home.notice.service.NoticeErrorCode;
import com.ssafy.home.notice.service.NoticeException;
import com.ssafy.home.notice.service.NoticeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NoticeControllerTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void recentNoticesCanBeReadWithoutLogin() throws Exception {
        NoticeService service = mock(NoticeService.class);
        when(service.findRecent(5, null)).thenReturn(List.of(response(1L, 1L, "Notice", false)));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(get("/api/notices").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Notice"))
                .andExpect(jsonPath("$.data[0].editable").value(false));
    }

    @Test
    void createNoticeUsesAccessCookieMember() throws Exception {
        NoticeService service = mock(NoticeService.class);
        when(service.create(eq(1L), any(NoticeRequest.class))).thenReturn(response(3L, 1L, "Created", true));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(post("/api/notices")
                        .cookie(accessCookie(1L))
                        .contentType("application/json")
                        .content("""
                                {"title":"Created","content":"Body"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data.noticeId").value(3))
                .andExpect(jsonPath("$.data.editable").value(true));
    }

    @Test
    void createNoticeWithoutLoginReturnsUnauthorized() throws Exception {
        NoticeService service = mock(NoticeService.class);
        when(service.create(eq(null), any(NoticeRequest.class)))
                .thenThrow(new NoticeException(NoticeErrorCode.UNAUTHENTICATED, "login required."));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(post("/api/notices")
                        .contentType("application/json")
                        .content("""
                                {"title":"Created","content":"Body"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createNoticeWithoutAdminPermissionReturnsForbidden() throws Exception {
        NoticeService service = mock(NoticeService.class);
        when(service.create(eq(2L), any(NoticeRequest.class)))
                .thenThrow(new NoticeException(NoticeErrorCode.FORBIDDEN, "admin permission is required."));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(post("/api/notices")
                        .cookie(accessCookie(2L))
                        .contentType("application/json")
                        .content("""
                                {"title":"Created","content":"Body"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateAndDeleteNoticeReturnCommonResponses() throws Exception {
        NoticeService service = mock(NoticeService.class);
        when(service.update(eq(1L), eq(3L), any(NoticeRequest.class))).thenReturn(response(3L, 1L, "After", true));
        MockMvc mockMvc = standaloneSetup(controller(service)).build();

        mockMvc.perform(put("/api/notices/3")
                        .cookie(accessCookie(1L))
                        .contentType("application/json")
                        .content("""
                                {"title":"After","content":"Body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("After"));

        mockMvc.perform(delete("/api/notices/3")
                        .cookie(accessCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    private static NoticeController controller(NoticeService service) {
        return new NoticeController(service, cookieService(), tokenService());
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

    private static NoticeResponse response(Long noticeId, Long memberId, String title, boolean editable) {
        return new NoticeResponse(noticeId, memberId, title, "Body",
                LocalDateTime.of(2026, 6, 23, 10, 0),
                LocalDateTime.of(2026, 6, 23, 10, 0),
                editable);
    }
}
