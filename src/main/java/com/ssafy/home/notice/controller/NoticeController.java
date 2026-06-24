package com.ssafy.home.notice.controller;

import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.member.auth.AuthCookieService;
import com.ssafy.home.member.auth.AuthenticatedMember;
import com.ssafy.home.member.auth.JwtClaims;
import com.ssafy.home.member.auth.JwtTokenService;
import com.ssafy.home.member.auth.JwtTokenType;
import com.ssafy.home.member.service.MemberException;
import com.ssafy.home.notice.dto.NoticeRequest;
import com.ssafy.home.notice.dto.NoticeResponse;
import com.ssafy.home.notice.service.NoticeException;
import com.ssafy.home.notice.service.NoticeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;
    private final AuthCookieService authCookieService;
    private final JwtTokenService jwtTokenService;

    public NoticeController(
            NoticeService noticeService,
            AuthCookieService authCookieService,
            JwtTokenService jwtTokenService
    ) {
        this.noticeService = noticeService;
        this.authCookieService = authCookieService;
        this.jwtTokenService = jwtTokenService;
    }

    @GetMapping
    public ApiResponse<List<NoticeResponse>> notices(
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(noticeService.findRecent(limit, currentMemberId(request)));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeResponse>> notice(
            @PathVariable Long noticeId,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(noticeService.findById(noticeId, currentMemberId(request))));
        } catch (NoticeException e) {
            return error(e);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoticeResponse>> create(
            @RequestBody NoticeRequest requestBody,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("created", noticeService.create(currentMemberId(request), requestBody)));
        } catch (NoticeException e) {
            return error(e);
        }
    }

    @PutMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeResponse>> update(
            @PathVariable Long noticeId,
            @RequestBody NoticeRequest requestBody,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(noticeService.update(currentMemberId(request), noticeId, requestBody)));
        } catch (NoticeException e) {
            return error(e);
        }
    }

    @DeleteMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> delete(
            @PathVariable Long noticeId,
            HttpServletRequest request
    ) {
        try {
            noticeService.delete(currentMemberId(request), noticeId);
            return ResponseEntity.ok(ApiResponse.ok("deleted", Map.of("deleted", true)));
        } catch (NoticeException e) {
            return error(e);
        }
    }

    private Long currentMemberId(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE);
        if (value instanceof Long memberId) {
            return memberId;
        }
        try {
            JwtClaims claims = jwtTokenService.verify(authCookieService.accessToken(request), JwtTokenType.ACCESS);
            return claims.memberId();
        } catch (MemberException exception) {
            return null;
        }
    }

    private static <T> ResponseEntity<ApiResponse<T>> error(NoticeException e) {
        HttpStatus status = switch (e.errorCode()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getMessage(), null));
    }
}
