package com.ssafy.home.member.auth;

import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.mapper.RefreshTokenMapper;
import com.ssafy.home.member.service.MemberException;
import com.ssafy.home.member.service.MemberService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberAuthServiceTest {

    @Test
    void loginStoresRefreshHashAndRefreshRotatesItOnce() {
        MemberService memberService = mock(MemberService.class);
        JwtTokenService tokenService = mock(JwtTokenService.class);
        RefreshTokenMapper mapper = mock(RefreshTokenMapper.class);
        MemberAuthService service = new MemberAuthService(memberService, tokenService, mapper);
        MemberResponse member = new MemberResponse(1L, "user@example.com", "User", null,
                LocalDateTime.now(), LocalDateTime.now());
        JwtTokenPair first = pair("access-1", "refresh-1");
        JwtTokenPair second = pair("access-2", "refresh-2");
        when(memberService.login("user@example.com", "password")).thenReturn(member);
        when(tokenService.issue(1L)).thenReturn(first, second);
        when(tokenService.verify("refresh-1", JwtTokenType.REFRESH))
                .thenReturn(new JwtClaims(1L, JwtTokenType.REFRESH, "id", first.refreshExpiresAt()));
        when(mapper.rotate(eq(1L), eq(TokenHash.sha256("refresh-1")),
                eq(TokenHash.sha256("refresh-2")), any())).thenReturn(1);

        MemberAuthService.LoginResult login = service.login("user@example.com", "password");
        JwtTokenPair refreshed = service.refresh("refresh-1");

        assertThat(login.tokens()).isEqualTo(first);
        assertThat(refreshed).isEqualTo(second);
        verify(mapper).upsert(eq(1L), eq(TokenHash.sha256("refresh-1")), any());
    }

    @Test
    void reusedRefreshTokenIsRejected() {
        MemberService memberService = mock(MemberService.class);
        JwtTokenService tokenService = mock(JwtTokenService.class);
        RefreshTokenMapper mapper = mock(RefreshTokenMapper.class);
        MemberAuthService service = new MemberAuthService(memberService, tokenService, mapper);
        JwtTokenPair next = pair("access-2", "refresh-2");
        when(tokenService.verify("used-refresh", JwtTokenType.REFRESH))
                .thenReturn(new JwtClaims(1L, JwtTokenType.REFRESH, "id", next.refreshExpiresAt()));
        when(tokenService.issue(1L)).thenReturn(next);
        when(mapper.rotate(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.refresh("used-refresh"))
                .isInstanceOf(MemberException.class)
                .hasMessage("refresh token is no longer valid.");
    }

    @Test
    void logoutAndMemberRevocationDeleteStoredRefreshTokens() {
        MemberService memberService = mock(MemberService.class);
        JwtTokenService tokenService = mock(JwtTokenService.class);
        RefreshTokenMapper mapper = mock(RefreshTokenMapper.class);
        MemberAuthService service = new MemberAuthService(memberService, tokenService, mapper);

        service.logout("refresh-token");
        service.revokeMember(1L);

        verify(mapper).deleteByTokenHash(TokenHash.sha256("refresh-token"));
        verify(mapper).deleteByMemberId(1L);
    }

    private static JwtTokenPair pair(String access, String refresh) {
        Instant now = Instant.parse("2026-06-15T00:00:00Z");
        return new JwtTokenPair(access, now.plusSeconds(900), refresh, now.plusSeconds(604800));
    }
}
