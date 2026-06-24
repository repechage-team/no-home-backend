package com.ssafy.home.interest.controller;

import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.interest.dto.InterestRegionRequest;
import com.ssafy.home.interest.dto.InterestRegionResponse;
import com.ssafy.home.interest.service.InterestRegionException;
import com.ssafy.home.interest.service.InterestRegionService;
import com.ssafy.home.member.auth.AuthCookieService;
import com.ssafy.home.member.auth.AuthenticatedMember;
import com.ssafy.home.member.auth.JwtClaims;
import com.ssafy.home.member.auth.JwtTokenService;
import com.ssafy.home.member.auth.JwtTokenType;
import com.ssafy.home.member.service.MemberException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interest-regions")
public class InterestRegionController {

    private final InterestRegionService service;
    private final AuthCookieService authCookieService;
    private final JwtTokenService jwtTokenService;

    public InterestRegionController(
            InterestRegionService service,
            AuthCookieService authCookieService,
            JwtTokenService jwtTokenService
    ) {
        this.service = service;
        this.authCookieService = authCookieService;
        this.jwtTokenService = jwtTokenService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InterestRegionResponse>>> myRegions(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.findMyRegions(currentMemberId(request))));
        } catch (InterestRegionException e) {
            return error(e);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InterestRegionResponse>> add(
            @RequestBody InterestRegionRequest requestBody,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("created", service.addMyRegion(currentMemberId(request), requestBody)));
        } catch (InterestRegionException e) {
            return error(e);
        }
    }

    @DeleteMapping("/{interestRegionId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> delete(
            @PathVariable Long interestRegionId,
            HttpServletRequest request
    ) {
        try {
            service.deleteMyRegion(currentMemberId(request), interestRegionId);
            return ResponseEntity.ok(ApiResponse.ok("deleted", Map.of("deleted", true)));
        } catch (InterestRegionException e) {
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

    private static <T> ResponseEntity<ApiResponse<T>> error(InterestRegionException e) {
        HttpStatus status = switch (e.errorCode()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getMessage(), null));
    }
}
