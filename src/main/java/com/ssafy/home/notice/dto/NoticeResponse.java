package com.ssafy.home.notice.dto;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long noticeId,
        Long memberId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean editable
) {

    public static NoticeResponse from(Notice notice, boolean editable) {
        return new NoticeResponse(
                notice.noticeId(),
                notice.memberId(),
                notice.title(),
                notice.content(),
                notice.createdAt(),
                notice.updatedAt(),
                editable
        );
    }
}
