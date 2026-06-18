package com.ssafy.home.member.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.common.response.ApiResponse;
import com.ssafy.home.member.service.MemberException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;
    private final AuthCookieService authCookieService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationInterceptor(
            JwtTokenService jwtTokenService,
            AuthCookieService authCookieService,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authCookieService = authCookieService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            JwtClaims claims = jwtTokenService.verify(authCookieService.accessToken(request), JwtTokenType.ACCESS);
            request.setAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE, claims.memberId());
            return true;
        } catch (MemberException exception) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(exception.getMessage(), null));
            return false;
        }
    }
}
