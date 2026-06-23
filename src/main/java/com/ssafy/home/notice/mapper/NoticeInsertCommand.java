package com.ssafy.home.notice.mapper;

public class NoticeInsertCommand {

    private Long noticeId;
    private final Long memberId;
    private final String title;
    private final String content;

    public NoticeInsertCommand(Long memberId, String title, String content) {
        this.memberId = memberId;
        this.title = title;
        this.content = content;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
