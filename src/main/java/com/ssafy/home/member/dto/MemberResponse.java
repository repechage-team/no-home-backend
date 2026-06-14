package com.ssafy.home.member.dto;

import java.time.LocalDateTime;

public record MemberResponse(
        Long memberId,
        String email,
        String name,
        String phone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.memberId(),
                member.email(),
                member.name(),
                member.phone(),
                member.createdAt(),
                member.updatedAt()
        );
    }
}
