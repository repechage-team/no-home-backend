package com.ssafy.home.notice.dto;

import java.time.LocalDateTime;

public record Notice(
        Long noticeId,
        Long memberId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
