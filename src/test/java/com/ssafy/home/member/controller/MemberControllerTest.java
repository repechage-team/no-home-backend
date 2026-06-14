package com.ssafy.home.member.controller;

import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.dto.MemberSignupRequest;
import com.ssafy.home.member.dto.MemberUpdateRequest;
import com.ssafy.home.member.service.MemberErrorCode;
import com.ssafy.home.member.service.MemberException;
import com.ssafy.home.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MemberControllerTest {

    @Test
    void signupSuccessAndDuplicateFailureUseApiResponse() throws Exception {
        MemberService service = mock(MemberService.class);
        when(service.signup(any(MemberSignupRequest.class))).thenReturn(response(1L, "user@example.com", "User"));
        MockMvc mockMvc = standaloneSetup(new MemberController(service)).build();

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
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("email already exists."));
    }

    @Test
    void loginStoresOnlyMemberIdInSessionAndFailureReturnsUnauthorized() throws Exception {
        MemberService service = mock(MemberService.class);
        when(service.login("user@example.com", "password")).thenReturn(response(1L, "user@example.com", "User"));
        MockMvc mockMvc = standaloneSetup(new MemberController(service)).build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute(MemberController.LOGIN_MEMBER_ID, 1L))
                .andExpect(request().sessionAttribute("password", not("password")))
                .andReturn();
        assertThat(Collections.list(result.getRequest().getSession().getAttributeNames()))
                .containsExactly(MemberController.LOGIN_MEMBER_ID);

        when(service.login("user@example.com", "wrong"))
                .thenThrow(new MemberException(MemberErrorCode.INVALID_CREDENTIALS, "invalid email or password."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void logoutIsSafeWhenSessionExistsOrNot() throws Exception {
        MemberService service = mock(MemberService.class);
        MockMvc mockMvc = standaloneSetup(new MemberController(service)).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(MemberController.LOGIN_MEMBER_ID, 1L);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void unauthenticatedMeAccessIsBlocked() throws Exception {
        MemberService service = mock(MemberService.class);
        when(service.findCurrentMember(null))
                .thenThrow(new MemberException(MemberErrorCode.UNAUTHENTICATED, "login is required."));
        MockMvc mockMvc = standaloneSetup(new MemberController(service)).build();

        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void currentMemberLookupUpdateDeleteUseSessionMemberId() throws Exception {
        MemberService service = mock(MemberService.class);
        when(service.findCurrentMember(1L)).thenReturn(response(1L, "user@example.com", "User"));
        when(service.updateCurrentMember(eq(1L), any(MemberUpdateRequest.class)))
                .thenReturn(response(1L, "user@example.com", "Changed"));
        MockMvc mockMvc = standaloneSetup(new MemberController(service)).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(MemberController.LOGIN_MEMBER_ID, 1L);

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1));

        mockMvc.perform(put("/api/members/me")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {"memberId":2,"name":"Changed","phone":"010"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Changed"));

        mockMvc.perform(delete("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(service).findCurrentMember(1L);
        verify(service).updateCurrentMember(eq(1L), any(MemberUpdateRequest.class));
        verify(service).deleteCurrentMember(1L);
    }

    private static MemberResponse response(Long memberId, String email, String name) {
        return new MemberResponse(memberId, email, name, "010",
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 0));
    }
}
