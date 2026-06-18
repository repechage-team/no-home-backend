package com.ssafy.home.member.auth;

import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.mapper.RefreshTokenMapper;
import com.ssafy.home.member.service.MemberErrorCode;
import com.ssafy.home.member.service.MemberException;
import com.ssafy.home.member.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class MemberAuthService {

    private final MemberService memberService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenMapper refreshTokenMapper;

    public MemberAuthService(
            MemberService memberService,
            JwtTokenService jwtTokenService,
            RefreshTokenMapper refreshTokenMapper
    ) {
        this.memberService = memberService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    @Transactional
    public LoginResult login(String email, String password) {
        MemberResponse member = memberService.login(email, password);
        JwtTokenPair tokens = jwtTokenService.issue(member.memberId());
        refreshTokenMapper.upsert(member.memberId(), TokenHash.sha256(tokens.refreshToken()),
                LocalDateTime.ofInstant(tokens.refreshExpiresAt(), ZoneOffset.UTC));
        return new LoginResult(member, tokens);
    }

    @Transactional
    public JwtTokenPair refresh(String refreshToken) {
        JwtClaims claims = jwtTokenService.verify(refreshToken, JwtTokenType.REFRESH);
        JwtTokenPair newTokens = jwtTokenService.issue(claims.memberId());
        int rotated = refreshTokenMapper.rotate(
                claims.memberId(),
                TokenHash.sha256(refreshToken),
                TokenHash.sha256(newTokens.refreshToken()),
                LocalDateTime.ofInstant(newTokens.refreshExpiresAt(), ZoneOffset.UTC)
        );
        if (rotated != 1) {
            throw new MemberException(MemberErrorCode.UNAUTHENTICATED, "refresh token is no longer valid.");
        }
        return newTokens;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenMapper.deleteByTokenHash(TokenHash.sha256(refreshToken));
        }
    }

    @Transactional
    public void revokeMember(Long memberId) {
        refreshTokenMapper.deleteByMemberId(memberId);
    }

    public record LoginResult(MemberResponse member, JwtTokenPair tokens) {
    }
}
