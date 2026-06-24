package com.ssafy.home.member.controller;

import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.member.auth.AuthCookieService;
import com.ssafy.home.member.auth.AuthenticatedMember;
import com.ssafy.home.member.auth.JwtTokenPair;
import com.ssafy.home.member.auth.MemberAuthService;
import com.ssafy.home.member.dto.MemberLoginRequest;
import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.dto.MemberSignupRequest;
import com.ssafy.home.member.dto.MemberUpdateRequest;
import com.ssafy.home.member.dto.PasswordResetRequest;
import com.ssafy.home.member.service.MemberErrorCode;
import com.ssafy.home.member.service.MemberException;
import com.ssafy.home.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MemberController {

    private final MemberService memberService;
    private final MemberAuthService memberAuthService;
    private final AuthCookieService authCookieService;

    public MemberController(
            MemberService memberService,
            MemberAuthService memberAuthService,
            AuthCookieService authCookieService
    ) {
        this.memberService = memberService;
        this.memberAuthService = memberAuthService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@RequestBody MemberSignupRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", memberService.signup(request)));
        } catch (MemberException e) {
            return error(e);
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(
            @RequestBody MemberLoginRequest request,
            HttpServletResponse response
    ) {
        try {
            MemberAuthService.LoginResult result = memberAuthService.login(
                    request == null ? null : request.email(),
                    request == null ? null : request.password()
            );
            authCookieService.writeTokenPair(response, result.tokens());
            return ResponseEntity.ok(ApiResponse.ok(result.member()));
        } catch (MemberException e) {
            return error(e);
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            JwtTokenPair tokens = memberAuthService.refresh(authCookieService.refreshToken(request));
            authCookieService.writeTokenPair(response, tokens);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("refreshed", true)));
        } catch (MemberException e) {
            authCookieService.clear(response);
            return error(e);
        }
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Map<String, Boolean>> logout(HttpServletRequest request, HttpServletResponse response) {
        memberAuthService.logout(authCookieService.refreshToken(request));
        authCookieService.clear(response);
        return ApiResponse.ok("logged out", Map.of("loggedOut", true));
    }

    @PostMapping("/auth/password-reset")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> resetPassword(@RequestBody PasswordResetRequest request) {
        try {
            MemberResponse member = memberService.resetPassword(request);
            memberAuthService.revokeMember(member.memberId());
            return ResponseEntity.ok(ApiResponse.ok("password reset", Map.of("reset", true)));
        } catch (MemberException e) {
            return error(e);
        }
    }

    @GetMapping("/members/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(memberService.findCurrentMember(currentMemberId(request))));
        } catch (MemberException e) {
            return error(e);
        }
    }

    @GetMapping("/members/search")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> searchMembers(
            @RequestParam String keyword,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(memberService.searchMembers(currentMemberId(request), keyword)));
        } catch (MemberException e) {
            return error(e);
        }
    }

    @PutMapping("/members/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMe(
            @RequestBody MemberUpdateRequest requestBody,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(memberService.updateCurrentMember(currentMemberId(request), requestBody)));
        } catch (MemberException e) {
            return error(e);
        }
    }

    @DeleteMapping("/members/me")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteMe(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            memberAuthService.revokeMember(currentMemberId(request));
            memberService.deleteCurrentMember(currentMemberId(request));
            authCookieService.clear(response);
            return ResponseEntity.ok(ApiResponse.ok("deleted", Map.of("deleted", true)));
        } catch (MemberException e) {
            return error(e);
        }
    }

    private static Long currentMemberId(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE);
        return value instanceof Long memberId ? memberId : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> error(MemberException e) {
        HttpStatus status = switch (e.errorCode()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case DUPLICATE_EMAIL -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS, UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getMessage(), null));
    }
}
