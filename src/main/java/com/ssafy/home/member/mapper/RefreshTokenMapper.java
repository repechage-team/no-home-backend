package com.ssafy.home.member.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface RefreshTokenMapper {

    int upsert(
            @Param("memberId") Long memberId,
            @Param("tokenHash") String tokenHash,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    int rotate(
            @Param("memberId") Long memberId,
            @Param("currentTokenHash") String currentTokenHash,
            @Param("newTokenHash") String newTokenHash,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    int deleteByMemberId(@Param("memberId") Long memberId);
}
