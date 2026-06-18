package com.ssafy.home.member.auth;

import java.time.Instant;

public record JwtTokenPair(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt
) {
}
