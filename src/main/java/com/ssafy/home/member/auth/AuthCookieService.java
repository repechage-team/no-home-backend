package com.ssafy.home.member.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private final boolean secure;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;

    public AuthCookieService(
            @Value("${auth.jwt.cookie-secure}") boolean secure,
            @Value("${auth.jwt.access-token-seconds}") long accessTokenSeconds,
            @Value("${auth.jwt.refresh-token-seconds}") long refreshTokenSeconds
    ) {
        this.secure = secure;
        this.accessTokenSeconds = accessTokenSeconds;
        this.refreshTokenSeconds = refreshTokenSeconds;
    }

    public void writeTokenPair(HttpServletResponse response, JwtTokenPair tokenPair) {
        add(response, ACCESS_COOKIE, tokenPair.accessToken(), "/api", accessTokenSeconds);
        add(response, REFRESH_COOKIE, tokenPair.refreshToken(), "/api/auth", refreshTokenSeconds);
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", "/api", 0);
        add(response, REFRESH_COOKIE, "", "/api/auth", 0);
    }

    public String accessToken(HttpServletRequest request) {
        return cookieValue(request, ACCESS_COOKIE);
    }

    public String refreshToken(HttpServletRequest request) {
        return cookieValue(request, REFRESH_COOKIE);
    }

    private void add(HttpServletResponse response, String name, String value, String path, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
