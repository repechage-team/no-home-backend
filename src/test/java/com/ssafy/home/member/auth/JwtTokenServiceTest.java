package com.ssafy.home.member.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.member.service.MemberException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-thirty-two-bytes";
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

    @Test
    void issuesTypedAccessAndRefreshTokens() {
        JwtTokenService service = service(NOW);

        JwtTokenPair pair = service.issue(7L);

        JwtClaims access = service.verify(pair.accessToken(), JwtTokenType.ACCESS);
        JwtClaims refresh = service.verify(pair.refreshToken(), JwtTokenType.REFRESH);
        assertThat(access.memberId()).isEqualTo(7L);
        assertThat(access.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(refresh.memberId()).isEqualTo(7L);
        assertThat(refresh.expiresAt()).isEqualTo(NOW.plusSeconds(604800));
    }

    @Test
    void rejectsWrongTypeTamperingAndExpiration() {
        JwtTokenService service = service(NOW);
        JwtTokenPair pair = service.issue(7L);

        assertThatThrownBy(() -> service.verify(pair.accessToken(), JwtTokenType.REFRESH))
                .isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.verify(pair.accessToken() + "x", JwtTokenType.ACCESS))
                .isInstanceOf(MemberException.class);

        JwtTokenService afterExpiration = service(NOW.plusSeconds(901));
        assertThatThrownBy(() -> afterExpiration.verify(pair.accessToken(), JwtTokenType.ACCESS))
                .isInstanceOf(MemberException.class);
    }

    private static JwtTokenService service(Instant now) {
        return new JwtTokenService(SECRET, 900, 604800, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }
}
