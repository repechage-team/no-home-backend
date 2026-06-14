package com.ssafy.home.member.dto;

import java.time.LocalDateTime;

public record Member(
        Long memberId,
        String email,
        String passwordHash,
        String name,
        String phone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
