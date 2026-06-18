package com.ssafy.home.member.auth;

import java.time.Instant;

public record JwtClaims(Long memberId, JwtTokenType type, String tokenId, Instant expiresAt) {
}
